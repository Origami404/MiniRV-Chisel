package single_cycle

import Chisel._
import single_cycle.Utils.signExtend

class InstDecoder extends Module {
    val io = IO(new Bundle() {
        val inst = Input(UInt(32.W))
        val rd = Output(UInt(5.W))
        val rs1 = Output(UInt(5.W))
        val rs2 = Output(UInt(5.W))
        val imm = Output(SInt(32.W))
    })

    private val inst = io.inst;
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

object Opcodes {
    /** add, sub, xor, or, and, sll, srl, sra, slt, sltu */
    final val ARITH      = "b0110011".U
    /** addi, xori, ori, andi, slli, srli, srai, slti, sltiu */
    final val ARITH_IMM  = "b0010011".U
    /** beq, bne, blt, bge, bltu, bgeu */
    final val BRANCH     = "b1100011".U
    /** lb, lh, lw, lbu, lhu */
    final val LOAD       = "b0000011".U
    /** sb, sh, sw */
    final val STORE      = "b0100011".U
    final val LUI        = "b0110111".U
    final val AUIPC      = "b0010111".U
    final val JAL        = "b1101111".U
    final val JALR       = "b1100111".U
    /** ecall, ebreak */
    final val SYSTEM     = "b1110011".U
}


