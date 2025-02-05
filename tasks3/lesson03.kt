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

data class LocalBinOp(
    val op: String,
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
        is BrilConstOp -> LocalConstValue(this.value)
        is BrilIdOp -> this.toLocalValue(var2p)
        is BrilAddOp -> this.toLocalValue(var2p, val2p)
        is BrilMulOp -> this.toLocalValue(var2p, val2p)
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
    val value = LocalBinOp(
        op = this.op,
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

fun BrilMulOp.toLocalValue(
    var2p: LvnContext,
    val2p: Map<LocalValue, Int>,
): LocalValue? {
    if (!var2p.contains(this.argL)) {
        return null;
    }
    if (!var2p.contains(this.argR)) {
        return null;
    }
    val value = LocalBinOp(
        op = this.op,
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
        is LocalBinOp -> BrilAddOp(
            op = this.op, 
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
    function: BrilFunction,
): BrilFunction {
    return function
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
        val nprogram = program.copy(
            functions = program.functions.map(::lvn),
        )
        val json = adapter.toJson(nprogram)
        println(json)
    }
}
