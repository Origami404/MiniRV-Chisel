package top.origami404.miniRV

import Chisel._

object T {
    final val Inst = UInt(32.W)
    final val Addr = UInt(32.W)
    final val RegNo = UInt(5.W)
    final val Word = UInt(32.W)
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

class EnumWithWidth(width: Int) {
    protected val w = width.W
    final def dataT = UInt(w)
}

object BRUOps extends EnumWithWidth(3) {
    final var EQ = 0.U(w)
    final var NE = 1.U(w)
    final var GE = 2.U(w)
    final var LT = 3.U(w)
    final var LTU = 4.U(w)
    final var GEU = 5.U(w)
}

object ALUOps extends EnumWithWidth(3) {
    final val ADD: UInt = 0.U(w)
    final val SUB: UInt = 1.U(w)
    final val AND: UInt = 2.U(w)
    final val OR : UInt = 3.U(w)
    final val XOR: UInt = 4.U(w)
    final val SLL: UInt = 5.U(w)
    final val SRL: UInt = 6.U(w)
    final val SRA: UInt = 7.U(w)
}

object C {
    /** where ALU lhs argument comes from */
    final object lhs_sel extends EnumWithWidth(2) {
        final val rs1   = 0.U(w)
        final val pc    = 1.U(w)
        final val zero  = 2.U(w)
    }
    /** where ALU rhs argument comes from */
    final object rhs_sel extends EnumWithWidth(2) {
        final val rs2   = 0.U(w)
        final val imm   = 1.U(w)
        final val four  = 2.U(w)
    }
    /** whether ALU rhs should be negative first */
    final object rhs_neg extends EnumWithWidth(1) {
        final val no    = 0.U(w)
        final val yes   = 1.U(w)
    }
    /** which output of ALU should be used as result of EXE */
    final object result_sel extends EnumWithWidth(2) {
        /** use ALU.result */
        final val result    = 0.U(w)
        /** use ALU.lti */
        final val lti_flag  = 1.U(w)
        /** use ALU.ltu */
        final val ltu_flag  = 2.U(w)
    }
    /** whether ALU result should write back to RegFile */
    final object rfw_en extends EnumWithWidth(1) {
        final val no    = 0.U(w)
        final val yes   = 1.U(w)
    }
    /** where RegFile write data comes from */
    final object rfw_sel extends EnumWithWidth(1) {
        final val alu_result    = 0.U(w)
        final val memory        = 1.U(w)
    }
    /** memory I/O width */
    final object mem_width_sel extends EnumWithWidth(2) {
        final val no    = 0.U(w)
        final val byte  = 1.U(w)
        final val half  = 2.U(w)
        final val word  = 3.U(w)
    }
    /** how we should extend the memory read result */
    final object memr_sel extends EnumWithWidth(1) {
        final var sign = 0.U(w)
        final var zero = 1.U(w)
    }
}
