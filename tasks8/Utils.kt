import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.*
import okio.*

@kotlin.ExperimentalStdlibApi
fun program(
    filename: String?,
): Pair<JsonAdapter<BrilProgram>, BrilProgram?> {
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

    return adapter to adapter.fromJson(source)
} 


