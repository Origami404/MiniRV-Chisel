package top.origami404.miniRV

import Chisel._
import top.origami404.miniRV.utils.F.signExtend
import top.origami404.miniRV.{DataT, Opcodes}
import top.origami404.miniRV.utils.F

class InstDecoder extends Module {
    val io = IO(new Bundle {
        val inst    = Input(DataT.Inst)
        val rd      = Output(DataT.RegNo)
        val rs1     = Output(DataT.RegNo)
        val rs2     = Output(DataT.RegNo)
        val imm     = Output(DataT.SWord)
    })

    private val inst = io.inst
    io.rd := inst(11, 7)
    io.rs1 := inst(19, 15)
    io.rs2 := inst(24, 20)

    private val opcode = inst(6, 0)

    private val is_I = opcode === Opcodes.ARITH_IMM ||
        opcode === Opcodes.LOAD || opcode === Opcodes.JALR || opcode === Opcodes.SYSTEM
    private val is_S = opcode === Opcodes.STORE
    private val is_B = opcode === Opcodes.BRANCH
    private val is_U = opcode === Opcodes.LUI || opcode === Opcodes.AUIPC
    private val is_J = opcode === Opcodes.JAL
    private val is_R = opcode === Opcodes.ARITH

    when (is_I) {
        io.imm := signExtend(32, inst(31, 20))
    } .elsewhen (is_S) {
        io.imm := signExtend(32, Cat(inst(31, 25), inst(11, 7)))
    } .elsewhen (is_B) {
        io.imm := signExtend(32, Cat(inst(31, 25), inst(11, 7), 0.U(1.W)))
    } .elsewhen (is_U) {
        io.imm := Cat(inst(31, 12), 0.U(12.W)).asSInt
    } .elsewhen (is_J) {
        // 太恐怖了，加个 assert
        val bits = Cat(0.U(11.W), inst(31, 31), inst(19, 12), inst(20, 20), inst(30, 21), 0.U(1.W))
        assert(bits.getWidth == 32)
        io.imm := bits.asSInt
    } .otherwise {
        io.imm := 0.S(32.W)
    }
}




