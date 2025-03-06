
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

@kotlin.ExperimentalStdlibApi
fun main(args: Array<String>) {
  println("Task 6 - Static Single Assignment")
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

  println("Tasks 6 - Done")
}
