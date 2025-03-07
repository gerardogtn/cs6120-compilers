
import com.squareup.moshi.*
import okio.*
import java.util.TreeSet
import java.util.TreeMap
import java.io.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

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
//   println("Task 6 - Static Single Assignment")
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

  val new_prog = p.copy(functions=p.functions.map(::fromSsa))
  val out = adapter.toJson(new_prog)
  println(out)

//   println("Tasks 6 - Done")
}
