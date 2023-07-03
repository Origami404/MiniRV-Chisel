package single_cycle

import Chisel._

class ALU extends Module {
    val io = IO(new Bundle {
        val op = Input(UInt(3.W))
        val arg_1 = Input(SInt(32.W))
        val arg_2 = Input(SInt(32.W))
        val result = Output(SInt(32.W))
        val zero = Output(Bool())
        val neg = Output(Bool())
    })

    private val op = io.op
    private val lhs = io.arg_1
    private val rhs = io.arg_2
    private val res = io.result

    when (op === ALUOps.AND) {
        res := lhs & rhs
    } .elsewhen (op === ALUOps.OR) {
        res := lhs | rhs
    } .elsewhen (op === ALUOps.XOR) {
        res := lhs ^ rhs
    } .elsewhen (op === ALUOps.SLL) {
        res := lhs << rhs(4, 0)
    } .elsewhen (op === ALUOps.SRL) {
        res := lhs >> rhs(4, 0)
    } .elsewhen (op === ALUOps.SRA) {
        res := (lhs.asUInt >> rhs(4, 0)).asSInt
    } .otherwise {
        // ADD or SUB
        val real_rhs = Mux(op === ALUOps.SUB, -rhs, rhs)
        res := lhs + real_rhs
    }

    io.zero := res === 0.S
    io.neg := res(31).asBool
}

object ALUOps {
    final val dataT = UInt(3.W)
    final val ADD: UInt = 0.U(3.W)
    final val SUB: UInt = 1.U(3.W)
    final val AND: UInt = 2.U(3.W)
    final val OR : UInt = 3.U(3.W)
    final val XOR: UInt = 4.U(3.W)
    final val SLL: UInt = 5.U(3.W)
    final val SRL: UInt = 6.U(3.W)
    final val SRA: UInt = 7.U(3.W)
}