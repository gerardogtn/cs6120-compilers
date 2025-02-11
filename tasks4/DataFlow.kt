
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

sealed class DataflowStrategy {
    
    abstract val start: TreeSet<String>
    fun merge(
        sets: Collection<TreeSet<String>>
    ): TreeSet<String> {
       // union
       return sets.fold(TreeSet<String>()) { s, acc -> 
            acc.addAll(s)
            acc
       }
    }

    abstract fun transfer(
        block: Block,
        inb: TreeSet<String>,
    ): TreeSet<String>

    fun pick(
        worklist: LinkedList<Int>,
    ): Pair<Int, LinkedList<Int>> {
        return worklist.first() to LinkedList(worklist.drop(1))
    }
}

class ReachableDefinitionsStrategy(
    override val start: TreeSet<String> = TreeSet(),
): DataflowStrategy() {

    override fun transfer(
        block: Block,
        inb: TreeSet<String>,
    ): TreeSet<String> {
        val defined = block.mapNotNull { it.dest() }.let { TreeSet<String>(it) }
        return TreeSet(defined.plus(inb.minus(defined)))
    }
}

data class DataflowResult(
    val blocks: Blocks,
    //val b2l: Map<Int, String>,
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
    fun predecessors(bid: Int): TreeSet<Int> {
        val res = TreeSet<Int>()
        cfg.forEach { (b, n) -> 
            if (b != bid && n.contains(bid)) {
                res.add(b)
            }
        }
        return res
    }
    fun successors(bid: Int): TreeSet<Int> = cfg[bid]!!

    // data structures.
    val n = cfg.size
    val inm = TreeMap<Int, TreeSet<String>>()
    val outm = TreeMap<Int, TreeSet<String>>()

    // initialization.
    val entry = 0
    inm[entry] = strategy.start 
    for (i in 0 .. n) {
        outm[i] = strategy.start
    }

    var worklist = LinkedList<Int>()
    cfg.forEach { (bid, _) -> worklist.add(bid) }
    while (worklist.isNotEmpty()) {
        val (bid, next) = strategy.pick(worklist)
        worklist = next
        val b: Block = blocks[bid]!!
        inm[bid] = predecessors(bid).map { outm[it]!! }.let {
            strategy.merge(it)
        }
        val prev = outm[bid]!!
        outm[bid] = strategy.transfer(b, inm[bid]!!)
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

    val strategy = ReachableDefinitionsStrategy()
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
