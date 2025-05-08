
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
    labelToBlock: LabelToBlock,
): List<BriloopInstr> {
    val (x, children) = node
    val mergeNodes = children.filter { (bid, _) -> isMergeNode(bid, rpo, preds) }
    val codeForX = nodeWithin(node, mergeNodes, rpo, preds, blocks, labelToBlock)
    val instr: List<BriloopInstr> = if (isLoopHeader(x, rpo, preds)) {
        val ctxt1 = listOf(LoopHeadedBy(node)) + context
        val name = generateVariableName()
        val arg = BriloopOp(
            op = "const",
            value = BriloopValueBoolean(true),
            type = BriloopPrimitiveType("bool"),
            dest = name,
            args = null,
            funcs = null,
        )
        System.err.println("Creating while for $name")
        val whil = BriloopWhileStmt(
            arg = name,
            body = codeForX(ctxt1),
        )
        listOf(arg, whil)
    } else {
        codeForX(context)
    }
    return instr
}

fun nodeWithin(
    node: DTree,
    nodes: List<DTree>,
    rpo: Rpo,
    preds: Predecessors,
    blocks: Blocks,
    labelToBlock: LabelToBlock,
): (Context) -> List<BriloopInstr> {
    fun base(): (Context) -> List<BriloopInstr> = { context: Context -> 
        val bid = node.bid
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
            doBranch(node, labelToBlock[lastBrilInstr.label]!!, context, labelToBlock, rpo, preds, blocks)
        } else if (lastBrilInstr is BrilBrOp) {
            BriloopIfThenStmt(
                arg = lastBrilInstr.arg,
                tru = doBranch(node, labelToBlock[lastBrilInstr.labelL]!!, listOf(IfThenElse) + context, labelToBlock, rpo, preds, blocks),
                fals = doBranch(node, labelToBlock[lastBrilInstr.labelR]!!, listOf(IfThenElse) + context, labelToBlock, rpo, preds, blocks),
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
            briloopInstrs.add(lastBrilInstr.toBriloop()!!)
            doBranch(node, node.bid + 1, context, labelToBlock, rpo, preds, blocks) 
            //throw IllegalStateException("Unexpected last node of block $lastBrilInstr")
        }

        briloopInstrs + next
    }
    fun inductive(): (Context) -> List<BriloopInstr> = { context: Context -> 
        listOf<BriloopInstr>(
            BriloopBlockStmt(
                body = nodeWithin(node, nodes.drop(1), rpo, preds, blocks, labelToBlock)(listOf(LoopHeadedBy(nodes.first())) + context)
            )
        ) + doTree(nodes.first(), rpo, preds, blocks, context, labelToBlock)
    }
    return if (nodes.isEmpty()) {
        base()
    } else {
        inductive()
    }
}

var i = 0
fun generateVariableName(): String {
    val name = "__v$i"
    i = i + 1
    return name
}

fun doBranch(
    node: DTree,
    nextbid: Int,
    ctxt: Context,
    labelToBlock: LabelToBlock,
    rpo: Rpo,
    preds: Predecessors,
    blocks: Blocks,
) : List<BriloopInstr> {
    val (bid, children) = node
    val target = nextbid 

    // is backward edge
    return if (rpo[target]!! < rpo[bid]!!) {
        val name = generateVariableName()
        val i =  ctxt.indexOfFirst { (it is LoopHeadedBy) && (it.node.bid == target) }
        val ival = BriloopValueInt(i)
        val cont = BriloopOp(
            op = "continue",
            value = null,
            type = null,
            dest = null,
            args = null,
            funcs = null,
        )
       listOf(
           cont
        )
    // is Merge Label
    } else if (isMergeNode(target, rpo, preds)) {
        val name = generateVariableName()
        val i =  ctxt.indexOfFirst { (it is LoopHeadedBy) && (it.node.bid == target) }
        val ival = BriloopValueInt(i)
        val iOp= BriloopOp(
            op = "const",
            value = ival,
            type  = BriloopPrimitiveType("int"),
            dest = name,
            args = null,
            funcs = null,
        )
        val brStmt = BriloopBreakStmt(
            arg = name,
        )
        listOf(
            iOp,
            brStmt,
        )
    } else {
        doTree(
            node = children.first { it.bid == target } ,
            rpo = rpo,
            preds = preds,
            blocks = blocks,
            context = ctxt,
            labelToBlock = labelToBlock,
        )
    }
}

fun beyondRelooper(
    func: BrilFunction
): BriloopFunction {
    val (blocks, cfg) = cfg(func)
    val preds = predecessors(cfg)
    System.err.println("Preds")
    preds.forEach { System.err.println(it) }
    val doms = doms(blocks, cfg)
    val sdoms = flip_doms(doms)
    val idoms = idomsSearch(sdoms)

    val tree = dtree(idoms)
    System.err.println("dom tree")
    System.err.println(tree)

    val rpo = reversePostOrder(cfg)
    System.err.println("Rpo")
    System.err.println(rpo)
    
    val labelToBlock = labelToBlock(blocks)

    System.err.println("Merge nodes")
    for (i in 0 ..< cfg.size) {
        val text = if (isMergeNode(i, rpo, preds)) {
            " is a merge node"
        } else {
            " is NOT a merge node"
        }
        System.err.println("Block $i $text")
    }

    System.err.println("Loop headers")
    for (i in 0 ..< cfg.size) {
        val text = if (isLoopHeader(i, rpo, preds)) {
            " is a loop header node"
        } else {
            " is NOT a loop header node"
        }
        System.err.println("Block $i $text")
    }
    val brilooped = doTree(tree, rpo, preds, blocks, emptyList(), labelToBlock)

    return BriloopFunction(
        name = func.name,
        args = func.args?.map { 
            val arg: BriloopArg = it.toBriloop()
            arg
        },
        type = func.type.toBriloop(),
        instrs = brilooped,
    )
}

@kotlin.ExperimentalStdlibApi
fun main(args: Array<String>) {
    System.err.println("beyond relooper")
    val moshi = moshi()
    val brilAdapterProgram = brilProgram(args, moshi)
    if (brilAdapterProgram == null) {
        System.err.println("No program found")
        return
    }

    val (_, brilProgram) = brilAdapterProgram
    val briloopFuncs = brilProgram.functions.map { func -> 
        beyondRelooper(func)
    }
    val briloopProgram = BriloopProgram(
        functions = briloopFuncs,
    )
    val briloopAdapter = moshi.adapter<BriloopProgram>()
    val json = briloopAdapter.toJson(briloopProgram)

    println(json)
}
