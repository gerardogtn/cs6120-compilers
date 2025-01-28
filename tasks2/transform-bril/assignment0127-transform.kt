import java.util.LinkedList
import java.util.TreeMap
import java.io.File
import java.io.IOException
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

// Bril JSON Parsing
// Using moshi for parsing.
data class BrilProgram(
    val functions: List<BrilFunction>,
    val pos: BrilSourcePosition?,
)

data class BrilFunction(
    val name: String,
    val args: List<BrilArg>?,
    val type: BrilType?,
    val instrs: List<BrilInstr>,
    val pos: BrilSourcePosition?,
)

data class BrilArg(
    val name: String,
    val type: BrilType,
    val pos: BrilSourcePosition?,
)

sealed interface BrilType

class BrilTypeAdapter {

    @FromJson
    fun fromJson(
        reader: JsonReader,
        parameterizedDelegate: JsonAdapter<BrilParameterizedType>,
    ): BrilType? {
        val nextToken = reader.peek()
        if (nextToken == JsonReader.Token.BEGIN_OBJECT) {
            return parameterizedDelegate.fromJson(reader)
        } else if (nextToken == JsonReader.Token.STRING) {
            return BrilPrimitiveType(reader.nextString(), null)
        } else {
            return null
        }
    }

    @ToJson
    fun toJson(
        writer: JsonWriter,
        brilType: BrilType?,
        parameterizedDelegate: JsonAdapter<BrilParameterizedType>,
    ) {
        when (brilType) {
            null -> writer.nullValue()
            is BrilPrimitiveType -> writer.value(brilType.name)
            is BrilParameterizedType -> parameterizedDelegate.toJson(writer, brilType)
        }
    }
}

data class BrilPrimitiveType(
    val name: String,
    val pos: BrilSourcePosition?,
) : BrilType

data class BrilParameterizedType(
    val map: Map<String, String>,
    val pos: BrilSourcePosition?,
) : BrilType

sealed interface BrilInstr

class BrilInstrAdapter {

    @FromJson
    fun fromJson(
        reader: JsonReader,
        labelDelegate: JsonAdapter<BrilLabel>,
        instructionDelegate: JsonAdapter<BrilOp>,
    ): BrilInstr? {
        var hasOp = false
        val peek = reader.peekJson()
        peek.beginObject()
        while (peek.hasNext()) {
            val name = peek.nextName()
            if (name == "op") {
                hasOp = true
            }
            peek.skipValue()
        }
        peek.endObject()
        return if (hasOp) {
            instructionDelegate.fromJson(reader)
        } else {
            labelDelegate.fromJson(reader)
        }
    }

    @ToJson
    fun toJson(
        writer: JsonWriter,
        instr: BrilInstr?,
        labelDelegate: JsonAdapter<BrilLabel>,
        instructionDelegate: JsonAdapter<BrilOp>,
    ) {
        when (instr) {
            null -> writer.nullValue()
            is BrilLabel -> labelDelegate.toJson(writer, instr)
            is BrilOp -> instructionDelegate.toJson(writer, instr) 
        }
    }
}

data class BrilLabel(
    val label: String,
    val pos: BrilSourcePosition?,
) : BrilInstr

sealed interface BrilPrimitiveValueType

class BrilPrimitiveValueTypeAdapter {

    @FromJson
    fun fromJson(
        reader: JsonReader,
    ) : BrilPrimitiveValueType? {
        val peek = reader.peek()
        return when (peek) {
            JsonReader.Token.BOOLEAN -> BrilPrimitiveValueBool(reader.nextBoolean())
            JsonReader.Token.NUMBER -> BrilPrimitiveValueInt(reader.nextInt())
            else -> null
        }
    }

    @ToJson
    fun toJson(
        writer: JsonWriter,
        primitiveType: BrilPrimitiveValueType?,
    ) {
        when (primitiveType) {
            is BrilPrimitiveValueInt -> writer.value(primitiveType.value)
            is BrilPrimitiveValueBool -> writer.value(primitiveType.value)
            else -> writer.nullValue()
        }
    }
}

data class BrilPrimitiveValueInt(
    val value: Int,
): BrilPrimitiveValueType

data class BrilPrimitiveValueBool(
    val value: Boolean,
): BrilPrimitiveValueType

data class BrilOp(
    val op: String,
    val dest: String?,
    val type: BrilType?,
    val value: BrilPrimitiveValueType?,
    val args: List<String>?,
    val funcs: List<String>?,
    val labels: List<String>?,
    val pos: BrilSourcePosition?,
): BrilInstr

data class BrilSourcePosition(
    val pos: BrilPos,
    val end: BrilPos?,
    val src: String?,
)

data class BrilPos(
    val row: Int,
    val col: Int,
)


// Generate 1,000 possible unique names for variables and parameters.
// They are all of the format color-material-item. Like plum-paper-camera, or rose-maple-bucket.
// Features:
// - Names have the same length.
// - Names have the same format.
// - Names are assigned at random.
fun generateNames(): LinkedList<String> {
    val colors = listOf(
        "pink", "blue", "gold", "ruby", "teal", "plum", "navy", "rust", "rose", "jade"
    )
    val materials = listOf(
        "steel", "brass", "paper", "glass", "nylon", "pearl", "amber", "maple", "cedar", "stone"
    )
    val items = listOf(
        "pencil", "bottle", "camera", "wallet", "ladder", "pillow", "bucket", "shield", "jacket", "hammer"
    )
    val result = LinkedList<String>()
    colors.forEach { color ->
        materials.forEach { material -> 
            items.forEach { item -> 
                result.add("$color-$material-$item")
            }
        }
    }
    return LinkedList(result.shuffled())
}

@kotlin.ExperimentalStdlibApi
fun parseFile(
    filename: String,
    adapter: JsonAdapter<BrilProgram>,
): BrilProgram? {
    val s = try {
        File(filename).readText()
    } catch(e: IOException) {
        null
    }
    return if (s != null) {
        adapter.fromJson(s)
    } else {
        null
    }
}

fun rename(
    vars: LinkedList<String>,
    program: BrilProgram,
): BrilProgram {
    return program.copy(
        functions = program.functions.map { function -> 
            renameFunction(vars, function)
        }
    )
}

fun renameFunction(
    vars: LinkedList<String>,
    function: BrilFunction,
): BrilFunction {
    val map = TreeMap<String, String>()
    function.args?.map { arg -> map.put(arg.name, nextName(vars)) }
    function.instrs?.map { instr -> 
        if (instr is BrilOp && instr.dest != null) {
            map.put(instr.dest, nextName(vars))
        }
    }
    return function.copy(
        args = function.args?.map { arg -> arg.copy(name = map.get(arg.name)!!) },
        instrs = function.instrs.map<BrilInstr, BrilInstr> { instr -> 
            if (instr is BrilOp) {
                instr.copy(
                    dest = if (instr.dest != null) {
                        map.get(instr.dest)!!
                    } else {
                        instr.dest
                    },
                    args = instr.args?.map { arg -> map.get(arg)!! },
                )
            } else {
                instr
            }
        },
    )
}

fun nextName(vars: LinkedList<String>): String {
    val name = vars.get(0)!!
    vars.removeAt(0)
    return name
}

@kotlin.ExperimentalStdlibApi
fun main(args: Array<String>) {
    //println("Assignment 2 - Jan/27 - Transforming Bril files")
    //println("convert variable names to unique memorable names")
    val filename = args[0]
    val names = generateNames()

     val moshi = Moshi.Builder()
        .add(BrilPrimitiveValueTypeAdapter())
        .add(BrilTypeAdapter())
        .add(BrilInstrAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()
    val adapter: JsonAdapter<BrilProgram> = moshi.adapter<BrilProgram>()

    val program = parseFile(filename, adapter)
    if (program != null) {
        val next = rename(names, program)
        val json = adapter.toJson(next)
        System.out.println(json)
    }
}
