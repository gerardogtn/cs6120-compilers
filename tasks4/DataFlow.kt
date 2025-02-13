
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.LinkedList;
import com.squareup.moshi.*;
import java.io.File;
import java.io.IOException;
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.buffer
import okio.source
import okio.Okio
 
@kotlin.ExperimentalStdlibApi
fun parseFile(
    filename: String,
): String? {
    return try {
        File(filename).readText()
    } catch(e: IOException) {
        null
    }
}

data class DataflowStrategy(
    val merge: (Collection<TreeSet<String>>) -> TreeSet<String>,
    val transfer: (Block) -> (TreeSet<String>) -> TreeSet<String>,
    val direction: Direction,
    val start: TreeSet<String>,
    val pick: (TreeSet<Int>) -> Pair<Int, TreeSet<Int>> = { workset ->
        if (direction == Direction.FORWARDS) {
            workset.pollFirst() to workset
        } else {
            workset.pollLast() to workset
        }
    },
) {
    enum class Direction { FORWARDS, BACKWARDS }
    object Merge {
        val BigUnion = { sets: Collection<TreeSet<String>> ->
            val out = TreeSet<String>()
            sets.forEach { s -> out.addAll(s) }
                out
        }
        val BigIntersection = { sets: Collection<TreeSet<String>> ->
            sets.reduceOrNull { acc, s -> acc.filter { s.contains(it) }.let { TreeSet(it)  }}
                ?: TreeSet<String>()
        }
    } 
}

fun reachableDefinitions(function: BrilFunction) = DataflowStrategy(
    merge = DataflowStrategy.Merge.BigUnion,
    transfer = { block -> { inb -> 
        val defined = block.mapNotNull { it.dest() }.let { TreeSet<String>(it) }
        TreeSet(defined.plus(inb))
    }},
    direction = DataflowStrategy.Direction.FORWARDS,
    start = function.args?.mapNotNull { it.name }.orEmpty().let { TreeSet<String>(it) }
)

data class DataflowResult(
    val blocks: Blocks,
    val inm: TreeMap<Int, TreeSet<String>>,
    val outm: TreeMap<Int, TreeSet<String>>,
)

data class DataflowJson(
    val id: Int,
    val label: String,
    val inb: Set<String>,
    val outb: Set<String>,
)

fun dataflow(
    strategy: DataflowStrategy,
    function: BrilFunction,
): DataflowResult {
    val (blocks, cfg: Cfg) = cfg(function)
    val predecessors: (Int) -> TreeSet<Int> = { bid ->
        val res = TreeSet<Int>()
        cfg.forEach { (b, n) -> 
            if (b != bid && n.contains(bid)) {
                res.add(b)
            }
        }
        res
    }
    val successors: (Int) -> TreeSet<Int> = { bid -> cfg[bid]!! }

    // data structures.
    val n = cfg.size
    val (preds, succs, entry) = when (strategy.direction) {
        DataflowStrategy.Direction.FORWARDS -> {
            Triple(predecessors, successors, 0)
        } 
        DataflowStrategy.Direction.BACKWARDS -> {
            Triple(successors, predecessors, n - 1)
        }
    }
    val inm = TreeMap<Int, TreeSet<String>>()
    val outm = TreeMap<Int, TreeSet<String>>()

    // initialization.
    inm[entry] = strategy.start
    for (i in 0 .. n) {
        outm[i] = strategy.start
    }

    var worklist = TreeSet<Int>()
    cfg.forEach { (bid, _) -> worklist.add(bid) }
    while (worklist.isNotEmpty()) {
        val (bid, next) = strategy.pick(worklist)
        worklist = next
        val b: Block = blocks[bid]!!
        inm[bid] = predecessors(bid).map { outm[it]!! }.let {
            strategy.merge(it)
        }
        val prev = outm[bid]!!
        outm[bid] = strategy.transfer(b)(inm[bid]!!)
        if (prev != outm[bid]) {
            worklist.addAll(successors(bid))
        }
    }

    return DataflowResult(
        blocks = blocks,
        inm = inm, 
        outm = outm,
    )
}


@kotlin.ExperimentalStdlibApi
fun main(args: Array<String>) {
    //println("Lesson 4 - Data Flow")
    val moshi = Moshi.Builder()
        .add(BrilPrimitiveValueTypeAdapter())
        .add(BrilTypeAdapter())
        .add(BrilInstrAdapter())
        .add(BrilOpAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()
    val adapter: JsonAdapter<BrilProgram> = moshi.adapter<BrilProgram>()
    val brilInstrAdapter = moshi.adapter<BrilInstr>()
    val source = if (args.size > 0 && args[0].startsWith("-f")) {
        val file = File(args[1])
        val source = file.source()
        source.buffer()
    } else {
        System.`in`.source().buffer()
    }

    val program = adapter.fromJson(source)

    if (program == null) {
        println("No program")
        return 
    }

    val strategy = reachableDefinitions(program.functions!!.first())
    val result = dataflow(strategy, program.functions!!.first())
    
    val dfjs = result.blocks.mapIndexed { i, b ->
        DataflowJson(
            id = i,
            label = "$i",
            inb = result.inm[i]!!,
            outb = result.outm[i]!!,
        )
    }
    val dfjsonAdapter: JsonAdapter<List<DataflowJson>> = moshi.adapter<List<DataflowJson>>()
    val jsonstring = dfjsonAdapter.toJson(dfjs)
    println(jsonstring)
}
