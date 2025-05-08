
import com.squareup.moshi.*
import java.util.LinkedList
import java.util.TreeSet
import java.util.TreeMap

// A map from block id to it's reverse post order. 
typealias Rpo = TreeMap<Int, Int>
fun reversePostOrder(
    cfg: Cfg,
): Rpo {
    val visited = TreeSet<Int>()
    val queue = LinkedList<Int>()
    val res = LinkedList<Int>()
    queue.add(0)

    while (queue.isNotEmpty()) {
        val h = queue.peekFirst()
        visited.add(h)
        var flipped = false
        cfg[h]!!.reversed().forEach { b -> 
            if (visited.contains(b).not()) {
                flipped = true
                queue.add(0, b)
            }
        }
        if (flipped.not()) {
            res.add(h)
            queue.pollFirst()
        }
    }

    val map = TreeMap<Int, Int>() 
    res.reversed().forEachIndexed { i, o -> 
        map[o] = i
    }
    return map
}

fun isMergeNode(
    bid: Int,
    rpo: Rpo,
    preds: Predecessors,
): Boolean {
    val count = preds[bid]!!.fold(0) { acc, pred -> 
        if (rpo[pred]!! < rpo[bid]!!) {
            acc + 1
        } else {
            acc
        }
    }
    return count >= 2
}

fun isLoopHeader(
    bid: Int,
    rpo: Rpo,
    preds: Predecessors,
): Boolean {
    return preds[bid]!!.any { 
        rpo[it]!! > rpo[bid]!! 
    }
}

typealias Context = List<Int>
fun doTree(
    node: DTree,
    rpo: Rpo,
    preds: Predecessors,
    blocks: Blocks,
    context: Context,
) {
    val (x, children) = node
    val ctxt0 = children.filter { (bid, _) -> isMergeNode(bid, rpo, preds) }.map { it.bid } 
    val codeForX = nodeWithin(x, ctxt0, blocks)
    if (isLoopHeader(x, rpo, preds)) {
        val ctxt1 = listOf(x) + context
        BriloopWhileStmt(
            arg = "t",
            body = codeForX(ctxt1),
        )
    } else {
        codeForX (context)
    }
}

fun nodeWithin(
    bid: Int,
    ctxt: Context,
    blocks: Blocks,
): (Context) -> List<BriloopInstr> {
    fun base() = { context: Context -> 
        val brilInstrs = blocks[bid]!!
        val briloopInstrs = LinkedList<BriloopInstr>()
        for (i in 0 ..< brilInstrs.size - 1) {
            val briloopInstr = brilInstrs[i]!!.toBriloop()
            if (briloopInstr != null) {
                briloopInstrs.add(briloopInstr)
            }
        }

        val lastBrilInstr = brilInstrs[brilInstrs.size - 1]!!
        
        val next: List<BriloopInstr> = if (lastBrilInstr is BrilJmpOp) {
            doBranch(bid, lastBrilInstr.label, context)
        } else if (lastBrilInstr is BrilBrOp) {
            BriloopIfThenStmt(
                arg = lastBrilInstr.arg,
                tru = doBranch(bid, lastBrilInstr.labelL, listOf(-1) + context),
                fals = doBranch(bid, lastBrilInstr.labelR, listOf(-1) + context),
            ).let { listOf<BriloopInstr>(it) }
        } else if (lastBrilInstr is BrilRetOp) {
            BriloopOp(
                op = "ret",
                args = lastBrilInstr.arg?.let { listOf(it) },
                dest = null,
                type = null,
                value = null,
                funcs = null,
            ).let{ it -> listOf<BriloopInstr>(it) }
        } else {
            throw IllegalStateException("Unexpected last node of block $lastBrilInstr")
        }

        briloopInstrs + next
    }
    return { ctxt -> emptyList() } 
}

fun doBranch(
    bid: Int,
    label: String,
    ctxt: Context,
) : List<BriloopInstr> {

    return emptyList()
}

fun beyondRelooper(
    func: BrilFunction
): BrilFunction {
    val (blocks, cfg) = cfg(func)
    val preds = predecessors(cfg)
    println("Preds")
    preds.forEach { println(it) }
    val doms = doms(blocks, cfg)
    val sdoms = flip_doms(doms)
    val idoms = idomsSearch(sdoms)

    val tree = dtree(idoms)
    println("dom tree")
    println(tree)

    val rpo = reversePostOrder(cfg)
    println("Rpo")
    println(rpo)

    println("Merge nodes")
    for (i in 0 ..< cfg.size) {
        val text = if (isMergeNode(i, rpo, preds)) {
            " is a merge node"
        } else {
            " is NOT a merge node"
        }
        println("Block $i $text")
    }

    println("Loop headers")
    for (i in 0 ..< cfg.size) {
        val text = if (isLoopHeader(i, rpo, preds)) {
            " is a loop header node"
        } else {
            " is NOT a loop header node"
        }
        println("Block $i $text")
    }


    return func
}

@kotlin.ExperimentalStdlibApi
fun main(args: Array<String>) {
    System.err.println("beyond relooper")
    val moshi = moshi()
    val programAdapter = brilProgram(args, moshi)
    if (programAdapter == null) {
        System.err.println("No program found")
        return
    }

    val (adapter, program) = programAdapter
    program.functions.forEach { func -> 
        beyondRelooper(func)
    }
}
