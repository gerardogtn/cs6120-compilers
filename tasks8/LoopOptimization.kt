import java.util.TreeSet
import java.util.TreeMap
import java.util.LinkedList
import kotlin.collections.indexOfFirst

typealias BackEdges = TreeMap<Int, TreeSet<Int>>
fun backedges(
    cfg: Cfg,
    strictDominates: Doms,
): BackEdges {
    val found = BackEdges()
    for (i in 0 ..< cfg.size) {
        val vi = i;
        val n = cfg[vi]!!;
        for (vj in n) {
            if (strictDominates[vj]!!.contains(vi)) {
                if (found.contains(vi).not()) {
                    found.put(vi, TreeSet())
                }
                found[vi]!!.add(vj)
            }
        }
    }
    return found
}

// Return the nodes that can reach target in cfg, while ignoring skip.
fun reach(
    cfg: Cfg,
    target: Int,
    skip: Int,
): TreeSet<Int> {
    System.err.println("Checking nodes that reach $target, ignoring $skip")
    val reach = TreeSet<Int>()
    val visited = TreeSet<Int>()
    visited.add(skip) // ignore skip
    val queue = TreeSet<Int>()
    for (i in 0 ..< cfg.size) {
        queue.add(i)
    }

    for (vi in 0 ..< cfg.size) {
        if (vi != skip && vi !in reach && vi !in visited) {
            val next = cfg[vi]!!
            var reaches = false
            for (vj in next) {
                if (vj != skip && vj in reach) {
                    reaches = true
                    break
                }
            }
            if (reaches) {
                visited.removeAll(next)
                visited.add(skip)
                reach.add(vi)
                queue.addAll(next)
            }
        }
    }

    return reach
}

data class NaturalLoop(
    val start: Int,
    val end: Int,
    val middle: TreeSet<Int>,
)

typealias NaturalLoops = LinkedList<NaturalLoop>
fun naturalLoops(
    cfg: Cfg,
    backedges: BackEdges,
): NaturalLoops {
    val res = NaturalLoops()
    backedges.forEach { (vi, n) -> 
        n.forEach { vj -> 
            val reach = reach(cfg, target=vi, skip=vj)
            res.add(NaturalLoop(start = vj, end = vi, middle = reach))
        }
    }
    return res
}

typealias NextLabel= () -> String
fun nextLabelGenerator(
    labels: TreeSet<String>,
): NextLabel {
    val acc = TreeSet(labels)
    var counter = 0
    val prefix = "__label"
    return { 
        var candidate = "$prefix$counter"
        while (candidate in acc) {
            counter = counter + 1
            candidate = "$prefix$counter"
        }
        counter = counter + 1
        candidate
    }
}

typealias NextUniqueString = (String) -> String
fun nextUniqueStringGenerator(
    taken: TreeSet<String>
): NextUniqueString {
    val acc = TreeMap<String, Int>()
    taken.forEach { s -> 
        acc.put(s, 0)
    }
    return { l -> 
        if (acc.contains(l).not()) {
            acc.put(l, 0)
        }
        var c = acc[l]!!
        acc.put(l, c + 1)
        "$l.$c"
    }
}

fun insertPreHeaders(
    p: BrilProgram,
): BrilProgram {
    return p.copy(
        functions = p.functions.map { brilFun -> 
            val (blocks, cfg) = cfg(brilFun)
            val isdom = doms(blocks, cfg)
            val strictDominates = flip_doms(isdom, strict = true)
            val backedges = backedges(cfg, strictDominates)
            val naturalLoops = naturalLoops(cfg, backedges)
            val nextLabel = nextLabelGenerator(brilFun.labels())
            val labelToBlock = labelToBlock(blocks)
            insertPreHeaders(
                f = brilFun,
                naturalLoops = naturalLoops,
                nextLabel = nextLabel,
                blocks = blocks,
                labelToBlock = labelToBlock,
            )
        }
    )
}

fun insertPreHeaders(
    f: BrilFunction,
    naturalLoops: NaturalLoops,
    nextLabel: NextLabel,
    blocks: Blocks,
    labelToBlock: LabelToBlock,
): BrilFunction {
    val preheaderLabels = TreeMap<Int, String>()
    naturalLoops.forEach { (s, _, _) -> 
        val l = nextLabel()
        preheaderLabels.put(s, l)
    }

    // the set of nodes that are in a loop that starts with s.
    val looped = TreeMap<Int, TreeSet<Int>>()
    for (i in 0 ..< blocks.size) {
        looped[i] = TreeSet()
    }
    naturalLoops.forEach { (s, e, m) ->
        looped[s]!!.add(e)
        looped[s]!!.addAll(m)
    }

    // Rebuild the program with preheaders.
    return f.copy(
        instrs = blocks.mapIndexed { bid, b -> 
            val prefix = LinkedList<BrilInstr>()
            if (preheaderLabels.contains(bid)) {
                prefix.add(
                    BrilLabel(
                        label = preheaderLabels[bid]!!,
                        pos = null,
                    )
                )
                prefix.add(
                    BrilJmpOp(
                        op = "jmp",
                        label = (b.first() as BrilLabel).label,
                    )
                )
            }
            val items = b.map { instr -> 
                if (instr is BrilJmpOp) {
                    val lid = labelToBlock[instr.label]!!
                    if (preheaderLabels.contains(lid) && looped[lid]!!.contains(bid).not()) {
                        instr.copy(
                            label = preheaderLabels[lid]!!
                        )
                    } else {
                        instr
                    }
                } else if (instr is BrilBrOp) {
                    val lidL = labelToBlock[instr.labelL]!!
                    val labelL = if (preheaderLabels.contains(lidL) && looped[lidL]?.contains(bid) == false) {
                        preheaderLabels[lidL]!!
                    } else {
                        instr.labelL
                    }
                    val lidR = labelToBlock[instr.labelR]!!
                    val labelR = if (preheaderLabels.contains(lidR) && looped[lidR]?.contains(bid) == false) {
                        preheaderLabels[lidR]!!
                    } else {
                        instr.labelR
                    }
                    instr.copy(
                        labelL = labelL,
                        labelR = labelR,
                    )
                } else {
                    instr
                }
            }
            prefix.plus(items)
        }.flatten()
    )
}

// p is a bril program in ssa form.
fun loopOptimize(
    p: BrilProgram,
): BrilProgram {
    // p1 is the ssa-program with preheaders.
    // If you have identified that block b starts a natural loop,
    // then you can be sure that block (b - 1) is the preheader of the loop.
    val p1 = p.copy(
        functions = p.functions.map { brilFun -> 
            val (blocks, cfg) = cfg(brilFun)
            System.err.println("cfg")
            cfg.forEach { System.err.println(it) }
            val isdom = doms(blocks, cfg)
            val strictDominates = flip_doms(isdom, strict = true)
            System.err.println("strict dominates")
            strictDominates.forEach { System.err.println(it) }
            val backedges = backedges(cfg, strictDominates)
            System.err.println("backedges")
            backedges.forEach { System.err.println(it) }
            System.err.println("naturalLoops")
            val naturalLoops = naturalLoops(cfg, backedges)
            naturalLoops.forEach { System.err.println(it) }
            val nextLabel = nextLabelGenerator(brilFun.labels())
            brilFun
        }
    )
    val p2 = toSsa(p1)
    return p1
}

@kotlin.ExperimentalStdlibApi
fun main(args: Array<String>) {
    val argSet: TreeSet<String> = TreeSet()
    for (i in 0 ..< args.size) {
        argSet.add(args[i]!!)
    }

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

    val p0 = insertPreHeaders(p)
    println(adapter.toJson(p0))
    //val p1 = toSsa(p0)
    //val p2 = loopOptimize(p1)
    //println(adapter.toJson(p2))
}
