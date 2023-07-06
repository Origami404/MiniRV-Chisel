package top.origami404.miniRV

import Chisel._
import top.origami404.miniRV.{DataT, ALUOps}
import top.origami404.miniRV.utils.F

class ALU extends Module {
    val io = IO(new Bundle {
        val op      = Input(ALUOps.dataT)
        val arg_1   = Input(DataT.Word)
        val arg_2   = Input(DataT.Word)
        val result  = Output(DataT.Word)
        val zero    = Output(Bool())
        val neg     = Output(Bool())
    })

    private val op = io.op
    private val lhs = io.arg_1
    private val rhs = io.arg_2

    private val res = Wire(DataT.Word)
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
        res := F.tcSra(lhs, rhs(4, 0))
    } .otherwise { // ADD
        res := F.tcAdd(lhs, rhs)
    }

    io.result  := res
    io.zero    := res === 0.U
    io.neg     := res(31).asBool
}

