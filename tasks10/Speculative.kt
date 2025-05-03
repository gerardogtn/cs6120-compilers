
import com.squareup.moshi.*
import java.util.TreeSet
import java.util.TreeMap
import java.util.LinkedList
import java.io.File
import okio.*

// Traces are a list of basic blocks. 
typealias Trace = List<String>
typealias Traces = ArrayList<Trace>

fun traces(
    moshi: Moshi,
    filename: String,
): Traces {
    val type0 = Types.newParameterizedType(List::class.java, String::class.java)
    val type = Types.newParameterizedType(List::class.java, type0)
    val adapter = moshi.adapter<Traces>(type)
    val file = File(filename)
    val source = file.source().buffer()
    val parsed =  adapter.fromJson(source)!!.map { it!!}
    return Traces(parsed)
}

fun merged(
    trace: Trace,
    blocks: Blocks,
    labelToBlock: LabelToBlock,
): List<BrilInstr>? {

    // A block is a candidate if all the operations in the blocks are 
    // not side effects.
    // In such a case we concatenate all instructions. 
    val acc = LinkedList<BrilInstr>()
    var i = 0
    while (i < trace.size) {
        val label = trace[i]
        val bid = labelToBlock[label] ?: return null
        val block = blocks[bid] 
        var j = block.size - 1
        while (j > 0) {
            val instr = block[j]
            if (instr.hasSideEffects() && (instr !is BrilBrOp && instr !is BrilJmpOp)) {
                return null
            }
            acc.add(0, instr)
            j = j - 1
        }
        i = i + 1
    }
    return acc
}

fun stitch(
    blocks: Blocks,
    loop: Trace,
    merged: List<BrilInstr>,
) : List<BrilInstr> {
    val guards: List<String> = merged.mapNotNull {
        if (it is BrilBrOp) {
            it.arg
        } else {
            null
        }
    }
    
    // let's build the new block
    val loopHeader = loop.last()
    val newLabel = "__merged"
    val nojumps = merged.filter {
        it !is BrilBrOp  && it !is BrilJmpOp
    }

    // unroll the blocks four times
    val unrolled = LinkedList<BrilInstr>()
    unrolled.add(BrilLabel(label = newLabel))
    unrolled.add(BrilSpeculateOp())
    (1..4).map { unrolled.addAll(nojumps) }
    guards.forEach { g -> 
        // if the speculative execution fails, just jump to the original loop. 
        unrolled.add(BrilGuardOp(arg = g, label = loopHeader))
    }
    unrolled.add(BrilCommitOp())
    unrolled.add(BrilJmpOp(op = "jmp", label = newLabel))

    // build the resulting function. 
    var mapped = blocks.flatMap { block -> 
        block.map { instr -> 
            instr.replaceLabel { s -> 
                if (s == loopHeader) {
                    newLabel
                } else {
                    s
                }
            }
        }
    }

    val result = LinkedList<BrilInstr>()
    result.addAll(mapped)
    result.addAll(unrolled)
    return result
}

fun jitUnroll(
    func: BrilFunction,
    sortedTraces: Traces,
): BrilFunction {
    val (blocks, cfg) = cfg(func)
    val labelToBlock = labelToBlock(blocks)

    var i = 0 
    var merged: List<BrilInstr>? = null
    var trace: Trace? = null
    while (i < sortedTraces.size) {
        trace = sortedTraces[i]
        merged = merged(trace, blocks, labelToBlock)
        if (merged != null) {
            break
        }
        i = i + 1
    }

    if (merged == null || trace == null) {
        return func;
    }
    
    val stitched = stitch(
        blocks = blocks,
        loop = trace,
        merged = merged
    )

    return func.copy(
        instrs = stitched
    )
}

fun jitUnroll(
    program: BrilProgram,
    sortedTraces: Traces,
): BrilProgram {
    return program.copy(
        functions = program.functions.map { func -> jitUnroll(func, sortedTraces) }
    )
}

@kotlin.ExperimentalStdlibApi
fun main(args: Array<String>) {
    val moshi = moshi()
    // Traces
    val traceFilename = args[0]
    val traces = traces(moshi, traceFilename)
    // We'll optimize the shortest trace that is a trace candidate
    val sortedTraces: Traces = traces.sortedBy { it.size }.let { ArrayList(it) }

    // Program
    val programAdapter = brilProgram(args, moshi)
    if (programAdapter == null) {
        System.err.println("No program found")
        return
    }
    val (adapter, program) = programAdapter

    val unrolled = jitUnroll(program, sortedTraces)
    val dced = dce(unrolled)
    val lvned = lvn(dced)

    val optimized = lvned
    println(adapter.toJson(optimized))
}
