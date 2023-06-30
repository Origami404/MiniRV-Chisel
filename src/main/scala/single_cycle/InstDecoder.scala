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
    // (addi, xori, ori, andi, slli, srli, srai, slti, sltiu),
    // (lb, lh, lw, lbu, lhu), (jalr), (ecall, ebreak)
    private val is_I = opcode === "b0010011".U ||
        opcode === "b0000011".U || opcode === "b1100111".U || opcode === "b1110011".U
    private val is_S = opcode === "b0100011".U  // sb, sh, sw
    private val is_B = opcode === "b1100011".U  // beq, bne, blt, bge, bltu, bgeu
    private val is_U = opcode === "b0110111".U  // lui, auipc
    private val is_J = opcode === "b1101111".U  // jal
    private val is_R = opcode === "b0110011".U  // add, sub, xor, or, and, sll, srl, sra, slt, sltu

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


