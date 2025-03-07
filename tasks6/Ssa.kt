
import com.squareup.moshi.*
import okio.*
import java.util.TreeSet
import java.util.TreeMap
import java.util.LinkedList
import java.io.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.collections.indexOfFirst

@kotlin.ExperimentalStdlibApi
fun program(
    filename: String?,
): Pair<JsonAdapter<BrilProgram>, BrilProgram?> {
 val moshi = Moshi.Builder()
        .add(BrilPrimitiveValueTypeAdapter())
        .add(BrilTypeAdapter())
        .add(BrilInstrAdapter())
        .add(BrilOpAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()
    val adapter: JsonAdapter<BrilProgram> = moshi.adapter<BrilProgram>()
    val brilInstrAdapter = moshi.adapter<BrilInstr>()
    val source = if (filename != null) {
        val file = File(filename)
        val source = file.source()
        source.buffer()
    } else {
        System.`in`.source().buffer()
    }

    return adapter to adapter.fromJson(source)
} 

fun predecessors(
    v: Int,
    cfg: Cfg,
): TreeSet<Int> {
    var result = TreeSet<Int>()
    cfg.forEach { v1, next -> 
        if (v != v1 && next.contains(v)) {
            result.add(v1)
        }
    }
    return result
}

// Map of variable to block ids. 
typealias Defs = TreeMap<String, TreeSet<Int>>
fun defs(
    blocks: Blocks,
): Defs {
    val res = Defs() 
    blocks.forEachIndexed { i, b -> 
        b.forEach { instr -> 
            val dest = instr.dest()
            if (dest != null) {
                if (res.contains(dest).not()) {
                    res.put(dest, TreeSet<Int>())
                }
                res.get(dest)!!.add(i)
            }
        }
    }
    return res
}


typealias Phis = TreeMap<Int, TreeMap<String, TreeMap<Int, String>>>
fun toSsa(
    blocks: Blocks,
    cfg: Cfg,
    idoms: Idoms,
    domFrontier: DomFrontier,
    blockToLabel: Map<Int, String>,
): List<BrilInstr> {
    val defs = defs(blocks)
    val phis: Phis = Phis()
    blocks.forEachIndexed { i, _ -> 
        phis[i] = TreeMap()
    }
    
    val processing = TreeSet<String>()
    processing.addAll(defs.keys)
    while(processing.isNotEmpty()) {
        val v = processing.pollFirst()
        val bs = TreeSet(defs[v]!!)
        bs.forEach { b -> 
            domFrontier[b]!!.forEach { b1 -> 
                val c = phis[b1]!!
                if (c.contains(v).not()) {
                    c.put(v, TreeMap<Int, String>())
                }
                c[v]!!.put(b, v)
                if (defs[v]!!.contains(b1).not()) {
                    defs[v]!!.add(b1)
                    processing.add(v)
                }
            }
        }
    }
    
    val blockMap = TreeMap<Int, LinkedList<BrilInstr>>()
    blocks.forEachIndexed { i, b -> 
        val label = b.first()
        val list = LinkedList<BrilInstr>()
        list.add(label)
        phis[i]!!.forEach { (vname, args) ->
            val phiArgs = LinkedList<String>()
            val labels = LinkedList<String>()
            args.forEach { bid, name ->
                phiArgs.add(name)
                labels.add(blockToLabel[bid]!!)
            }
            val phiOp = BrilPhiOp(
                op = "phi",
                args = phiArgs,
                labels = labels,
                dest = vname,
                type = BrilPrimitiveType("int", null), // TODO - remove hardcoding
            )
            list.add(phiOp)
        }
        for (i in 1 ..< b.size) {
            list.add(b[i]!!)
        }
        blockMap.put(i, list)
    }
    // using a treemap as a stack of variables. 
    val stack = TreeMap<String, LinkedList<String>>()
    val varCount = TreeMap<String, Int>()
    defs.keys.forEach { v -> 
        stack.put(v, LinkedList<String>())
        varCount.put(v, 0)
    }

    fun rename(
        id: Int,
    ) {
        val block = blockMap[id]!!
        var pushed = LinkedList<String>()
        for (i in 0 ..< blockMap[id]!!.size) {
           val instr = blockMap[id]!![i]!!
           if (instr is BrilLabel) {
               continue
           } else {
               val dest = instr.dest()
               val newDest = if (dest != null) {
                   "$dest.${varCount[dest]!!}"
               } else {
                   dest
               }
               val newInstr = instr.replaceArgs { arg -> 
                   if (stack.contains(arg) && stack[arg]!!.isNotEmpty()) {
                       stack[arg]!!.first()
                   } else {
                       arg
                   }
               }.let {
                   if (newDest != null) {
                       it.renameDest(newDest)
                   } else {
                       it
                   }
               }
               if (dest != null) {
                    stack[dest]!!.push(newDest)
                    pushed.push(dest)
                    varCount[dest] = varCount[dest]!! + 1
               }
               blockMap[id]!![i] = newInstr
           }
        }
        cfg[id]!!.forEach { succ ->
            for (i in 0 ..< blockMap[succ]!!.size) {
                val instr = blockMap[succ]!![i]
                if (instr is BrilPhiOp) {
                    val argIndex = instr.labels.indexOfFirst { it == blockToLabel[id] }
                    if (argIndex != -1) {
                        val instr0 = instr.copy(
                            args = instr.args.mapIndexed { j, arg -> 
                                if (j == argIndex) {
                                   stack[arg]!!.first() 
                                } else {
                                    arg
                                }
                            }
                        )
                        blockMap[succ]!![i] = instr0
                    }
                }
            }
        }
        idoms[id]!!.forEach { next ->
            rename(next)
        }
        
        while (pushed.isNotEmpty()) {
            stack[pushed.pop()]!!.pop()
        }
    }
    rename(0)
    val res = LinkedList<BrilInstr>()
    blockMap.forEach { (i, b) ->
        res.addAll(b)
    }
    return res
}


fun fromSsa(
    function: BrilFunction,
): BrilFunction {
    val (blocks, cfg) = cfg(function)
    val lblToBlock = labelToBlock(blocks)
    val blockIdMapping = blocksToIds(blocks)
    blocks.forEachIndexed { i, b ->
        b.forEachIndexed { j, instr ->
            if (instr is BrilPhiOp) {
                val listOfArgs = instr.args
                val listOfLabels = instr.labels
                for ((n, v) in listOfLabels.withIndex()) {
                    val bid = lblToBlock[v!!]
                    val newBrilID = BrilIdOp("id", instr.dest, instr.type, listOfArgs[n])
                    blockIdMapping[bid!!]!!.add(blockIdMapping[bid!!]!!.size - 1, newBrilID)
                }
                blockIdMapping[i]!!.removeAt(j)
            }
        }    
    }

    val newInstrs = blockIdMapping.values.flatten()
    return function.copy(
        instrs = newInstrs,
    )
}

fun debugToSsa(p: BrilProgram) {
    println("STARTING")
    val function = p.functions.first()
    val (blocks, cfg) = cfg(function)
    val new_fx = fromSsa(function)
    println("NEW")
    println(new_fx)
  
}

@kotlin.ExperimentalStdlibApi
fun main(args: Array<String>) {
    val filename = if (args.size > 0 && args[0].startsWith("-f")) {
        args[1]
    } else {
        null
    }
    val (adapter, p) = program(filename)

    if (p == null) {
        println("Invalid input")
        return
    }   
    val p1 = p.copy(
        functions = p.functions.map { brilFun -> 
            val (blocks, cfg) = cfg(brilFun)
            val doms = doms(blocks, cfg)
            val domsFlipped = flip_doms(doms)
            val idoms = idomsSearch(domsFlipped)
            val domfrontier = domfrontier(domsFlipped, cfg)
            val bidTolabel = bidToLabel(blocks)
            val ssa = toSsa(blocks, cfg, idoms, domfrontier, bidTolabel)
            brilFun.copy(
                instrs = ssa
            )
        }
    )
    println(adapter.toJson(p1))
}
