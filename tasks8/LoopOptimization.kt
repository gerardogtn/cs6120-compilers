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

// Block to index to instruction
typealias Invariants = TreeMap<Int, TreeMap<Int, BrilInstr>>
fun isInvariant(
    b: Int,
    i: Int,
    instr: BrilInstr,
    invariants: Invariants,
    result: DataflowResult<TreeSet<String>>,
    loop: TreeSet<Int>,
): Boolean {
    return instr is BrilConstOp
}

fun licm(
    naturalLoop: NaturalLoop,
    brilFun: BrilFunction,
): BrilFunction {
    val strategy = reachableDefinitions(brilFun)
    val result = dataflow(strategy, brilFun)
    val loopBlocks = TreeSet<Int>()
    val (blocks, cfg) = cfg(brilFun)
    val (s, e, m) = naturalLoop
    loopBlocks.add(s)
    loopBlocks.add(e)
    loopBlocks.addAll(m)
    var curr = Invariants()
    var prev: Invariants? = null
    for (b in loopBlocks) {
        curr[b] = TreeMap()
    }
    // definition dominates all of its uses, 
    // no other definitions of same variable exist in loop
    while (prev != curr) {
        prev = curr
        curr = TreeMap(curr)
        for (b in loopBlocks) {
            val block = blocks[b]!!
            for (i in 0 ..< block.size) {
                val instr = block[i]!!
                var isInvariant = instr.args()?.all { 
                    result.outm[s - 1]!!.contains(it)
                } ?: false
                isInvariant = isInvariant && (instr !is BrilLabel) && (instr !is BrilJmpOp) && (instr !is BrilBrOp) && (instr !is BrilPhiOp) && (instr !is BrilCallOp) && (instr !is BrilPrintOp) && (instr !is BrilDivOp)
                if (isInvariant) {
                    // * check if definition dominates all uses
                    for (n in cfg[b]!!) {
                        if (n !in loopBlocks) {
                            continue
                        }
                        for (j in 0 ..< blocks[n]!!.size) {
                            val instr2 = blocks[n]!![j]!!
                            if (instr2 is BrilJmpOp || instr2 is BrilBrOp) {
                                continue
                            }
                            if (instr2.args()?.contains(instr.dest()!!) == true) {
                                isInvariant = false
                                break
                            }
                        }
                    }
                    curr[b]!![i] = instr 
                }
            }
        }
    }
    // curr will contain the invariants
    val invariants = curr
    
    val dests = TreeSet<String>()
    //System.err.println("invariants")
    for (b in loopBlocks) {
        // System.err.println("block $b")
        invariants[b]!!.forEach { (i, instr) ->
            // System.err.println(instr)
            instr.dest()?.let { dests.add(it) }
        }
    }

    // * check if definitions dominate all uses
    for (b in loopBlocks) {
        for (i in 0 ..< blocks[b]!!.size) {
            val instr = blocks[b]!![i]!!
            if (instr is BrilJmpOp || instr is BrilBrOp) {
                continue
            }
            instr.args()?.forEach { arg ->
                if (dests.contains(arg)) {
                    System.err.println("arg $arg")
                    if (result.inm[b]!!.contains(arg).not()) {
                        System.err.println("not dominated")
                        dests.remove(arg)
                        System.err.println("removing $arg")
                        System.err.println("removing from invariants: $invariants[b]!![$i]")
                        invariants[b]!!.remove(i)
                    }
                }
            }
        }
    }

    val unmovable = LinkedList<String>()
    val exits = TreeSet<Int>()
    for (b in loopBlocks) {
        for (n in cfg[b]!!) {
            if (n !in loopBlocks) {
                exits.add(n)
            }
        }
    }
    //System.err.println("loop exits")
    //System.err.println(exits)

    for (exit in exits) {
        for (instr in blocks[exit]!!) {
            if (dests.any { instr.args()?.contains(it) ?: false }) {
                unmovable.add(instr.dest()!!)
            }
        }
    }

    for (i in loopBlocks) {
        invariants[i]!!.forEach { _, instr -> 
            if (instr.dest() !in unmovable) {
                blocks[s - 1].appendToBlock(instr)
                blocks[i].remove(instr)
            }
        }
    }
    return brilFun.copy(
        instrs = blocks.flatMap {
            it
        }
    )
}

fun Block.appendToBlock(
    instr: BrilInstr,
) {
    var i = this.size - 1
    val last = this[i]!!
    if (last is BrilJmpOp || last is BrilRetOp || last is BrilBrOp) {
        i = i - 1
    }
    this.add(i + 1, instr)
}

fun loopOptimize(
    brilFun: BrilFunction,
): BrilFunction {
    val (blocks, cfg) = cfg(brilFun)
    //System.err.println("cfg")
    //cfg.forEach { System.err.println(it) }
    val isdom = doms(blocks, cfg)
    val strictDominates = flip_doms(isdom, strict = true)
    //System.err.println("strict dominates")
    //strictDominates.forEach { System.err.println(it) }
    val backedges = backedges(cfg, strictDominates)
    //System.err.println("backedges")
    //backedges.forEach { System.err.println(it) }
    //System.err.println("naturalLoops")
    val naturalLoops = naturalLoops(cfg, backedges)


    // Natural loops won't change on optimization so there
    // is no need to recalculate them, but reachable definitions
    // might change so we need to recompute them. 
    var optimized = brilFun
    naturalLoops.forEach {
        optimized = licm(it, optimized)
    }
    return optimized
}

// p is a bril program in ssa-form with preheaders.
fun loopOptimize(
    p: BrilProgram,
): BrilProgram {
    // p is the ssa-program with preheaders.
    // If you have identified that block b starts a natural loop,
    // then you can be sure that block (b - 1) is the preheader of the loop.
    val p1 = p.copy(
        functions = p.functions.map { brilFun -> 
            loopOptimize(brilFun)
       }
    )
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
    if (argSet.contains("--preheaders")) {
        println(adapter.toJson(p0))
        return
    }
    //println(adapter.toJson(p0))
    System.err.println(p0.functions.first().instrs.first())
    val p1 = toSsa(p0)
    System.err.println(p1.functions.first().instrs.first())
    val p2: BrilProgram = lvn(p1)
    System.err.println(p2.functions.first().instrs.first())
    val p3 = loopOptimize(p2)
    System.err.println(p3.functions.first().instrs.first())
    println(adapter.toJson(p3))
}
