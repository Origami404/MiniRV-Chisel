package single_cycle

import Chisel._

class ALU extends Module {
    val in = IO(Input(new Bundle {
        val op = ALUOps.dataT
        val arg_1 = DataT.SWord
        val arg_2 = DataT.SWord
    }))

    val out = IO(Output(new Bundle {
        val result = DataT.SWord
        val zero = Bool()
        val neg = Bool()
    }))

    private val op = in.op
    private val lhs = in.arg_1
    private val rhs = in.arg_2

    private val res = Wire(DataT.SWord)
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
    } .otherwise { // ADD
        res := lhs + rhs
    }

    out.result  := res
    out.zero    := res === 0.S
    out.neg     := res(31).asBool
}

