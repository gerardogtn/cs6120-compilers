
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
 
fun doms(
    blocks: List<Block>,
    cfg: Cfg,
): TreeMap<Int, TreeSet<Int>> {
    fun bigcap(c: Collection<TreeSet<Int>>): TreeSet<Int> {
        val res = TreeSet<Int>()
        for (s in c) {
            res.addAll(s)
        }
        return res
    }
    var res = TreeMap<Int, TreeSet<Int>>()
    // Starting point, all blocks are dominated by all others.
    blocks.forEachIndexed { i, b -> 
        val all = TreeSet<Int>()
        for(i in 0..blocks.size) {
            all.add(i)
        }
        res.put(i, all)
    }
    res.put(0, TreeSet<Int>().also { it.add(0)})

    var prev: TreeMap<Int, TreeSet<Int>>? = null
    while (prev != res) {
        prev = res
        res = TreeMap(res)
        // starting at 1 to ignore entry
        for(i in 1..blocks.size) {
            val preds = predecessors(i, cfg).map{ res.get(it)!! }
            val bigcap = bigcap(preds)
            bigcap.add(i)
            res.put(i, bigcap)
        }
    }
    return res
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
  val doms = doms(blocks, cfg)
  doms.forEach { println(it) }
  println("yup")
}
