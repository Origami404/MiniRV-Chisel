package single_cycle

import Chisel._
import single_cycle.Utils.signExtend

class InstDecoder extends Module {
    val in = IO(Input(new Bundle {
        val inst = UInt(32.W)
    }))

    val out = IO(Output(new Bundle {
        val rd = UInt(5.W)
        val rs1 = UInt(5.W)
        val rs2 = UInt(5.W)
        val imm = SInt(32.W)
    }))

    private val inst = in.inst
    out.rd := inst(11, 7)
    out.rs1 := inst(19, 15)
    out.rs2 := inst(24, 20)

    private val opcode = inst(6, 0)

    private val is_I = opcode === Opcodes.ARITH_IMM ||
        opcode === Opcodes.LOAD || opcode === Opcodes.JALR || opcode === Opcodes.SYSTEM
    private val is_S = opcode === Opcodes.STORE
    private val is_B = opcode === Opcodes.BRANCH
    private val is_U = opcode === Opcodes.LUI || opcode === Opcodes.AUIPC
    private val is_J = opcode === Opcodes.JAL
    private val is_R = opcode === Opcodes.ARITH

    when (is_I) {
        out.imm := signExtend(32, inst(31, 20))
    } .elsewhen (is_S) {
        out.imm := signExtend(32, Cat(inst(31, 25), inst(11, 7)))
    } .elsewhen (is_B) {
        out.imm := signExtend(32, Cat(inst(31, 25), inst(11, 7), 0.U(1.W)))
    } .elsewhen (is_U) {
        out.imm := Cat(inst(31, 12), 0.U(12.W)).asSInt
    } .elsewhen (is_J) {
        // 太恐怖了，加个 assert
        val bits = Cat(0.U(11.W), inst(31, 31), inst(19, 12), inst(20, 20), inst(30, 21), 0.U(1.W))
        assert(bits.getWidth == 32)
        out.imm := bits.asSInt
    } .otherwise {
        out.imm := 0.S(32.W)
    }
}




