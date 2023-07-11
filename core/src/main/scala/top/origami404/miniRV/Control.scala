package top.origami404.miniRV

import Chisel._
import chisel3.util.ValidIO
import top.origami404.miniRV.utils.{F, M}

class CTL_EXE_Bundle extends Bundle {
    val alu_sel = Output(ALUOps.dataT)
    val bru_sel = Output(BRUOps.dataT)
    val lhs_sel = Output(C.lhs_sel.dataT)
    val rhs_sel = Output(C.rhs_sel.dataT)
    val rhs_neg = Output(C.rhs_neg.dataT)
    val result_sel = Output(C.result_sel.dataT)
}

class CTL_MEM_Bundle extends Bundle {
    val mem_width_sel = Output(C.mem_width_sel.dataT)
    val memr_sel = Output(C.memr_sel.dataT)
}

class CTL_WB_Bundle extends Bundle {
    val rfw_en = Output(C.rfw_en.dataT)
    val rfw_sel = Output(C.rfw_sel.dataT)
}

class Control extends Module {
    val io = IO(new Bundle {
        val inst = Input(T.Inst)
        val exe = new CTL_EXE_Bundle
        val mem = new CTL_MEM_Bundle
        val wb = new CTL_WB_Bundle
    })

    private val opcode = io.inst(6, 0)
    private val funct3 = io.inst(14, 12)
    private val funct7 = io.inst(31, 25)

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

    private val bru_sel = io.exe.bru_sel
    bru_sel := MuxLookup(funct3, 0.U, Seq(
        0x0.U -> BRUOps.EQ,
        0x1.U -> BRUOps.NE,
        0x4.U -> BRUOps.LT,
        0x5.U -> BRUOps.GE,
        0x6.U -> BRUOps.LTU,
        0x7.U -> BRUOps.GEU
    ))

    // lhs = Reg[rs1] / pc / 0
    private val lhs_sel = io.exe.lhs_sel
    when (opcode === Opcodes.LUI) {
        lhs_sel := C.lhs_sel.zero
    } .elsewhen (opcode === Opcodes.AUIPC || opcode === Opcodes.JAL || opcode === Opcodes.JALR) {
        lhs_sel := C.lhs_sel.pc
    } .otherwise {
        lhs_sel := C.lhs_sel.rs1
    }

    // rhs = Reg[rs2] / imm
    private val rhs_sel = io.exe.rhs_sel
    when (opcode === Opcodes.ARITH || opcode === Opcodes.BRANCH) {
        rhs_sel := C.rhs_sel.rs2
    } .elsewhen (opcode === Opcodes.JAL || opcode === Opcodes.JALR) {
        rhs_sel := C.rhs_sel.four
    } .otherwise {
        rhs_sel := C.rhs_sel.imm
    }

    // rhs = rhs / -rhs
    private val rhs_neg = io.exe.rhs_neg
    private val is_sub = opcode === Opcodes.ARITH & funct3 === 0x0.U && funct7 === 0x20.U
    when (is_sub) {
        rhs_neg := C.rhs_neg.yes
    } .otherwise {
        rhs_neg := C.rhs_neg.no
    }

    private val result_sel = io.exe.result_sel
    when (opcode === Opcodes.ARITH | opcode === Opcodes.ARITH_IMM) {
        when (funct3 === 0x2.U) {
            result_sel := C.result_sel.lti_flag
        } .elsewhen (funct3 === 0x3.U) {
            result_sel := C.result_sel.ltu_flag
        } .otherwise {
            result_sel := C.result_sel.result
        }
    } .otherwise {
        result_sel := C.result_sel.result
    }

    // ===================== MEM ========================= //
    private val mem_width_sel = io.mem.mem_width_sel
    when (opcode === Opcodes.STORE || opcode === Opcodes.LOAD) {
        when (funct3 === 0x0.U || funct3 === 0x4.U) {
            mem_width_sel := C.mem_width_sel.byte
        } .elsewhen (funct3 === 0x1.U || funct3 === 0x5.U) {
            mem_width_sel := C.mem_width_sel.half
        } .elsewhen (funct3 === 0x2.U) {
            mem_width_sel := C.mem_width_sel.word
        } .otherwise {
            mem_width_sel := C.mem_width_sel.no
        }
    } .otherwise {
        mem_width_sel := C.mem_width_sel.no
    }

    private val memr_sel = io.mem.memr_sel
    when (opcode === Opcodes.LOAD) {
        when (funct3 === 0x4.U || funct3 === 0x5.U) {
            memr_sel := C.memr_sel.zero
        } .otherwise {
            memr_sel := C.memr_sel.sign
        }
    } .otherwise {
        memr_sel := C.memr_sel.sign
    }

    // ===================== WB ========================= //
    private val rfw_en = io.wb.rfw_en
    when (opcode === Opcodes.STORE || opcode === Opcodes.BRANCH || opcode === Opcodes.SYSTEM) {
        rfw_en := C.rfw_en.no
    } .otherwise {
        rfw_en := C.rfw_en.yes
    }

    private val rfw_sel = io.wb.rfw_sel
    when (opcode === Opcodes.LOAD) {
        rfw_sel := C.rfw_sel.memory
    } .otherwise {
        rfw_sel := C.rfw_sel.alu_result
    }
}

class IF_BPD_Bundle extends Bundle {
    val inst = Output(T.Inst)
    val pc = Output(T.Inst)
    val npc = Input(T.Inst)
}

class EXE_BPD_Bundle extends Bundle {
    val br_fail = Output(Bool())
    val real_npc_base = Output(T.Addr)
    val real_npc_offset = Output(T.Addr)
}

class BPD_PIPE_Bundle extends Bundle {
    val br_pred = Output(Bool())
}

class BranchPred extends Module {
    val io = IO(new Bundle {
        val if_ = Flipped(new IF_BPD_Bundle)
        val exe = Flipped(new EXE_BPD_Bundle)
        val pipe = new BPD_PIPE_Bundle
    })

    private object TwoBitPredictor extends Module {
        val io = IO(new Bundle {
            val pred_fail = Input(Bool())
            val pred = Output(Bool())
        })

        private val a = Reg(Bool())
        private val b = Reg(Bool())
        a := Mux(io.pred_fail, b, a)
        b := Mux(io.pred_fail, !a, a)

        io.pred := a
    }

    private val predictor = Module(TwoBitPredictor)
    predictor.io.pred_fail := io.exe.br_fail

    // parse inst, get pred & imm
    val inst = io.if_.inst
    val opcode = inst(6, 0)

    val imm = Wire(T.Addr)
    when (opcode === Opcodes.JAL) {
        val bits = Cat(inst(31, 31), inst(19, 12), inst(20, 20), inst(30, 21), 0.U(1.W))
        imm := F.signExtend(imm.getWidth, bits)
    } .elsewhen (opcode === Opcodes.BRANCH) {
        imm := F.signExtend(imm.getWidth, Cat(inst(31, 25), inst(11, 7), 0.U(1.W)))
    } .otherwise {
        imm := 0.U
    }

    // predict with respect to opcode
    private val pred_npc_base = io.if_.pc
    private val pred_will_branch =
        opcode === Opcodes.JAL | (opcode === Opcodes.BRANCH & predictor.io.pred)
    private val pred_npc_offset = Mux(pred_will_branch, imm, 4.U)
    io.pipe.br_pred := pred_will_branch

    // select output with respect to EXE failure
    private val npc_base = Mux(io.exe.br_fail, io.exe.real_npc_base, pred_npc_base)
    private val npc_offset = Mux(io.exe.br_fail, io.exe.real_npc_offset, pred_npc_offset)
    io.if_.npc := npc_base + npc_offset
}

class MEM_FWD_Bundle extends Bundle {
    val is_load = Output(Bool())
    val rd = Output(T.RegNo)
    val alu_result = Output(T.Word)
    val memr_data = Output(T.Word)
    val ctl_wb = Output(new CTL_WB_Bundle)
    val have_inst = Output(Bool())
}

class EXE_FWD_Bundle extends Bundle {
    val rd = Output(T.RegNo)
    val alu_result = Output(T.Word)
    val ctl_wb = Output(new CTL_WB_Bundle)
    val have_inst = Output(Bool())
}

class FWD_ID_Bundle extends Bundle {
    val reg_rs1 = Output(ValidIO(T.Word))
    val reg_rs2 = Output(ValidIO(T.Word))
}

class Forwarder extends Module {
    val io = IO(new Bundle {
        val rsn = Flipped(new ID_RSN_Bundle)
        val exe = Flipped(new EXE_FWD_Bundle)
        val mem = Flipped(new MEM_FWD_Bundle)
        val out = new FWD_ID_Bundle
    })

    // port alias
    private val rs1 = io.rsn.rs1
    private val rs2 = io.rsn.rs2

    private val exe_rd = io.exe.rd
    private val mem_rd = io.mem.rd
    private val mem_is_load = io.mem.is_load
    
    private val exe_alu = io.exe.alu_result
    private val mem_alu = io.mem.alu_result
    private val mem_read = io.mem.memr_data

    private val fwd_1 = io.out.reg_rs1
    private val fwd_2 = io.out.reg_rs2

    // logic
    private val fwd_exe_1 = 
        io.exe.have_inst &
        exe_rd =/= 0.U & exe_rd === rs1 &
        io.exe.ctl_wb.rfw_en === C.rfw_en.yes & 
        io.exe.ctl_wb.rfw_sel === C.rfw_sel.alu_result
    private val fwd_mem_1 = 
        io.mem.have_inst &
        mem_rd =/= 0.U & mem_rd === rs1 &
        io.mem.ctl_wb.rfw_en === C.rfw_en.yes

    when (fwd_exe_1) {
        fwd_1.valid := true.B
        fwd_1.bits := exe_alu
    } .elsewhen (fwd_mem_1) {
        fwd_1.valid := true.B
        fwd_1.bits := Mux(mem_is_load, mem_read, mem_alu)
    } .otherwise {
        fwd_1.valid := false.B
        fwd_1.bits := 0.U
    }


    private val fwd_exe_2 = 
        io.exe.have_inst &
        exe_rd =/= 0.U & exe_rd === rs2 &
        io.exe.ctl_wb.rfw_en === C.rfw_en.yes & 
        io.exe.ctl_wb.rfw_sel === C.rfw_sel.alu_result
    private val fwd_mem_2 =
        io.mem.have_inst &
        mem_rd =/= 0.U & mem_rd === rs2 &
        io.mem.ctl_wb.rfw_en === C.rfw_en.yes
        
    when (fwd_exe_2) {
        fwd_2.valid := true.B
        fwd_2.bits := exe_alu
    } .elsewhen (fwd_mem_2) {
        fwd_2.valid := true.B
        fwd_2.bits := Mux(mem_is_load, mem_read, mem_alu)
    } .otherwise {
        fwd_2.valid := false.B
        fwd_2.bits := 0.U
    }
}

class EXE_HZD_Bundle extends Bundle {
    val br_fail = Output(Bool())
    val is_load = Output(Bool())
    val rd = Output(Bool())
}

class Hazard extends Module {
    val io = IO(new Bundle {
        val exe = Flipped(new EXE_HZD_Bundle)
        val rsn = Flipped(new ID_RSN_Bundle)
        val stall = Output(Bool())
        val exe_nop = Output(Bool())
        val id_nop = Output(Bool())
    })

    private val stall = io.exe.is_load & (io.exe.rd === io.rsn.rs1 | io.exe.rd === io.rsn.rs2)
    private val exe_nop = stall | io.exe.br_fail
    private val id_nop = io.exe.br_fail

    io.stall := stall
    io.exe_nop := exe_nop
    io.id_nop := id_nop
}