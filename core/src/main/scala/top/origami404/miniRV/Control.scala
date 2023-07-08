package top.origami404.miniRV

import Chisel._
import top.origami404.miniRV.{T, Opcodes, ALUOps, C}
import top.origami404.miniRV.utils.F
import chisel3.internal.prefix

class CTL_PC_Bundle extends Bundle {
    val npc_base_sel = Output(C.npc_base_sel.dataT)
    val npc_offset_sel = Output(C.npc_offset_sel.dataT)
}

class CTL_EXE_Bundle extends Bundle {
    val alu_sel = Output(ALUOps.dataT)
    val lhs_sel = Output(C.lhs_sel.dataT)
    val rhs_sel = Output(C.rhs_sel.dataT)
    val rhs_neg = Output(C.rhs_neg.dataT)
}

class CTL_MEM_Bundle extends Bundle {
    val memw_en = Output(C.memw_en.dataT)
}

class CTL_WB_Bundle extends Bundle {
    val rfw_en = Output(C.rfw_en.dataT)
    val rfw_sel = Output(C.rfw_sel.dataT)
}

class Control extends Module {
    val io = IO(new Bundle {
        val inst = Input(T.Inst)
        val pc = new CTL_PC_Bundle
        val exe = new CTL_EXE_Bundle
        val mem = new CTL_MEM_Bundle
        val wb = new CTL_WB_Bundle
    })

    private val opcode = io.inst(6, 0)
    private val funct3 = io.inst(14, 12)
    private val funct7 = io.inst(31, 25)

    // ===================== PC ========================= //
    private val npc_base_sel = io.pc.npc_base_sel
    when (opcode === Opcodes.JALR) {
        npc_base_sel := C.npc_base_sel.rs1
    } .otherwise {
        npc_base_sel := C.npc_base_sel.pc
    }

    private val npc_offset_sel = io.pc.npc_offset_sel
    when (opcode === Opcodes.BRANCH || opcode === Opcodes.JAL) {
        npc_offset_sel := C.npc_offset_sel.imm
    } .elsewhen (opcode === Opcodes.JALR) {
        npc_offset_sel := C.npc_offset_sel.alu
    } .otherwise {
        npc_offset_sel := C.npc_offset_sel.next
    }

    // ===================== EXE ========================= //
    private val alu_sel = io.exe.alu_sel
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

    // lhs = Reg[rs1] / pc / 0
    private val lhs_sel = io.exe.lhs_sel
    when (opcode === Opcodes.LUI) {
        lhs_sel := C.lhs_sel.zero
    } .elsewhen (opcode === Opcodes.AUIPC) {
        lhs_sel := C.lhs_sel.pc
    } .otherwise {
        lhs_sel := C.lhs_sel.rs1
    }

    // rhs = Reg[rs2] / imm
    private val rhs_sel = io.exe.rhs_sel
    when (opcode === Opcodes.ARITH || opcode === Opcodes.BRANCH) {
        rhs_sel := C.rhs_sel.rs2
    } .otherwise {
        rhs_sel := C.rhs_sel.imm
    }

    // rhs = rhs / -rhs
    private val rhs_neg = io.exe.rhs_neg
    private val is_sub_when_arith = funct3 === 0x0.U && funct7 === 0x20.U
    private val is_sltxx = funct3 === 0x2.U || funct3 === 0x3.U
    when (opcode === Opcodes.ARITH) {
        when (is_sub_when_arith) {
            // sub
            rhs_neg := C.rhs_neg.yes
        } .elsewhen (is_sltxx) {
            // slt, sltu
            rhs_neg := C.rhs_neg.yes
        } .otherwise {
            rhs_neg := C.rhs_neg.no
        }
    } .elsewhen (opcode === Opcodes.ARITH_IMM) {
      when (is_sltxx) {
          // slti, sltiu
          rhs_neg := C.rhs_neg.yes
      } .otherwise {
          rhs_neg := C.rhs_neg.no
      }
    } .elsewhen (opcode === Opcodes.BRANCH) {
        rhs_neg := C.rhs_neg.yes
    } .otherwise {
        rhs_neg := C.rhs_neg.no
    }

    // ===================== MEM ========================= //
    private val memw_en = io.mem.memw_en
    when (opcode === Opcodes.STORE) {
        memw_en := C.memw_en.yes
    } .otherwise {
        memw_en := C.memw_en.no
    }

    // ===================== WB ========================= //
    private val rfw_en = io.wb.rfw_en
    when (opcode === Opcodes.STORE || opcode === Opcodes.SYSTEM) {
        rfw_en := C.rfw_en.no
    } .otherwise {
        rfw_en := C.rfw_en.yes
    }

    private val rfw_sel = io.wb.rfw_sel
    when (opcode === Opcodes.STORE) {
        rfw_sel := C.rfw_sel.memory
    } .elsewhen (opcode === Opcodes.ARITH || opcode === Opcodes.ARITH_IMM) {
        when (is_sltxx) {
            // slt, sltu
            rfw_sel := C.rfw_sel.alu_neg_flag
        } .otherwise {
            rfw_sel := C.rfw_sel.alu_result
        }
    } .elsewhen (opcode === Opcodes.JAL || opcode === Opcodes.JALR) {
        rfw_sel := C.rfw_sel.pc_next
    } .otherwise {
        rfw_sel := C.rfw_sel.alu_result
    }
}

/**
  * The branch predictor that will be placed in IF.
  * Currently use 2-bit predictor.
  */
class BranchPred extends Module {
    val io = IO(new Bundle {
        val pc = Input(T.Addr)
        val inst = Input(T.Inst)

        val br_pred = Output(Bool())
        val npc_offset_pred = Output(T.Addr)

        val br_real = Input(Bool())
        val need_recover = Output(Bool())
        val pc_recover = Output(T.Addr)
    })

    // inst decoder
    val opcode = io.inst(6, 0)
    val imm = F.signExtend(32, Cat(io.inst(31, 25), io.inst(11, 7), 0.U(1.W)))
    
    val need_pred = opcode === Opcodes.BRANCH
    val target_pc = io.pc + imm
    val curr_pred = Wire(Bool())
    
    // small buffer for keeping info for the next branch right after a branch
    // rc for Record
    class BrPredRecord extends Bundle {
        val valid = Output(Bool())
        val pred = Output(Bool())
        val recover_pc = Output(T.Addr)
    }
    val rc_1 = Reg(new BrPredRecord)
    val rc_2 = Reg(new BrPredRecord)

    rc_1.valid := need_pred
    rc_1.pred := curr_pred
    rc_1.recover_pc := target_pc

    val clear_slot_2 = Wire(Bool())
    rc_2.valid := !clear_slot_2 & rc_1.valid
    rc_2.pred := rc_1.pred
    rc_2.recover_pc := rc_1.recover_pc

    // 2-bit branch predictor
    val tb_a = Reg(Bool(), init = false.B)
    val tb_b = Reg(Bool(), init = false.B)

    val pred_fail = rc_2.valid & (rc_2.pred =/= io.br_real)
    tb_a := Mux(pred_fail, tb_b, tb_a)
    tb_b := Mux(pred_fail, !tb_a, tb_a)
    
    curr_pred := tb_a
    // When we prediect a br (1st), there maybe a br (2nd) right after a br (1st),
    // and we keep the 2nd br's pred result in rc_1.
    // If now we found our predition about the 1st br is wrong, we must cancal 
    // the predition for the 2nd br. 
    clear_slot_2 := pred_fail

    // fail recover
    io.need_recover := pred_fail
    io.pc_recover := rc_2.recover_pc
}
