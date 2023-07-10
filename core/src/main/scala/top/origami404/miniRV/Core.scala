package top.origami404.miniRV

import Chisel._
import chisel3.util.ValidIO
import top.origami404.miniRV.utils.M
import top.origami404.miniRV.{T, C}
import top.origami404.miniRV.Control
import top.origami404.miniRV.InstDecoder
import top.origami404.miniRV.RegFile
import top.origami404.miniRV.{InstRAMBundle, BusBundle, DebugBundle}
import top.origami404.miniRV.utils.F

class CPUCore extends Module {
    val io = IO(new Bundle {
        val inst_rom = new InstRAMBundle
        val bus = new BusBundle
    })

    private val fwd = Module(new Forwarder)
    private val hzd = Module(new Hazard)
    private val pred = Module(new BranchPred)
    private val reg = Module(new RegFile)

    private val if_ = Module(new IF)
    private val if_id = Module(new IF_ID)
    private val id = Module(new ID)
    private val id_exe = Module(new ID_EXE)
    private val exe = Module(new EXE)
    private val exe_mem = Module(new EXE_MEM)
    private val mem = Module(new MEM)
    private val mem_wb = Module(new MEM_WB)
    private val wb = Module(new WB)

    // wires to CPU outside
    this.io.inst_rom := if_.io.rom 
    this.io.bus := mem.io.bus

    // wires to Forward module
    fwd.io.rsn := id.io.rsn
    fwd.io.exe := exe.io.fwd
    fwd.io.mem := mem.io.fwd
    exe.io.fwd := fwd.io.exe

    // wires to Hazard module
    hzd.io.exe := exe.io.hzd
    hzd.io.rsn := id.io.rsn
    if_.io.pc_stall := hzd.io.stall
    if_id.io.pipe.stall := hzd.io.stall
    id_exe.io.pipe.nop := hzd.io.nop
    id_exe.io.pipe.next_nop := hzd.io.next_nop

    // wires to Branch Prediction module
    pred.io.if_ := if_.io.pred
    pred.io.exe := exe.io.pred
    if_.io.pred_pipe := pred.io.pipe

    // wires to Register File module
    id.io.reg := reg.io.r
    wb.io.reg := reg.io.w 

    // data path
    if_id.io.in := if_.io.out
    id.io.in := if_id.io.out
    id_exe.io.in := id.io.out
    exe.io.in := id_exe.io.out
    exe_mem.io.in := exe.io.out
    mem.io.in := exe_mem.io.out
    mem_wb.io.in := mem.io.out
    wb.io.in := mem_wb.io.out
}

class IF_ID_Bundle extends Bundle {
    val inst = Output(T.Inst)
    val pc = Output(T.Addr)
    val pred = new BPD_PIPE_Bundle
}

class ID_EXE_Bundle extends Bundle {
    val pc = Output(T.Addr)
    val rd = Output(T.RegNo)
    val is_load = Output(Bool())
    /** bxx, jal(r) -> true */
    val is_br_like = Output(Bool())
    val is_jalr = Output(Bool())

    val reg_rs1 = Output(T.Word)
    val reg_rs2 = Output(T.Word)
    val imm = Output(T.Word)

    val ctl_exe = new CTL_EXE_Bundle
    val ctl_mem = new CTL_MEM_Bundle
    val ctl_wb = new CTL_WB_Bundle
    val pred = new BPD_PIPE_Bundle
}

class EXE_MEM_Bundle extends Bundle {
    val rd = Output(T.RegNo)
    val is_load = Output(Bool())
    
    val result = Output(T.Word)
    val memw_data = Output(T.Word)

    val ctl_mem = new CTL_MEM_Bundle
    val ctl_wb = new CTL_WB_Bundle
}

class MEM_WB_Bundle extends Bundle {
    val rd = Output(T.RegNo)
    val result = Output(T.Word)
    val memr_data = Output(T.Word)

    val ctl_wb = new CTL_WB_Bundle
}

class IF extends Module {
    val io = IO(new Bundle {
        val rom = new InstRAMBundle
        val pc_stall = Input(Bool())
        val pred_pipe = Flipped(new BPD_PIPE_Bundle)
        val pred = new IF_BPD_Bundle
        val out = new IF_ID_Bundle
    })

    private val pc = Reg(T.Addr, init = 0.U)
    when (!io.pc_stall) {
        pc := io.pred.npc
    }

    io.rom.inst_addr := pc
    io.pred.pc := pc
    io.pred.inst := io.rom.inst

    io.out.pred := io.pred_pipe
    io.out.inst := io.rom.inst
    io.out.pc := pc
}

class IF_ID extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new IF_ID_Bundle)
        val out = new IF_ID_Bundle
        val pipe = new Bundle {
            val stall = Input(Bool())
        }
    })

    private val reg = Reg(new IF_ID_Bundle)
    when (!io.pipe.stall) {
        reg := io.in
    }
    io.out := reg
}

/** 
 * rs1/rs2 info directly from ID, available in current cycle,
 * needed by both hazard & forward module.
 */
class ID_RSN_Bundle extends Bundle {
    val rs1 = Output(T.RegNo)
    val rs2 = Output(T.RegNo)
}

class ID extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new IF_ID_Bundle)
        val reg = Flipped(new RF_Read_Bundle)
        val out = new ID_EXE_Bundle
        val rsn = new ID_RSN_Bundle
    })

    private val decoder = Module(new InstDecoder)
    decoder.io.inst := io.in.inst

    io.reg.addr_1 := decoder.io.rs1
    io.reg.addr_2 := decoder.io.rs2

    io.out.pc := io.in.pc
    io.out.reg_rs1 := io.reg.data_1
    io.out.reg_rs2 := io.reg.data_2
    io.out.imm := decoder.io.imm
    io.out.rd := decoder.io.rd

    private val ctl = Module(new Control)
    ctl.io.inst := io.in.inst
    io.out.ctl_exe := ctl.io.exe
    io.out.ctl_mem := ctl.io.mem
    io.out.ctl_wb := ctl.io.wb

    io.rsn.rs1 := decoder.io.rs1
    io.rsn.rs2 := decoder.io.rs2
}

class ID_EXE extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new ID_EXE_Bundle)
        val out = new ID_EXE_Bundle
        val pipe = new Bundle {
            val nop = Input(Bool())
            val next_nop = Input(Bool())
        }
    })

    private val reg = Reg(new ID_EXE_Bundle)

    reg.pc      := io.in.pc 
    reg.rd      := io.in.rd 
    reg.is_load := io.in.is_load
    reg.is_jalr := io.in.is_jalr
    reg.is_br_like  := io.in.is_br_like
    reg.reg_rs1     := io.in.reg_rs1
    reg.reg_rs2     := io.in.reg_rs2 
    reg.imm     := io.in.imm 
    reg.ctl_exe := io.in.ctl_exe
    when (io.pipe.next_nop) {
        reg.ctl_mem.memw_en := C.memw_en.no
        reg.ctl_wb.rfw_en := C.rfw_en.no
    } .otherwise {
        reg.ctl_mem := io.in.ctl_mem
        reg.ctl_wb := io.in.ctl_wb
    }

    io.out.pc      := reg.pc 
    io.out.rd      := reg.rd 
    io.out.is_load := reg.is_load
    io.out.is_jalr := reg.is_jalr
    io.out.is_br_like  := reg.is_br_like
    io.out.reg_rs1     := reg.reg_rs1 
    io.out.reg_rs2     := reg.reg_rs2 
    io.out.imm     := reg.imm 
    io.out.ctl_exe := reg.ctl_exe
    when (io.pipe.nop) {
        io.out.ctl_mem.memw_en := C.memw_en.no
        io.out.ctl_wb.rfw_en := C.rfw_en.no
    } .otherwise {
        io.out.ctl_mem := reg.ctl_mem
        io.out.ctl_wb := reg.ctl_wb
    }
}

class EXE extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new ID_EXE_Bundle)
        val fwd = Flipped(new FWD_EXE_Bundle)
        val out = new EXE_MEM_Bundle
        val pred = new EXE_BPD_Bundle
        val hzd = new EXE_HZD_Bundle
    })

    // forward select
    private val reg_rs1 =
        Mux(io.fwd.reg_rs1.valid, io.fwd.reg_rs1.bits, io.in.reg_rs1)
    private val reg_rs2 =
        Mux(io.fwd.reg_rs2.valid, io.fwd.reg_rs2.bits, io.in.reg_rs2)
    
    // lhs/rhs/-rhs select
    private val lhs = Wire(T.Word)
    M.mux(lhs, 0.U, io.in.ctl_exe.lhs_sel, 
        C.lhs_sel.pc -> io.in.pc,
        C.lhs_sel.rs1 -> reg_rs1,
        C.lhs_sel.zero -> 0.U
    )
    
    private val rhs_raw = Wire(T.Word)
    M.mux(rhs_raw, 0.U, io.in.ctl_exe.rhs_sel,
        C.rhs_sel.imm -> io.in.imm,
        C.rhs_sel.rs2 -> reg_rs2,
        C.rhs_sel.four -> 4.U
    )
    
    private val rhs = Wire(T.Word)
    M.mux(rhs, 0.U, io.in.ctl_exe.rhs_neg,
        C.rhs_neg.no -> rhs_raw,
        C.rhs_neg.yes -> F.tcNeg(rhs_raw)
    )
    
    // alu
    private val alu = Module(new ALU)
    alu.io.op := io.in.ctl_exe.alu_sel
    alu.io.arg_1 := lhs
    alu.io.arg_2 := rhs

    private val result = Wire(T.Word)
    M.mux(result, 0.U, io.in.ctl_exe.result_sel,
        C.result_sel.result -> alu.io.result,
        C.result_sel.neg_flag -> Cat(0.U(T.Word.getWidth - 1), alu.io.neg)
    )

    // output for data path
    io.out.is_load := io.in.is_load
    io.out.rd := io.in.rd
    io.out.result := result
    io.out.memw_data := io.in.reg_rs2
    io.out.ctl_wb := io.in.ctl_wb
    io.out.ctl_mem := io.in.ctl_mem

    // bru
    private val bru = Module(new BRU)
    bru.io.op := io.in.ctl_exe.bru_sel
    bru.io.zero := alu.io.zero
    bru.io.neg := alu.io.neg
    private val br_fail =
        io.in.is_br_like & (io.in.pred.br_pred =/= bru.io.should_br)

    // output for branch prediction
    io.pred.br_fail := br_fail
    io.pred.real_npc_offset := io.in.imm
    io.pred.real_npc_base := Mux(io.in.is_jalr, io.in.reg_rs1, io.in.pc)

    // output for hazard
    io.hzd.br_fail := br_fail
    io.hzd.is_load := io.in.is_load
    io.hzd.rd := io.in.rd
}

class EXE_MEM extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new EXE_MEM_Bundle)
        val out = new EXE_MEM_Bundle
    })

    private val reg = Reg(new EXE_MEM_Bundle)
    reg := io.in
    io.out := reg
}

class MEM extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new EXE_MEM_Bundle)
        val bus = new BusBundle
        val out = new MEM_WB_Bundle
        val fwd = new MEM_FWD_Bundle
    })

    io.bus.wen := io.in.ctl_mem.memw_en
    io.bus.addr := io.in.result
    io.bus.wdata := io.in.memw_data

    io.out.rd := io.in.rd
    io.out.result := io.in.result
    io.out.memr_data := io.bus.rdata
    io.out.ctl_wb := io.in.ctl_wb

    io.fwd.is_load := io.in.is_load
    io.fwd.alu_result := io.in.result
    io.fwd.rd := io.in.rd
    io.fwd.memr_data := io.bus.rdata
}

class MEM_WB extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new MEM_WB_Bundle)
        val out = new MEM_WB_Bundle
    })

    private val reg = Reg(new MEM_WB_Bundle)
    reg := io.in
    io.out := reg
}

class WB extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new MEM_WB_Bundle)
        val reg = Flipped(new RF_Write_Bundle)
    })

    io.reg.enable := io.in.ctl_wb.rfw_en
    io.reg.addr := io.in.rd
    M.mux(io.reg.data, 0.U, io.in.ctl_wb.rfw_sel,
        C.rfw_sel.alu_result -> io.in.result,
        C.rfw_sel.memory -> io.in.memr_data
    )
}