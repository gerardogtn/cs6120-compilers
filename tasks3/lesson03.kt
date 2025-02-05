import java.util.LinkedList
import java.util.TreeMap
import java.util.TreeSet
import java.io.File
import java.io.IOException
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.buffer
import okio.source
import okio.Okio
import BrilIdOp

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
        mapDelegate: JsonAdapter<Map<String, String>>,
    ): BrilType? {
        val nextToken = reader.peek()
        return if (nextToken == JsonReader.Token.BEGIN_OBJECT) {
            BrilParameterizedType(
                map = mapDelegate.fromJson(reader),
                pos = null,
            )
        } else if (nextToken == JsonReader.Token.STRING) {
            BrilPrimitiveType(reader.nextString(), null)
        } else {
             null
        }
    }

    @ToJson
    fun toJson(
        writer: JsonWriter,
        brilType: BrilType?,
        mapDelegate: JsonAdapter<Map<String, String>>,
    ) {
        when (brilType) {
            null -> writer.nullValue()
            is BrilPrimitiveType -> writer.value(brilType.name)
            is BrilParameterizedType -> mapDelegate.toJson(writer, brilType.map)
        }
    }
}

data class BrilPrimitiveType(
    val name: String,
    val pos: BrilSourcePosition?,
) : BrilType

data class BrilParameterizedType(
    val map: Map<String, String>?,
    val pos: BrilSourcePosition?,
) : BrilType

sealed interface BrilInstr

fun BrilInstr.dest(): String? {
    return when(this) {
        is BrilConstOp -> this.dest
        is BrilAddOp -> this.dest
        is BrilMulOp -> this.dest
        is BrilSubOp -> this.dest
        is BrilDivOp -> this.dest
        is BrilNotOp -> this.dest
        is BrilAndOp -> this.dest
        is BrilOrOp -> this.dest
        is BrilEqOp -> this.dest
        is BrilLeOp -> this.dest
        is BrilGeOp -> this.dest
        is BrilLtOp -> this.dest
        is BrilGtOp -> this.dest
        is BrilCallOp -> this.dest
        is BrilIdOp -> this.dest
        else -> null
    }
}

fun BrilInstr.type(): BrilType? {
    return when(this) {
        is BrilConstOp -> this.type
        is BrilAddOp -> this.type
        is BrilMulOp -> this.type
        is BrilSubOp -> this.type
        is BrilDivOp -> this.type
        is BrilNotOp -> this.type
        is BrilAndOp -> this.type
        is BrilOrOp -> this.type
        is BrilEqOp -> this.type
        is BrilLeOp -> this.type
        is BrilGeOp -> this.type
        is BrilLtOp -> this.type
        is BrilGtOp -> this.type
        is BrilCallOp -> this.type
        is BrilIdOp -> this.type
        else -> null
    }
}


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
            JsonReader.Token.NUMBER -> {
                try {
                    BrilPrimitiveValueInt(reader.nextInt())
                } catch (e: JsonDataException) {
                    BrilPrimitiveValueDouble(reader.nextDouble())
                }
            }
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
            is BrilPrimitiveValueDouble -> writer.value(primitiveType.value)
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

data class BrilPrimitiveValueDouble(
    val value: Double,
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

data class BrilUnknownOp(
    override val op: String,
    val dest: String?,
    val type: BrilType?,
    val value: BrilPrimitiveValueType?,
    val args: List<String>?,
    val funcs: List<String>?,
    val labels: List<String>?,
    val pos: BrilSourcePosition?,
): BrilOp
 
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
            else -> BrilUnknownOp(
                op = brilOpJson.op,
                dest = brilOpJson.dest,
                type = brilOpJson.type,
                value = brilOpJson.value,
                args = brilOpJson.args,
                funcs = brilOpJson.funcs,
                labels = brilOpJson.labels,
                pos = brilOpJson.pos,
            )
        }
    }

    @ToJson
    fun toJson(
        brilOp: BrilOp
    ): BrilOpJson {
        return when(brilOp) {
            is BrilUnknownOp -> BrilOpJson(
                op = brilOp.op,
                dest = brilOp.dest,
                type = brilOp.type,
                value = brilOp.value,
                args = brilOp.args,
                funcs = brilOp.funcs,
                labels = brilOp.labels,
                pos = brilOp.pos,
            )
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
                args = listOf(brilOp.arg),
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
): String? {
    return try {
        File(filename).readText()
    } catch(e: IOException) {
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

sealed interface LocalValue 

data class LocalSumValue(
    val l: Int,
    val r: Int,
): LocalValue

data class LocalConstValue(
    val literal: BrilPrimitiveValueType,
): LocalValue

data class LocalIdValue(
    val pos: Int,
): LocalValue

typealias Lvn = TreeMap<Int, LocalValue>
typealias LvnContext = TreeMap<String, Int>
typealias LvnEnv = TreeMap<Int, String>
typealias LvnVars = TreeMap<Int, Pair<String, BrilType>>

fun BrilInstr.toLocalValue(
    var2p: LvnContext,
    val2p: Map<LocalValue, Int>,
): LocalValue? {
    return when (this) {
        is BrilAddOp -> this.toLocalValue(var2p, val2p)
        is BrilConstOp -> LocalConstValue(this.value)
        is BrilIdOp -> this.toLocalValue(var2p)
        else -> null
    }
}

fun BrilIdOp.toLocalValue(
    var2p: LvnContext,
): LocalValue? {
    if (!var2p.contains(this.arg)) {
        return null;
    }
    return LocalIdValue(var2p.get(this.arg)!!)
}

fun BrilAddOp.toLocalValue(
    var2p: LvnContext,
    val2p: Map<LocalValue, Int>,
): LocalValue? {
    if (!var2p.contains(this.argL)) {
        return null;
    }
    if (!var2p.contains(this.argR)) {
        return null;
    }
    val value = LocalSumValue(
        l = var2p.get(this.argL)!!,
        r = var2p.get(this.argR)!!,
    )
    return if (val2p.contains(value)) {
        LocalIdValue(
            pos = val2p.get(value)!!
        )
    } else {
        value
    }
}

fun lvn(
    block: Block
) : Block {
    val p2val = Lvn()
    val val2p = HashMap<LocalValue, Int>()
    val p2var = TreeMap<Int, String>()
    val var2p = LvnContext()
    block.forEachIndexed { i, instr -> 
        val localValue: LocalValue? = instr.toLocalValue(
            var2p,
            val2p
        )
        if (localValue != null) {
            p2val.put(i, localValue)
            val2p.put(localValue, i)
            instr.dest()?.let { varname ->
                p2var.put(i, varname)
                var2p.put(varname, i)
            }
        }
    }
    val res = Block()
    block.forEachIndexed { i, instr -> 
        val instr = if (!p2val.contains(i)) {
            instr
        } else {
            p2val.get(i)!!.toInstr(instr, i, p2var)   
        }
        res.add(instr)
    }
    return res
}

fun LocalValue.toInstr(
    instr: BrilInstr,
    i: Int,
    p2var: Map<Int, String>,
): BrilInstr {
    return when(val localValue = this) {
        is LocalConstValue -> instr
        is LocalIdValue -> BrilIdOp(
            op = "id",
            dest = instr.dest()!!,
            type = instr.type()!!,
            arg = p2var.get(localValue.pos)!!
        )
        is LocalSumValue -> BrilAddOp(
            op = "add", 
            dest = instr.dest()!!, 
            type = instr.type()!!,
            argL = p2var.get(localValue.l)!!,
            argR = p2var.get(localValue.r)!!,
        )
    }
}

fun BrilInstr.renameVars(
    swaps: Map<String, String>
): BrilInstr {
    return when(this) {
        is BrilAddOp -> this.copy(
            argL = swaps.get(argL) ?: argL,
            argR = swaps.get(argR) ?: argR,
        )
        is BrilMulOp -> this.copy(
            argL = swaps.get(argL) ?: argL,
            argR = swaps.get(argR) ?: argR,
        ) 
        is BrilSubOp -> this.copy(
            argL = swaps.get(argL) ?: argL,
            argR = swaps.get(argR) ?: argR,
        ) 
        is BrilDivOp -> this.copy(
            argL = swaps.get(argL) ?: argL,
            argR = swaps.get(argR) ?: argR,
        )  
        is BrilAndOp -> this.copy(
            argL = swaps.get(argL) ?: argL,
            argR = swaps.get(argR) ?: argR,
        ) 
        is BrilOrOp -> this.copy(
            argL = swaps.get(argL) ?: argL,
            argR = swaps.get(argR) ?: argR,
        ) 
        is BrilNotOp -> this.copy(
            arg = swaps.get(arg) ?: arg,
        )
        is BrilEqOp -> this.copy(
            argL = swaps.get(argL) ?: argL,
            argR = swaps.get(argR) ?: argR,
        )  
        is BrilGtOp -> this.copy(
            argL = swaps.get(argL) ?: argL,
            argR = swaps.get(argR) ?: argR,
        ) 
        is BrilLtOp -> this.copy(
            argL = swaps.get(argL) ?: argL,
            argR = swaps.get(argR) ?: argR,
        ) 
        is BrilGeOp -> this.copy(
            argL = swaps.get(argL) ?: argL,
            argR = swaps.get(argR) ?: argR,
        ) 
        is BrilLeOp -> this.copy(
            argL = swaps.get(argL) ?: argL,
            argR = swaps.get(argR) ?: argR,
        ) 
        is BrilCallOp -> this.copy(
            args = this.args?.map { swaps.get(it) ?: it },
        )
        is BrilRetOp -> this.copy(
            arg = this.arg?.let { swaps.get(it) ?: it },
        )
        is BrilIdOp -> this.copy(
            arg = swaps.get(arg) ?: arg,
        )
        is BrilPrintOp -> this.copy(
            args = this.args?.map { swaps.get(it) ?: it },
        )
        is BrilUnknownOp -> this.copy(
            args = this.args?.map { swaps.get(it) ?: it },
        )
        else -> this
    }
}

fun lvn(
    function: BrilFunction,
): BrilFunction {
    val blocks = blocks(function)
    val nblocks = blocks.map { b -> 
        var prev: Block? = null
        var curr = b
        while (curr != prev) {
            prev = curr
            curr = lvn(curr)
        }
        curr
    }

    return function.copy(
        instrs = nblocks.flatten()
    )
}

@kotlin.ExperimentalStdlibApi
fun main(args: Array<String>) {
    //println("Assignment 3 - Feb/06 - Local Value Numbering")
    //println("build basic blocks and CFG")
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

    if (program != null) {
        val nprogram = program.copy(
            functions = program.functions.map(::lvn),
        )
        val json = adapter.toJson(nprogram)
        println(json)
    }
}
