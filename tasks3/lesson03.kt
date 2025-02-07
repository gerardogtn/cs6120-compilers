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

sealed interface LocalValue

data class LocalConstValue(
    val literal: BrilPrimitiveValueType,
): LocalValue

data class LocalIdValue(
    val pos: Int,
): LocalValue

data class LocalAddOp(
    val l: Int,
    val r: Int,
    val dest: String?,
    val type: BrilType?,
): LocalValue

data class LocalMulOp(
    val l: Int,
    val r: Int,
    val dest: String?,
    val type: BrilType?,
): LocalValue

data class LocalSubOp(
    val l: Int,
    val r: Int,
    val dest: String?,
    val type: BrilType?,
): LocalValue

data class LocalDivOp(
    val l: Int,
    val r: Int,
    val dest: String?,
    val type: BrilType?,
): LocalValue

data class LocalEqOp(
    val l: Int,
    val r: Int,
    val dest: String?,
    val type: BrilType?,
): LocalValue

data class LocalLeOp(
    val l: Int,
    val r: Int,
    val dest: String?,
    val type: BrilType?,
): LocalValue

data class LocalGeOp(
    val l: Int,
    val r: Int,
    val dest: String?,
    val type: BrilType?,
): LocalValue

data class LocalLtOp(
    val l: Int,
    val r: Int,
    val dest: String?,
    val type: BrilType?,
): LocalValue

data class LocalGtOp(
    val l: Int,
    val r: Int,
    val dest: String?,
    val type: BrilType?,
): LocalValue

typealias Lvn = TreeMap<Int, LocalValue>
typealias LvnContext = TreeMap<String, Int>
typealias LvnEnv = TreeMap<Int, String>
typealias LvnVars = TreeMap<Int, Pair<String, BrilType>>

fun BrilInstr.toLocalValue(
    var2p: LvnContext,
    val2p: Map<LocalValue, Int>,
    p2val: Map<Int, LocalValue>,
): LocalValue? {
    return when (this) {
        is BrilConstOp -> LocalConstValue(this.value)
        is BrilIdOp -> this.toLocalValue(var2p)
        is BrilAddOp -> this.toLocalValue(var2p, val2p, p2val)
        is BrilMulOp -> this.toLocalValue(var2p, val2p, p2val)
        is BrilSubOp -> this.toLocalValue(var2p, val2p, p2val)
        is BrilDivOp -> this.toLocalValue(var2p, val2p, p2val)
        is BrilEqOp -> this.toLocalValue(var2p, val2p, p2val)
        is BrilLeOp -> this.toLocalValue(var2p, val2p, p2val)
        is BrilGeOp -> this.toLocalValue(var2p, val2p, p2val)
        is BrilLtOp -> this.toLocalValue(var2p, val2p, p2val)
        is BrilGtOp -> this.toLocalValue(var2p, val2p, p2val)
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
    p2val: Map<Int, LocalValue>,
): LocalValue? {
    if (!var2p.contains(this.argL)) {
        return null;
    }
    if (!var2p.contains(this.argR)) {
        return null;
    }
    val argLPos = var2p.get(this.argL)!!
    val argRPos = var2p.get(this.argR)!!
    val lval: LocalValue? = p2val.get(argLPos)
    val rval: LocalValue? = p2val.get(argRPos)
    if (lval is LocalConstValue && rval is LocalConstValue) {
        return LocalConstValue(
            literal = BrilPrimitiveValueInt(
                lval.literal.asInt()!! + rval.literal.asInt()!!
            )
        )
    }
    val value = LocalAddOp(
        l = argLPos,
        r = argRPos,
        dest = this.dest,
        type = this.type,
    )
    return if (val2p.contains(value)) {
        LocalIdValue(
            pos = val2p.get(value)!!
        )
    } else {
        value
    }
}

fun BrilMulOp.toLocalValue(
    var2p: LvnContext,
    val2p: Map<LocalValue, Int>,
    p2val: Map<Int, LocalValue>,
): LocalValue? {
    if (!var2p.contains(this.argL)) {
        return null;
    }
    if (!var2p.contains(this.argR)) {
        return null;
    }
    val argLPos = var2p.get(this.argL)!!
    val argRPos = var2p.get(this.argR)!!

    val lval: LocalValue? = p2val.get(argLPos)
    val rval: LocalValue? = p2val.get(argRPos)
    if (lval is LocalConstValue && rval is LocalConstValue) {
        return LocalConstValue(
            literal = BrilPrimitiveValueInt(
                lval.literal.asInt()!! * rval.literal.asInt()!!
            )
        )
    }
    val value = LocalMulOp(
        l = argLPos,
        r = argRPos,
        dest = this.dest,
        type = this.type,
    )
    return if (val2p.contains(value)) {
        LocalIdValue(
            pos = val2p.get(value)!!
        )
    } else {
        value
    }
}

fun BrilSubOp.toLocalValue(
    var2p: LvnContext,
    val2p: Map<LocalValue, Int>,
    p2val: Map<Int, LocalValue>,
): LocalValue? {
    if (!var2p.contains(this.argL)) {
        return null;
    }
    if (!var2p.contains(this.argR)) {
        return null;
    }
    val argLPos = var2p.get(this.argL)!!
    val argRPos = var2p.get(this.argR)!!

    val lval: LocalValue? = p2val.get(argLPos)
    val rval: LocalValue? = p2val.get(argRPos)
    if (lval is LocalConstValue && rval is LocalConstValue) {
        return LocalConstValue(
            literal = BrilPrimitiveValueInt(
                lval.literal.asInt()!! - rval.literal.asInt()!!
            )
        )
    }
    val value = LocalSubOp(
        l = argLPos,
        r = argRPos,
        dest = this.dest,
        type = this.type,
    )
    return if (val2p.contains(value)) {
        LocalIdValue(
            pos = val2p.get(value)!!
        )
    } else {
        value
    }
}


fun BrilDivOp.toLocalValue(
    var2p: LvnContext,
    val2p: Map<LocalValue, Int>,
    p2val: Map<Int, LocalValue>,
): LocalValue? {
    if (!var2p.contains(this.argL)) {
        return null;
    }
    if (!var2p.contains(this.argR)) {
        return null;
    }
    val argLPos = var2p.get(this.argL)!!
    val argRPos = var2p.get(this.argR)!!

    val lval: LocalValue? = p2val.get(argLPos)
    val rval: LocalValue? = p2val.get(argRPos)
    if (lval is LocalConstValue && rval is LocalConstValue && rval.literal.asInt() != 0) {
        return LocalConstValue(
            literal = BrilPrimitiveValueInt(
                lval.literal.asInt()!! / rval.literal.asInt()!!
            )
        )
    }
    val value = LocalDivOp(
        l = argLPos,
        r = argRPos,
        dest = this.dest,
        type = this.type,
    )
    return if (val2p.contains(value)) {
        LocalIdValue(
            pos = val2p.get(value)!!
        )
    } else {
        value
    }
}

fun BrilEqOp.toLocalValue(
    var2p: LvnContext,
    val2p: Map<LocalValue, Int>,
    p2val: Map<Int, LocalValue>,
): LocalValue? {
    if (!var2p.contains(this.argL)) {
        return null;
    }
    if (!var2p.contains(this.argR)) {
        return null;
    }
    val argLPos = var2p.get(this.argL)!!
    val argRPos = var2p.get(this.argR)!!

    val lval: LocalValue? = p2val.get(argLPos)
    val rval: LocalValue? = p2val.get(argRPos)
    if (lval is LocalConstValue && rval is LocalConstValue) {
        return LocalConstValue(
            literal = BrilPrimitiveValueBool(
                lval.literal.asInt()!! == rval.literal.asInt()!!
            )
        )
    }
    val value = LocalEqOp(
        l = argLPos,
        r = argRPos,
        dest = this.dest,
        type = this.type,
    )
    return if (val2p.contains(value)) {
        LocalIdValue(
            pos = val2p.get(value)!!
        )
    } else {
        value
    }
}


fun BrilLeOp.toLocalValue(
    var2p: LvnContext,
    val2p: Map<LocalValue, Int>,
    p2val: Map<Int, LocalValue>,
): LocalValue? {
    if (!var2p.contains(this.argL)) {
        return null;
    }
    if (!var2p.contains(this.argR)) {
        return null;
    }
    val argLPos = var2p.get(this.argL)!!
    val argRPos = var2p.get(this.argR)!!

    val lval: LocalValue? = p2val.get(argLPos)
    val rval: LocalValue? = p2val.get(argRPos)
    if (lval is LocalConstValue && rval is LocalConstValue) {
        return LocalConstValue(
            literal = BrilPrimitiveValueBool(
                value = lval.literal.asInt()!! <= rval.literal.asInt()!!
            )
        )
    }
    val value = LocalLeOp(
        l = argLPos,
        r = argRPos,
        dest = this.dest,
        type = this.type,
    )
    return if (val2p.contains(value)) {
        LocalIdValue(
            pos = val2p.get(value)!!
        )
    } else {
        value
    }
}


fun BrilGeOp.toLocalValue(
    var2p: LvnContext,
    val2p: Map<LocalValue, Int>,
    p2val: Map<Int, LocalValue>,
): LocalValue? {
    if (!var2p.contains(this.argL)) {
        return null;
    }
    if (!var2p.contains(this.argR)) {
        return null;
    }
    val argLPos = var2p.get(this.argL)!!
    val argRPos = var2p.get(this.argR)!!

    val lval: LocalValue? = p2val.get(argLPos)
    val rval: LocalValue? = p2val.get(argRPos)
    if (lval is LocalConstValue && rval is LocalConstValue) {
        return LocalConstValue(
            literal = BrilPrimitiveValueBool(
                value = lval.literal.asInt()!! >= rval.literal.asInt()!!
            )
        )
    }
    val value = LocalGeOp(
        l = argLPos,
        r = argRPos,
        dest = this.dest,
        type = this.type,
    )
    return if (val2p.contains(value)) {
        LocalIdValue(
            pos = val2p.get(value)!!
        )
    } else {
        value
    }
}


fun BrilLtOp.toLocalValue(
    var2p: LvnContext,
    val2p: Map<LocalValue, Int>,
    p2val: Map<Int, LocalValue>,
): LocalValue? {
    if (!var2p.contains(this.argL)) {
        return null;
    }
    if (!var2p.contains(this.argR)) {
        return null;
    }
    val argLPos = var2p.get(this.argL)!!
    val argRPos = var2p.get(this.argR)!!

    val lval: LocalValue? = p2val.get(argLPos)
    val rval: LocalValue? = p2val.get(argRPos)
    if (lval is LocalConstValue && rval is LocalConstValue) {
        return LocalConstValue(
            literal = BrilPrimitiveValueBool(
                value = lval.literal.asInt()!! < rval.literal.asInt()!!
            )
        )
    }
    val value = LocalLtOp(
        l = argLPos,
        r = argRPos,
        dest = this.dest,
        type = this.type,
    )
    return if (val2p.contains(value)) {
        LocalIdValue(
            pos = val2p.get(value)!!
        )
    } else {
        value
    }
}

fun BrilGtOp.toLocalValue(
    var2p: LvnContext,
    val2p: Map<LocalValue, Int>,
    p2val: Map<Int, LocalValue>,
): LocalValue? {
    if (!var2p.contains(this.argL)) {
        return null;
    }
    if (!var2p.contains(this.argR)) {
        return null;
    }
    val argLPos = var2p.get(this.argL)!!
    val argRPos = var2p.get(this.argR)!!

    val lval: LocalValue? = p2val.get(argLPos)
    val rval: LocalValue? = p2val.get(argRPos)
    if (lval is LocalConstValue && rval is LocalConstValue) {
        return LocalConstValue(
            literal = BrilPrimitiveValueBool(
                lval.literal.asInt()!! > rval.literal.asInt()!!
            )
        )
    }
    val value = LocalGtOp(
        l = argLPos,
        r = argRPos,
        dest = this.dest,
        type = this.type,
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
            val2p,
            p2val
        )
        if (localValue != null) {
            p2val.put(i, localValue)
            val2p.put(localValue, i)
            instr.dest()?.let { varname ->
                if (localValue is LocalIdValue) {
                    var2p.put(varname, localValue.pos)
                } else {
                    var2p.put(varname, i)
                }
                p2var.put(i, varname)
            }
        }
    }
    val res = Block()
    block.forEachIndexed { i, instr -> 
        val instr = if (!p2val.contains(i)) {
            if (instr is BrilPrintOp) {
                instr.copy(
                    args = instr.args?.map {
                        val p = var2p.get(it)
                        if (p != null) {
                            p2var.get(p)!!
                        } else {
                            it
                        }
                    }
                )
            } else {
                instr
            }
        } else {
            // println("$i ${p2val.get(i)!!}")
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
        is LocalConstValue -> BrilConstOp(
            op = "const",
            dest = instr.dest(),
            type = instr.type(),
            value = localValue.literal,
        )
        is LocalIdValue -> BrilIdOp(
            op = "id",
            dest = instr.dest()!!,
            type = instr.type()!!,
            arg = p2var.get(localValue.pos)!!
        )
        is LocalAddOp -> BrilAddOp(
            op = "add", 
            dest = instr.dest()!!, 
            type = instr.type()!!,
            argL = p2var.get(localValue.l)!!,
            argR = p2var.get(localValue.r)!!,
        )
        is LocalMulOp -> BrilMulOp(
            op = "mul", 
            dest = instr.dest()!!, 
            type = instr.type()!!,
            argL = p2var.get(localValue.l)!!,
            argR = p2var.get(localValue.r)!!,
        )
        is LocalSubOp -> BrilSubOp(
            op = "sub", 
            dest = instr.dest()!!, 
            type = instr.type()!!,
            argL = p2var.get(localValue.l)!!,
            argR = p2var.get(localValue.r)!!,
        )
        is LocalDivOp -> BrilDivOp(
            op = "div", 
            dest = instr.dest()!!, 
            type = instr.type()!!,
            argL = p2var.get(localValue.l)!!,
            argR = p2var.get(localValue.r)!!,
        )
        is LocalEqOp -> BrilEqOp(
            op = "eq", 
            dest = instr.dest()!!, 
            type = instr.type()!!,
            argL = p2var.get(localValue.l)!!,
            argR = p2var.get(localValue.r)!!,
        )
        is LocalLeOp -> BrilLeOp(
            op = "le", 
            dest = instr.dest()!!, 
            type = instr.type()!!,
            argL = p2var.get(localValue.l)!!,
            argR = p2var.get(localValue.r)!!,
        )
        is LocalGeOp -> BrilGeOp(
            op = "ge", 
            dest = instr.dest()!!, 
            type = instr.type()!!,
            argL = p2var.get(localValue.l)!!,
            argR = p2var.get(localValue.r)!!,
        )
        is LocalLtOp -> BrilLtOp(
            op = "lt", 
            dest = instr.dest()!!, 
            type = instr.type()!!,
            argL = p2var.get(localValue.l)!!,
            argR = p2var.get(localValue.r)!!,
        )
        is LocalGtOp -> BrilGtOp(
            op = "gt", 
            dest = instr.dest()!!, 
            type = instr.type()!!,
            argL = p2var.get(localValue.l)!!,
            argR = p2var.get(localValue.r)!!,
        )
    }
}

fun lvn(
    function: BrilFunction,
): BrilFunction {
    val blocks = blocks(function)
    val nblocks = blocks.map { b -> 
        lvn(b)
    }

    return function.copy(
        instrs = nblocks.flatten()
    )
}

fun dce(
    instrs: List<BrilInstr>
): List<BrilInstr> {
    if (instrs.isEmpty()) {
        return instrs
    }
    val seen = TreeSet<String>()
    val seenLabels = TreeSet<String>()
    instrs.forEach { instr -> 
        seen.addAll(instr.args())
        if (instr is BrilJmpOp) {
            seenLabels.add(instr.label)
        } else if (instr is BrilBrOp) {
            seenLabels.add(instr.labelL)
            seenLabels.add(instr.labelR)
        }
    }
    return instrs.mapNotNull { instr -> 
        val dest = instr.dest()
        if (instr is BrilLabel && !seenLabels.contains(instr.label)) {
            null
        } else if (dest == null) {
            instr
        } else if (!seen.contains(dest)) {
            null
        } else {
            instr
        }
    }
}

fun dce(
    function: BrilFunction,
): BrilFunction {
    return function.copy(
        instrs = dce(function.instrs)
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
        var prev: BrilProgram? = null
        var curr: BrilProgram = program
        while (prev != curr) {
            prev = curr
            curr = curr.copy(
                functions = curr.functions.map { brilfunction ->
                    lvn(dce(brilfunction))
                }
            )
        }
        val json = adapter.toJson(curr)
        println(json)
    }
}
