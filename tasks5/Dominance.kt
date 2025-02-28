
import com.squareup.moshi.*
import okio.*
import java.util.TreeSet
import java.util.TreeMap
import java.io.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@kotlin.ExperimentalStdlibApi
fun program(
    filename: String?,
): BrilProgram? {
 val moshi = Moshi.Builder()
        .add(BrilPrimitiveValueTypeAdapter())
        .add(BrilTypeAdapter())
        .add(BrilInstrAdapter())
        .add(BrilOpAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()
    val adapter: JsonAdapter<BrilProgram> = moshi.adapter<BrilProgram>()
    val brilInstrAdapter = moshi.adapter<BrilInstr>()
    val source = if (filename != null) {
        val file = File(filename)
        val source = file.source()
        source.buffer()
    } else {
        System.`in`.source().buffer()
    }

    return adapter.fromJson(source)
} 

fun <Z> Cfg.mfold(z: Z, f: (Z, Int, TreeSet<Int>) -> Z): Z {
    var result = z
    this.forEach { k, v -> 
        result = f(result, k, v)
    }
    return result
}

fun predecessors(
    v: Int,
    cfg: Cfg,
): TreeSet<Int> {
    var result = TreeSet<Int>()
    cfg.forEach { v1, next -> 
        if (v != v1 && next.contains(v)) {
            result.add(v1)
        }
    }
    return result
}

typealias Doms = TreeMap<Int, TreeSet<Int>>
fun doms(
    blocks: List<Block>,
    cfg: Cfg,
): Doms {
    fun bigcap(c: Collection<TreeSet<Int>>): TreeSet<Int> {
        return c.reduceOrNull { acc, s ->
            acc.filter { s.contains(it) }.let { TreeSet(it) }
        }
        ?: TreeSet<Int>()
    }
    var res = TreeMap<Int, TreeSet<Int>>()
    // Starting point, all blocks are dominated by all others.
    blocks.forEachIndexed { i, b -> 
        val all = TreeSet<Int>()
        for(i in 0..<blocks.size) {
            all.add(i)
        }
        res.put(i, all)
    }
    res.put(0, TreeSet<Int>().also { it.add(0)})

    var prev: Doms? = null
    while (prev != res) {
        prev = res
        res = TreeMap(res)
        // starting at 1 to ignore entry
        for(i in 1..<blocks.size) {
            println(res)
            val preds = predecessors(i, cfg)
            val preddoms = preds.map{ TreeSet(res.get(it)!!) }
            val bigcap = bigcap(preddoms)
            bigcap.add(i)
            res.put(i, bigcap)
        }
    }
    return res
}

data class DTree(
    val bid: Int,
    val children: List<DTree>
)

typealias Idoms = TreeMap<Int, TreeSet<Int>>
fun idoms(
    doms: Doms,
) : Idoms {
    val res = TreeMap<Int, Int?>()
    for (i in 0..<doms.size) {
        res[i] = null
    }
    // remove reflexive relation
    for (i in 0 ..< doms.size) {
        doms[i]?.remove(i)
    }

    val visited = TreeSet<Int>()
    visited.add(0)
    for (i in 0 ..< doms.size) {
        for (j in 0 ..< doms.size) {
            if (visited.contains(i)) {
                continue
            }
            doms[j]!!.remove(i)
            if (doms[j]!!.isEmpty()) {
                visited.add(j)
                res[i] = j
            }
        }
    }
    
    val output = Idoms()
    for (i in 0 ..< doms.size) {
        output[i] = TreeSet<Int>()
    }
    res.forEach { a, b -> 
        if (b != null) {
            output[b]!!.add(a)
        }
    }

    return output
}

fun dtree(
    idoms: Idoms,
    // By default we bulid the dtree of the first basic block in the function
    start: Int = 0,
): DTree {
    return DTree(
        start,
        children = idoms[start]!!.mapNotNull { 
            dtree(idoms, it)
        }
    )
}
@kotlin.ExperimentalStdlibApi
fun main(args: Array<String>) {
  println("Task 5 - Dominance relations")
  val filename = if (args.size > 0 && args[0].startsWith("-f")) {
      args[1]
  } else {
      null
  }
  val p = program(filename)

  if (p == null) {
      println("Invalid input")
      return
  }
  
  val function = p.functions.first()
  val (blocks, cfg) = cfg(function)
  println("cfg")
  cfg.forEach { println(it) }
  val doms = doms(blocks, cfg)
  println("doms")
  doms.forEach { println(it) }
  println("idoms")
  val idoms = idoms(Doms(doms))
  idoms.forEach { println(it)}
  val dtree = dtree(idoms)
  println(dtree)
}
