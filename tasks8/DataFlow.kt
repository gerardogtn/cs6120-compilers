
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

data class DataflowStrategy<T>(
    val merge: (Collection<T>) -> T,
    val transfer: (Block) -> (T) -> T,
    val direction: Direction,
    val start: T,
    val pick: (TreeSet<Int>) -> Pair<Int, TreeSet<Int>> = { workset ->
        if (direction == Direction.FORWARDS) {
            workset.pollFirst() to workset
        } else {
            workset.pollLast() to workset
        }
    },
) {
    enum class Direction { FORWARDS, BACKWARDS }
}

inline fun <reified T> bigUnion(
    sets: Collection<TreeSet<T>>,
): TreeSet<T> {
    val out = TreeSet<T>()
    sets.forEach { s -> out.addAll(s) }
    return out
}

inline fun <reified T> bigIntersection(
    sets: Collection<TreeSet<T>>,
): TreeSet<T> {
    return sets.reduceOrNull { acc, s -> acc.filter { s.contains(it) }.let { TreeSet(it)  }}
        ?: TreeSet<T>()
} 

inline fun <reified T, reified V> combine(
    l: TreeMap<T, Option<V>>,
    r: TreeMap<T, Option<V>>,
): TreeMap<T, Option<V>> {
    val map = TreeMap<T, Pair<Option<V>, Option<V>>>()
    l.descendingKeySet().plus(r.descendingKeySet()).forEach { s ->
        map.put(s, (l.get(s) ?: None) to (r.get(s) ?: None))
    }

    val res = TreeMap<T, Option<V>>()
    map.forEach { s, (l, r) -> 
        if (l is Some && r is Some && l.value == r.value) {
            res.put(s, l)
        } else {
            res.put(s, None)
        }
    }
    return res
}

inline fun <reified K, reified V> bigIntersection(
    maps: Collection<TreeMap<K, Option<V>>>,
): TreeMap<K, Option<V>> {
    return maps.reduceOrNull{ a, s -> combine(a, s) }  ?: TreeMap<K, Option<V>>()
} 

fun reachableDefinitions(function: BrilFunction) = DataflowStrategy(
    merge = ::bigUnion,
    transfer = { block -> { inb: TreeSet<String> -> 
        val defined = block.mapNotNull { it.dest() }.let { TreeSet<String>(it) }
        TreeSet(defined.plus(inb))
    }},
    direction = DataflowStrategy.Direction.FORWARDS,
    start = function.args?.mapNotNull { it.name }.orEmpty().let { TreeSet<String>(it) }
)

sealed interface Option<out T>
data class Some<out T>(val value: T): Option<T>
data object None : Option<Nothing>

typealias Cp = TreeMap<String, Option<String>>
fun constantPropagation(
    function: BrilFunction,
) = DataflowStrategy(
    merge = ::bigIntersection,
    transfer = { block -> { inb -> 
        val res = Cp()
        val consts = Cp()
        res.putAll(inb)
        block.forEach { instr -> 
            if (instr is BrilConstOp && instr.dest != null) {
               consts.put(instr.dest, Some(instr.value.toString()))
            }
        }

        consts.forEach { k, v ->
            res.put(k, v)
        }
        res
    }},
    direction = DataflowStrategy.Direction.FORWARDS,
    start = Cp(),
)


data class DataflowResult<T>(
    val blocks: Blocks,
    val inm: TreeMap<Int, T>,
    val outm: TreeMap<Int, T>,
)

inline fun <reified T> DataflowResult<T>.formatted(
    id: Int,
    label: String,
): DataflowJson {
    return DataflowJson(
        id = id,
        label = label,
        inb = this.inm[id]!!.let { t -> 
            val set = TreeSet<String>()
            set.add("$t")
            set
        },
        outb = this.outm[id]!!.let { t ->
            val set = TreeSet<String>()
            set.add("$t")
            set
        }
    )
}

data class DataflowJson(
    val id: Int,
    val label: String,
    val inb: Set<String>,
    val outb: Set<String>,
)

inline fun <reified T> dataflow(
    strategy: DataflowStrategy<T>,
    function: BrilFunction,
): DataflowResult<T> {
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
    val inm = TreeMap<Int, T>()
    val outm = TreeMap<Int, T>()

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

    return DataflowResult<T>(
        blocks = blocks,
        inm = inm, 
        outm = outm,
    )
}

fun <K, V, Z> TreeMap<K, V>.mfold(z: Z, f: (Z, K, V) -> Z): Z {
    var result = z
    this.forEach { (k, v) -> 
        result = f(result, k, v)
    }
    return result
}
