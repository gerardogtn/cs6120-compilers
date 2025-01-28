import java.util.LinkedList
import java.util.TreeMap
import java.util.TreeSet
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

// Main code
typealias Block = LinkedList<BrilInstr>
fun blocks(brilFunction: BrilFunction): List<Block> {
    val result = LinkedList<Block>()
    var curr = LinkedList<BrilInstr>()

    brilFunction.instrs.forEach { instr -> 
        when (instr) {
            is BrilLabel -> {
                if (curr.isNotEmpty()) {
                    result.add(curr)
                    curr = LinkedList<BrilInstr>()
                }
                curr.add(instr)
            }
            is BrilOp -> {
                curr.add(instr)
                if (instr.op == "jmp" || instr.op == "br") {
                    result.add(curr)
                    curr = LinkedList<BrilInstr>()
                }
            }
        }
    }

    if (curr.isNotEmpty()) {
        result.add(curr)
    }
    return result
}

fun blocksToIds(
    blocks: List<Block>
): TreeMap<Int, Block> {
    val result = TreeMap<Int, Block>()
    blocks.forEachIndexed { i, b -> 
        result.put(i, b)
    }
    return result
}

fun labelToBlock(
    blocks: List<Block>
): TreeMap<String, Int> {
    val result = TreeMap<String, Int>()
    blocks.forEachIndexed { i, b -> 
        when (val l = b.firstOrNull()) {
            is BrilLabel -> {
                result.put(l.label, i)
            }
            else -> Unit
        }
    }
    return result
}

fun cfg(
    blocks: List<Block>,
    labelToBlock: TreeMap<String, Int>,
): TreeMap<Int, TreeSet<Int>> {
    val result = TreeMap<Int, TreeSet<Int>>()
    blocks.forEachIndexed { i, b -> 
        val edgesOut = TreeSet<Int>()
        val last = b.lastOrNull() as? BrilOp
        if (last?.op == "jmp") {
            edgesOut.add(labelToBlock.get(last?.labels?.first()!!)!!)
        } else if (last?.op == "br") {
            edgesOut.add(labelToBlock.get(last?.labels?.get(0)!!)!!)
            edgesOut.add(labelToBlock.get(last?.labels?.get(1)!!)!!)
        } else if (i < blocks.size - 1) {
            edgesOut.add(i + 1)
        }
        result.put(i, edgesOut)
    }
    return result
}

@kotlin.ExperimentalStdlibApi
fun main(args: Array<String>) {
    //println("Assignment 2 - Jan/27 - Basic Blocks & CFGs")
    //println("build basic blocks and CFG")
    val filename = args[0]

    val moshi = Moshi.Builder()
        .add(BrilPrimitiveValueTypeAdapter())
        .add(BrilTypeAdapter())
        .add(BrilInstrAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()
    val adapter: JsonAdapter<BrilProgram> = moshi.adapter<BrilProgram>()
    val brilInstrAdapter = moshi.adapter<BrilInstr>()
    val program = parseFile(filename, adapter)
    if (program != null) {
        val function = program.functions.first()
        val blocks = blocks(function)
        println("Blocks are:")
        blocks.forEachIndexed { i, b -> 
            println("Block $i")
            b.forEach { instr -> 
                val instrJson = brilInstrAdapter.toJson(instr)
                println(instrJson) 
            } 
        }
        println("Labels are")
        val labels = labelToBlock(blocks)
        labels.forEach { (l, b) -> 
            println(l)
        }
        println("CFG")
        val cfg = cfg(blocks, labels)
        cfg.forEach { (id, next) -> 
            println("Block: $id. Connected to $next")
        }

    }
}
