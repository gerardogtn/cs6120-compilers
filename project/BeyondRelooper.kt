
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

sealed interface ContainingSyntax
data class LoopHeadedBy(val node: DTree) : ContainingSyntax
data object IfThenElse : ContainingSyntax

typealias Context = List<ContainingSyntax>
fun doTree(
    node: DTree,
    rpo: Rpo,
    preds: Predecessors,
    blocks: Blocks,
    context: Context,
): List<BriloopInstr> {
    val (x, children) = node
    val ctxt0 = children.filter { (bid, _) -> isMergeNode(bid, rpo, preds) }
    val codeForX = nodeWithin(x, ctxt0, rpo, preds, blocks)
    val instr: List<BriloopInstr> = if (isLoopHeader(x, rpo, preds)) {
        val ctxt1 = listOf(LoopHeadedBy(node)) + context
        BriloopWhileStmt(
            arg = "t",
            body = codeForX(ctxt1),
        ).let { listOf(it) }
    } else {
        codeForX(context)
    }
    return instr
}

fun nodeWithin(
    bid: Int,
    nodes: List<DTree>,
    rpo: Rpo,
    preds: Predecessors,
    blocks: Blocks,
): (Context) -> List<BriloopInstr> {
    fun base(): (Context) -> List<BriloopInstr> = { context: Context -> 
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
                tru = doBranch(bid, lastBrilInstr.labelL, listOf(IfThenElse) + context),
                fals = doBranch(bid, lastBrilInstr.labelR, listOf(IfThenElse) + context),
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
    fun inductive(): (Context) -> List<BriloopInstr> = { context: Context -> 
        listOf<BriloopInstr>(
            BriloopBlockStmt(
                body = nodeWithin(bid, nodes.drop(1), rpo, preds, blocks)(listOf(LoopHeadedBy(nodes.first())) + context)
            )
        ) + doTree(nodes.first(), rpo, preds, blocks, context)
    }
    return if (nodes.isEmpty()) {
        base()
    } else {
        inductive()
    }
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
