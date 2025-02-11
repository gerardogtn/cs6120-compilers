
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
    fun merge(
        sets: Collection<TreeSet<String>>
    ): TreeSet<String> {
       // union
       return sets.fold(TreeSet<String>()) { s, acc -> 
            acc.addAll(s)
            acc
       }
    }

    fun transfer(
        block: Block,
        inb: TreeSet<String>,
    ): TreeSet<String> {
        return inb;
    }

    fun pick(
        worklist: LinkedList<Int>,
    ): Int {
        return worklist.first()
    }
}

class SimpleStrategy : DataflowStrategy()

data class DataflowResult(
    val inm: Map<Int, Set<String>>,
    val outm: Map<Int, Set<String>>,
)

fun dataflow(
    strategy: DataflowStrategy,
    function: BrilFunction,
    c: TreeSet<String> = TreeSet<String>(),
): DataflowResult {
    fun predecessors(bid: Int): TreeSet<Int> = TODO()
    fun successors(bid: Int): TreeSet<Int> = TODO()

    // data structures.
    val n = 0 // TODO
    val inm = TreeMap<Int, TreeSet<String>>()
    val outm = TreeMap<Int, TreeSet<String>>()

    // initialization.
    // inm[entry] = c todo: 
    for (i in 0 .. n) {
        outm[i] = c
    }

    val worklist = LinkedList<Int>() // TODO: Add items to the worklist.
    while (worklist.isNotEmpty()) {
        val bid: Int = strategy.pick(worklist)
        val b: Block = TODO()
        inm[bid] = predecessors(bid).map { outm[it]!! }.let {
            strategy.merge(it)
        }
        val prev = outm[bid]!!
        outm[bid] = strategy.transfer(b, inm[bid]!!)
        if (prev != outm[bid]) {
            worklist.addAll(successors(bid))
        }
    }

    return DataflowResult(inm, outm)
}


@kotlin.ExperimentalStdlibApi
fun main(args: Array<String>) {
    println("Lesson 4 - Data Flow")
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

    val strategy = SimpleStrategy()
    val result = dataflow(strategy, program.functions!!.first())
    val resultAdapter: JsonAdapter<DataflowResult> = moshi.adapter<DataflowResult>()
    val json = resultAdapter.toJson(result)
    println(json)

}
