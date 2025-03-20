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

fun detectLoopInvariants(
    l: NaturalLoop,
    inm: TreeMap<Int, TreeSet<String>>,
    outm: TreeMap<Int, TreeSet<String>>,
    blocks: Blocks,
    cfg: Cfg,
) : LinkedList<BrilInstr> {
    val (s, e, m) = l
    val loopBody = TreeSet<Int>()
    loopBody.add(s)
    loopBody.addAll(m)
    loopBody.add(e)

    // check if the instruction is loop invariant
    val loopInvariants = LinkedList<BrilInstr>()

    // * FIRST PASS - check if all operands defined outside
    for (bid in loopBody) {
        val block = blocks[bid]!!
        for (instr in block) {
            if (instr is BrilOp) {
                // * change if condition instead of checking for null
                if (instr.dest() == null) {
                    continue
                }
                val operands = instr.args()
                var isLoopInvariant = true

                // * check if all operands are defined outside the loop using inm of preheader
                for (operand in operands) {

                    if (operand !in inm[s-1]!!) {
                        isLoopInvariant = false
                        break
                    }

                }

                if (isLoopInvariant) {
                    loopInvariants.add(instr)
                }
            }
        }
    }

    // * REPEATED PASS - all reaching definitions outside of loop OR 
    // * exactly one definition from a loop invariant statement inside the loop
    // * repeat until no more loop invariants can be found

    var changed = true
    // ? need to actually mutate at the end of the pass
    while (changed) {
        changed = false
        for (bid in loopBody) {
            val block = blocks[bid]!!
            for (instr in block) {
                if (instr is BrilOp) {
                    // * change if condition instead of checking for null
                    if (instr.dest() == null) {
                        continue
                    }
                    val operands = instr.args()
                    var isLoopInvariant = true

                    // * check if all operands are defined outside the loop OR exactly one definition from a loop invariant statement inside the loop
                    var loopInvariantInside = 0
                    for (operand in operands) {
                        var defined = false
                        if (operand !in inm[s - 1]!!) {

                            // * check if the operand is loop invariant
                            // * need to go through each op, if its an assignment to the operand then check that it is loop invariant
                            for (bid2 in loopBody) {
                                if (bid2 == bid) {
                                    continue
                                }
                                val block2 = blocks[bid2]!!
                                for (instr2 in block2) {
                                    if (instr2 is BrilOp && instr2.dest() == operand) {
                                        defined = true
                                        // * check if the instruction is loop invariant
                                        if (loopInvariants.contains(instr2)) {
                                            loopInvariantInside += 1
                                            if (loopInvariantInside > 1) {
                                                isLoopInvariant = false
                                                break
                                            }
                                        } else {
                                            isLoopInvariant = false
                                            break
                                        }
                                    }
                                }
                            }

                            if (loopInvariantInside > 1 || !defined) {
                                isLoopInvariant = false
                                break
                            }
                        }
                    }

                    if (isLoopInvariant) {
                        // * check if the instruction is already in the loop invariants
                        if (!loopInvariants.contains(instr)) {
                            loopInvariants.add(instr)
                            changed = true
                        }
                    }

                }
            }
        }
    }


    return loopInvariants
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
            // cfg.forEach { System.err.println(it) }
            val isdom = doms(blocks, cfg)
            val strictDominates = flip_doms(isdom, strict = true)
            // strictDominates.forEach { System.err.println(it) }
            val backedges = backedges(cfg, strictDominates)
            // backedges.forEach { System.err.println(it) }
            val naturalLoops = naturalLoops(cfg, backedges)
            // naturalLoops.forEach { System.err.println(it) }
            val nextLabel = nextLabelGenerator(brilFun.labels())
            brilFun
        }
    )

    // * iterate through p1 functions, get cfgs, blocks, and natural loops.
    // * for each natural loop, get the preheader and the loop body.
    // * for each instruction in the loop body, check if it is a candidate for code motion.
    // *  - this means it is loop invariant, 
    // *  - in blocks that dominate all exits of the loop
    // *  - assign to variable not assigned anywhere else in the loop.
    // *  - in blocks that dominate all blocks in loop that use the variable.
    // * perform a depth first search of the blocks:
    // * move candidate to preheader if all invariant operations it depends on have been moved

    for (f in p1.functions) {
        val (blocks, cfg) = cfg(f)
        val isdom = doms(blocks, cfg)
        val strictDominates = flip_doms(isdom, strict = true)
        val backedges = backedges(cfg, strictDominates)
        val naturalLoops = naturalLoops(cfg, backedges)
        // println("Natural loops: $naturalLoops")
        val nextLabel = nextLabelGenerator(f.labels())
        val strategy = reachableDefinitions(f)
        val result = dataflow(strategy, f)
        val (b, inm, outm) = result
        // println("Blocks: $b")
        // println("INM: $inm")
        // println("OUTM: $outm")

        for (loop in naturalLoops) {
            val (s, e, m) = loop
            val loopBody = TreeSet<Int>()
            loopBody.add(s)
            loopBody.addAll(m)
            loopBody.add(e)

            // check if the instruction is loop invariant
            val loopInvariants = detectLoopInvariants(loop, inm, outm, blocks, cfg)
            println("Loop invariants: $loopInvariants")

            // // move the loop invariants to the preheader
            // for (instr in loopInvariants) {
            //     val preheader = nextLabel()
            //     val newInstr = instr.copy(dest = preheader)
            //     f.instrs.add(0, newInstr)
            //     f.instrs.remove(instr)
            // }
        }

    }

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
    //println(adapter.toJson(p0))
    val p1 = toSsa(p0)
    val p2 = dce(p1)
    val p3 = loopOptimize(p2)
    // println(adapter.toJson(p3))
}
