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
fun naturalLoops(
    cfg: Cfg,
    backedges: BackEdges,
): LinkedList<NaturalLoop> {
    val res = LinkedList<NaturalLoop>()
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

fun insertPreHeaders(
    f: BrilFunction,
    loops: List<NaturalLoop>,
    nextLabel: NextLabel,
    blocks: Blocks,
): BrilFunction {
    val process = TreeSet<Int>()
    loops.forEach { (start, _, _) -> 
        process.add(start)
    }
    val instrs1 = LinkedList<BrilInstr>()
    blocks.forEachIndexed { i, block -> 
        if (i in process) {
            instrs1.add(
                BrilLabel(
                    label = nextLabel(),
                    pos = null,
                )
            )
        }
        instrs1.addAll(block)
    }
    return f.copy(
        instrs = instrs1
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
            insertPreHeaders(
                brilFun,
                naturalLoops,
                nextLabel,
                blocks
            )
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

    val p1 = toSsa(p)
    val p2 = loopOptimize(p1)
    println(adapter.toJson(p2))
}
