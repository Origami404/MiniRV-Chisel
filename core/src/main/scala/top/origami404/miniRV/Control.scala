package top.origami404.miniRV

import Chisel._
import top.origami404.miniRV.{DataT, Opcodes, ALUOps, Controls}

class Control extends Module {
    val io = IO(new Bundle {
        val inst = Input(DataT.Inst)
        val alu_sel = Output(ALUOps.dataT)
        val pc_sel = Output(Controls.pc_sel.dataT)
        val lhs_sel = Output(Controls.lhs_sel.dataT)
        val rhs_sel = Output(Controls.rhs_sel.dataT)
        val rhs_neg = Output(Controls.rhs_neg.dataT)
        val rfw_en = Output(Controls.rfw_en.dataT)
        val rfw_sel = Output(Controls.rfw_sel.dataT)
        val memw_en = Output(Controls.memw_en.dataT)
    })

    private val opcode = io.inst(6, 0)
    private val funct3 = io.inst(14, 12)
    private val funct7 = io.inst(31, 25)

    private val alu_sel = io.alu_sel
    when (opcode === Opcodes.ARITH || opcode === Opcodes.ARITH_IMM) {
        when (funct3 === 0x0.U) {
            // add(i), sub(i)
            alu_sel := ALUOps.ADD
        } .elsewhen (funct3 === 0x4.U) {
            // xor(i)
            alu_sel := ALUOps.XOR
        } .elsewhen (funct3 === 0x6.U) {
            // or(i)
            alu_sel := ALUOps.OR
        } .elsewhen (funct3 === 0x7.U) {
            // and(i)
            alu_sel := ALUOps.AND
        } .elsewhen (funct3 === 0x1.U) {
            // sll(i)
            alu_sel := ALUOps.SLL
        } .elsewhen (funct3 === 0x5.U) {
            // srl(i), sra(i)
            when (funct7(5) === 0.U) {
                alu_sel := ALUOps.SRL
            } .otherwise {
                alu_sel := ALUOps.SRA
            }
        } .otherwise {
            // TODO: slt(i), sltu(i)
            alu_sel := ALUOps.ADD
        }
    } .otherwise {
        // lx, sx : rs1 + imm
        // jalr : rs1 + imm
        // bxx : rs1 - rs2
        // jal: pc + imm
        // auipc: pc + (imm << 12)
        // lui: 0 + (imm << 12)
        alu_sel := ALUOps.ADD
    }

    private val pc_sel = io.pc_sel
    when (opcode === Opcodes.BRANCH || opcode === Opcodes.JAL) {
        pc_sel := Controls.pc_sel.imm
    } .elsewhen (opcode === Opcodes.JALR) {
        pc_sel := Controls.pc_sel.alu
    } .otherwise {
        pc_sel := Controls.pc_sel.next
    }

    // lhs = Reg[rs1] / pc / 0
    private val lhs_sel = io.lhs_sel
    when (opcode === Opcodes.LUI) {
        lhs_sel := Controls.lhs_sel.zero
    } .elsewhen (opcode === Opcodes.AUIPC) {
        lhs_sel := Controls.lhs_sel.pc
    } .otherwise {
        lhs_sel := Controls.lhs_sel.rs1
    }

    // rhs = Reg[rs2] / imm
    private val rhs_sel = io.rhs_sel
    when (opcode === Opcodes.ARITH || opcode === Opcodes.BRANCH) {
        rhs_sel := Controls.rhs_sel.rs2
    } .otherwise {
        rhs_sel := Controls.rhs_sel.imm
    }

    // rhs = rhs / -rhs
    private val rhs_neg = io.rhs_neg
    private val is_sub_when_arith = funct3 === 0x0.U && funct7 === 0x20.U
    private val is_sltxx = funct3 === 0x2.U || funct3 === 0x3.U
    when (opcode === Opcodes.ARITH) {
        when (is_sub_when_arith) {
            // sub
            rhs_neg := Controls.rhs_neg.yes
        } .elsewhen (is_sltxx) {
            // slt, sltu
            rhs_neg := Controls.rhs_neg.yes
        } .otherwise {
            rhs_neg := Controls.rhs_neg.no
        }
    } .elsewhen (opcode === Opcodes.ARITH_IMM) {
      when (is_sltxx) {
          // slti, sltiu
          rhs_neg := Controls.rhs_neg.yes
      } .otherwise {
          rhs_neg := Controls.rhs_neg.no
      }
    } .elsewhen (opcode === Opcodes.BRANCH) {
        rhs_neg := Controls.rhs_neg.yes
    } .otherwise {
        rhs_neg := Controls.rhs_neg.no
    }

    private val rfw_en = io.rfw_en
    when (opcode === Opcodes.STORE || opcode === Opcodes.SYSTEM) {
        rfw_en := Controls.rfw_en.no
    } .otherwise {
        rfw_en := Controls.rfw_en.yes
    }

    private val rfw_sel = io.rfw_sel
    when (opcode === Opcodes.STORE) {
        rfw_sel := Controls.rfw_sel.memory
    } .elsewhen (opcode === Opcodes.ARITH || opcode === Opcodes.ARITH_IMM) {
        when (is_sltxx) {
            // slt, sltu
            rfw_sel := Controls.rfw_sel.alu_neg_flag
        } .otherwise {
            rfw_sel := Controls.rfw_sel.alu_result
        }
    } .elsewhen (opcode === Opcodes.JAL || opcode === Opcodes.JALR) {
        rfw_sel := Controls.rfw_sel.pc_next
    } .otherwise {
        rfw_sel := Controls.rfw_sel.alu_result
    }

    private val memw_en = io.memw_en
    when (opcode === Opcodes.STORE) {
        memw_en := Controls.memw_en.yes
    } .otherwise {
        memw_en := Controls.memw_en.no
    }
}
