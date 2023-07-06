package top.origami404.miniRV

import Chisel._

object DataT {
    final val Inst = UInt(32.W)
    final val Addr = UInt(32.W)
    final val RegNo = UInt(5.W)

    final val UWord = UInt(32.W)
    final val SWord = SInt(32.W)
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

object Controls {
    /** where pc_offset comes from */
    final object pc_sel {
        final val dataT = UInt(2.W)
        /** just +4 */
        final val next  = 0.U(2.W)
        /** from imm in instruction */
        final val imm   = 1.U(2.W)
        /** from alu result */
        final val alu   = 2.U(2.W)
    }
    /** where ALU lhs argument comes from */
    final object lhs_sel {
        final val dataT = UInt(2.W)
        final val rs1   = 0.U(2.W)
        final val pc    = 1.U(2.W)
        final val zero  = 2.U(2.W)
    }
    /** where ALU rhs argument comes from */
    final object rhs_sel {
        final val dataT = Bool()
        final val rs2   = true.B
        final val imm   = false.B
    }
    /** whether ALU rhs should be negative first */
    final object rhs_neg {
        final val dataT = Bool()
        final val yes   = true.B
        final val no    = false.B
    }
    /** whether ALU result should write back to RegFile */
    final object rfw_en {
        final val dataT = Bool()
        final val yes   = true.B
        final val no    = false.B
    }
    /** where RegFile write data comes from */
    final object rfw_sel {
        final val dataT         = UInt(2.W)
        final val alu_result    = 0.U(2.W)
        final val alu_neg_flag  = 1.U(2.W)
        final val memory        = 2.U(2.W)
        final val pc_next       = 3.U(2.W)
    }
    /** whether we should write memory */
    final object memw_en {
        final val dataT = Bool()
        final val yes   = true.B
        final val no    = false.B
    }
}