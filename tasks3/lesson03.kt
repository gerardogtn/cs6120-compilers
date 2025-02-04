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

data class BrilOpJson(
    val op: String,
    val dest: String?,
    val type: BrilType?,
    val value: BrilPrimitiveValueType?,
    val args: List<String>?,
    val funcs: List<String>?,
    val labels: List<String>?,
    val pos: BrilSourcePosition?,
)

sealed interface BrilOp : BrilInstr {
    val op: String
}

data class BrilConstOp(
    override val op: String,
    val dest: String?,
    val type: BrilType?,
    val value: BrilPrimitiveValueType,
): BrilOp

data class BrilAddOp(
    override val op: String,
    val dest: String?,
    val type: BrilType?,
    val argL: String,
    val argR: String,
): BrilOp

data class BrilMulOp(
    override val op: String,
    val dest: String?,
    val type: BrilType?,
    val argL: String,
    val argR: String,
): BrilOp

data class BrilSubOp(
    override val op: String,
    val dest: String?,
    val type: BrilType?,
    val argL: String,
    val argR: String,
): BrilOp
 
data class BrilDivOp(
    override val op: String,
    val dest: String?,
    val type: BrilType?,
    val argL: String,
    val argR: String,
): BrilOp
 
data class BrilEqOp(
    override val op: String,
    val dest: String?,
    val type: BrilType?,
    val argL: String,
    val argR: String,
): BrilOp
 
data class BrilLtOp(
    override val op: String,
    val dest: String?,
    val type: BrilType?,
    val argL: String,
    val argR: String,
): BrilOp
 
data class BrilGtOp(
    override val op: String,
    val dest: String?,
    val type: BrilType?,
    val argL: String,
    val argR: String,
): BrilOp
 
data class BrilLeOp(
    override val op: String,
    val dest: String?,
    val type: BrilType?,
    val argL: String,
    val argR: String,
): BrilOp
 
data class BrilGeOp(
    override val op: String,
    val dest: String?,
    val type: BrilType?,
    val argL: String,
    val argR: String,
): BrilOp
 
data class BrilNotOp(
    override val op: String,
    val dest: String?,
    val type: BrilType?,
    val arg: String,
): BrilOp
 
data class BrilAndOp(
    override val op: String,
    val dest: String?,
    val type: BrilType?,
    val argL: String,
    val argR: String,
): BrilOp
 
data class BrilOrOp(
    override val op: String,
    val dest: String?,
    val type: BrilType?,
    val argL: String,
    val argR: String,
): BrilOp
 
data class BrilJmpOp(
    override val op: String,
    val label: String
): BrilOp

data class BrilBrOp(
    override val op: String,
    val arg: String,
    val labelL: String,
    val labelR: String,
): BrilOp

data class BrilCallOp(
    override val op: String,
    val dest: String?,
    val type: BrilType?,
    val func: String,
    val args: List<String>?,
): BrilOp

data class BrilRetOp(
    override val op: String,
    val arg: String?,
): BrilOp

data class BrilIdOp(
    override val op: String,
    val dest: String?,
    val type: BrilType?,
    val arg: String,
): BrilOp

data class BrilPrintOp(
    override val op: String,
    val args: List<String>?,
): BrilOp

data class BrilNopOp(
    override val op: String
): BrilOp

class BrilOpAdapter {

    @FromJson
    fun fromJson(
        brilOpJson: BrilOpJson,
    ) : BrilOp {
        return when(brilOpJson.op) {
            "const" -> BrilConstOp(
                op = brilOpJson.op,
                dest = brilOpJson.dest,
                type = brilOpJson.type,
                value = brilOpJson.value!!,
            )
            "add" -> BrilAddOp(
                op = brilOpJson.op,
                dest = brilOpJson.dest,
                type = brilOpJson.type,
                argL = brilOpJson.args!!.get(0)!!,
                argR = brilOpJson.args!!.get(1)!!,
            )
            "mul" -> BrilMulOp(
                op = brilOpJson.op,
                dest = brilOpJson.dest,
                type = brilOpJson.type,
                argL = brilOpJson.args!!.get(0)!!,
                argR = brilOpJson.args!!.get(1)!!,
            ) 
            "sub" -> BrilSubOp(
                op = brilOpJson.op,
                dest = brilOpJson.dest,
                type = brilOpJson.type,
                argL = brilOpJson.args!!.get(0)!!,
                argR = brilOpJson.args!!.get(1)!!,
            )  
            "div" -> BrilDivOp(
                op = brilOpJson.op,
                dest = brilOpJson.dest,
                type = brilOpJson.type,
                argL = brilOpJson.args!!.get(0)!!,
                argR = brilOpJson.args!!.get(1)!!,
            ) 
            "eq" -> BrilEqOp(
                op = brilOpJson.op,
                dest = brilOpJson.dest,
                type = brilOpJson.type,
                argL = brilOpJson.args!!.get(0)!!,
                argR = brilOpJson.args!!.get(1)!!,
            ) 
            "le" -> BrilLeOp(
                op = brilOpJson.op,
                dest = brilOpJson.dest,
                type = brilOpJson.type,
                argL = brilOpJson.args!!.get(0)!!,
                argR = brilOpJson.args!!.get(1)!!,
            ) 
            "ge" -> BrilGeOp(
                op = brilOpJson.op,
                dest = brilOpJson.dest,
                type = brilOpJson.type,
                argL = brilOpJson.args!!.get(0)!!,
                argR = brilOpJson.args!!.get(1)!!,
            ) 
            "lt" -> BrilLtOp(
                op = brilOpJson.op,
                dest = brilOpJson.dest,
                type = brilOpJson.type,
                argL = brilOpJson.args!!.get(0)!!,
                argR = brilOpJson.args!!.get(1)!!,
            ) 
            "gt" -> BrilGtOp(
                op = brilOpJson.op,
                dest = brilOpJson.dest,
                type = brilOpJson.type,
                argL = brilOpJson.args!!.get(0)!!,
                argR = brilOpJson.args!!.get(1)!!,
            ) 
            "not" -> BrilNotOp(
                op = brilOpJson.op,
                dest = brilOpJson.dest,
                type = brilOpJson.type,
                arg = brilOpJson.args!!.get(0)!!
            )
            "and" -> BrilAndOp(
                op = brilOpJson.op,
                dest = brilOpJson.dest,
                type = brilOpJson.type,
                argL = brilOpJson.args!!.get(0)!!,
                argR = brilOpJson.args!!.get(1)!!,
            ) 
            "or" -> BrilOrOp(
                op = brilOpJson.op,
                dest = brilOpJson.dest,
                type = brilOpJson.type,
                argL = brilOpJson.args!!.get(0)!!,
                argR = brilOpJson.args!!.get(1)!!,
            ) 
            "jmp" -> BrilJmpOp(
                op = brilOpJson.op,
                label = brilOpJson.labels!!.get(0)!!,
            )
            "br" -> BrilBrOp(
                op = brilOpJson.op,
                arg = brilOpJson.args!!.get(0)!!,
                labelL = brilOpJson.labels!!.get(0)!!,
                labelR = brilOpJson.labels!!.get(1)!!,
            )
            "call" -> BrilCallOp(
                op = brilOpJson.op,
                dest = brilOpJson.dest,
                type = brilOpJson.type,
                func = brilOpJson.funcs!!.get(0)!!,
                args = brilOpJson.args,
            )
            "ret" -> BrilRetOp(
                op = brilOpJson.op,
                arg = brilOpJson.args?.get(0),
            )
            "id" -> BrilIdOp(
                op = brilOpJson.op,
                dest = brilOpJson.dest,
                type = brilOpJson.type,
                arg = brilOpJson.args!!.get(0)!!,
            )
            "print" -> BrilPrintOp(
                op = brilOpJson.op,
                args = brilOpJson.args,
            )
            "nop" -> BrilNopOp(
                op = brilOpJson.op,
            )
            else -> throw IllegalStateException("Unexpected bril type $brilOpJson")
        }
    }

    @ToJson
    fun toJson(
        brilOp: BrilOp
    ): BrilOpJson {
        return when(brilOp) {
            is BrilAddOp -> BrilOpJson(
                op = brilOp.op,
                dest = brilOp.dest,
                type = brilOp.type,
                value = null,
                args = listOf(brilOp.argL, brilOp.argR),
                funcs = null,
                labels = null,
                pos = null,
            )
            is BrilMulOp -> BrilOpJson(
                op = brilOp.op,
                dest = brilOp.dest,
                type = brilOp.type,
                value = null,
                args = listOf(brilOp.argL, brilOp.argR),
                funcs = null,
                labels = null,
                pos = null,
            )
            is BrilSubOp -> BrilOpJson(
                op = brilOp.op,
                dest = brilOp.dest,
                type = brilOp.type,
                value = null,
                args = listOf(brilOp.argL, brilOp.argR),
                funcs = null,
                labels = null,
                pos = null,
            )
            is BrilDivOp -> BrilOpJson(
                op = brilOp.op,
                dest = brilOp.dest,
                type = brilOp.type,
                value = null,
                args = listOf(brilOp.argL, brilOp.argR),
                funcs = null,
                labels = null,
                pos = null,
            )
            is BrilEqOp -> BrilOpJson(
                op = brilOp.op,
                dest = brilOp.dest,
                type = brilOp.type,
                value = null,
                args = listOf(brilOp.argL, brilOp.argR),
                funcs = null,
                labels = null,
                pos = null,
            )
            is BrilLtOp -> BrilOpJson(
                op = brilOp.op,
                dest = brilOp.dest,
                type = brilOp.type,
                value = null,
                args = listOf(brilOp.argL, brilOp.argR),
                funcs = null,
                labels = null,
                pos = null,
            )
            is BrilGtOp -> BrilOpJson(
                op = brilOp.op,
                dest = brilOp.dest,
                type = brilOp.type,
                value = null,
                args = listOf(brilOp.argL, brilOp.argR),
                funcs = null,
                labels = null,
                pos = null,
            )
            is BrilLeOp -> BrilOpJson(
                op = brilOp.op,
                dest = brilOp.dest,
                type = brilOp.type,
                value = null,
                args = listOf(brilOp.argL, brilOp.argR),
                funcs = null,
                labels = null,
                pos = null,
            )
            is BrilGeOp -> BrilOpJson(
                op = brilOp.op,
                dest = brilOp.dest,
                type = brilOp.type,
                value = null,
                args = listOf(brilOp.argL, brilOp.argR),
                funcs = null,
                labels = null,
                pos = null,
            )
            is BrilNotOp -> BrilOpJson(
                op = brilOp.op,
                dest = brilOp.dest,
                type = brilOp.type,
                value = null,
                args = listOf(brilOp.arg),
                funcs = null,
                labels = null,
                pos = null,
            )
            is BrilAndOp -> BrilOpJson(
                op = brilOp.op,
                dest = brilOp.dest,
                type = brilOp.type,
                value = null,
                args = listOf(brilOp.argL, brilOp.argR),
                funcs = null,
                labels = null,
                pos = null,
            )
            is BrilOrOp -> BrilOpJson(
                op = brilOp.op,
                dest = brilOp.dest,
                type = brilOp.type,
                value = null,
                args = listOf(brilOp.argL, brilOp.argR),
                funcs = null,
                labels = null,
                pos = null,
            )
            is BrilJmpOp -> BrilOpJson(
                op = brilOp.op,
                dest = null,
                type = null,
                value = null,
                args = null,
                funcs = null,
                labels = listOf(brilOp.label),
                pos = null,
            )
            is BrilBrOp -> BrilOpJson(
                op = brilOp.op,
                dest = null,
                type = null,
                value = null,
                args = null,
                funcs = null,
                labels = listOf(brilOp.labelL, brilOp.labelR),
                pos = null,
            )
            is BrilCallOp -> BrilOpJson(
                op = brilOp.op,
                dest = brilOp.dest,
                type = brilOp.type,
                value = null,
                args = brilOp.args,
                funcs = listOf(brilOp.func),
                labels = null,
                pos = null,
            )
            is BrilRetOp -> BrilOpJson(
                op = brilOp.op,
                dest = null,
                type = null,
                value = null,
                args = brilOp.arg?.let { listOf(it) },
                funcs = null,
                labels = null,
                pos = null,
            )
            is BrilIdOp -> BrilOpJson(
                op = brilOp.op,
                dest = brilOp.dest,
                type = brilOp.type,
                value = null,
                args = listOf(brilOp.arg),
                funcs = null,
                labels = null,
                pos = null,
            )
            is BrilPrintOp -> BrilOpJson(
                op = brilOp.op,
                dest = null,
                type = null,
                value = null,
                args = brilOp.args,
                funcs = null,
                labels = null,
                pos = null,
            )
            is BrilNopOp -> BrilOpJson(
                op = brilOp.op,
                dest = null,
                type = null,
                value = null,
                args = null,
                funcs = null,
                labels = null,
                pos = null,
            )
            is BrilConstOp -> BrilOpJson(
                op = brilOp.op,
                dest = brilOp.dest,
                type = brilOp.type,
                value = brilOp.value,
                args = null,
                funcs = null,
                labels = null,
                pos = null,
            )
        }

    }
}

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

typealias Cfg = TreeMap<Int, TreeSet<Int>>

fun cfg(
    blocks: List<Block>,
    labelToBlock: TreeMap<String, Int>,
): Cfg {
    val result = Cfg()
    blocks.forEachIndexed { i, b -> 
        val edgesOut = TreeSet<Int>()
        val last = b.lastOrNull() as? BrilOp
        if (last is BrilJmpOp) {
            edgesOut.add(labelToBlock.get(last.label)!!)
        } else if (last is BrilBrOp) {
            edgesOut.add(labelToBlock.get(last.labelL)!!)
            edgesOut.add(labelToBlock.get(last.labelR)!!)
        } else if (i < blocks.size - 1) {
            edgesOut.add(i + 1)
        }
        result.put(i, edgesOut)
    }
    return result
}

@kotlin.ExperimentalStdlibApi
fun main(args: Array<String>) {
    println("Assignment 3 - Feb/06 - Local Value Numbering")
    //println("build basic blocks and CFG")
    val filename = args[0]

    val moshi = Moshi.Builder()
        .add(BrilPrimitiveValueTypeAdapter())
        .add(BrilTypeAdapter())
        .add(BrilInstrAdapter())
        .add(BrilOpAdapter())
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
