package top.origami404.miniRV

import Chisel._
import chisel3.util.ValidIO
import top.origami404.miniRV.utils.M
import top.origami404.miniRV.{T, C}
import top.origami404.miniRV.Control
import top.origami404.miniRV.InstDecoder
import top.origami404.miniRV.RegFile
import top.origami404.miniRV.utils.F

class InstRAMBundle extends Bundle {
  val inst_addr   = Output(UInt(32.W))
  val inst        = Input(UInt(32.W))
}

class BusBundle extends Bundle {
  val addr    = Output(UInt(32.W))
  val rdata   = Input(UInt(32.W))
  val wen     = Output(Bool())
  val wdata   = Output(UInt(32.W))
}

class DebugBundle extends Bundle {
  val wb_have_inst   = Output(Bool())
  val wb_pc          = Output(UInt(32.W))
  val wb_ena         = Output(Bool())
  val wb_reg         = Output(UInt(5.W))
  val wb_value       = Output(UInt(32.W))
}

class CPUCore extends Module {
    val io = IO(new Bundle {
        val inst_rom = new InstRAMBundle
        val bus = new BusBundle
        val debug = new DebugBundle
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

    // wires for debug
    this.io.debug.wb_have_inst := wb.io.debug.have_inst
    this.io.debug.wb_pc := wb.io.debug.pc
    this.io.debug.wb_ena := wb.io.reg.enable
    this.io.debug.wb_reg := wb.io.reg.addr
    this.io.debug.wb_value := wb.io.reg.data

    // wires to CPU outside
    this.io.inst_rom := if_.io.rom 
    this.io.bus := mem.io.bus

    // wires to Forward module
    fwd.io.rsn := id.io.rsn
    fwd.io.exe := exe.io.fwd
    fwd.io.mem := mem.io.fwd
    id.io.fwd := fwd.io.out

    // wires to Hazard module
    hzd.io.exe := exe.io.hzd
    hzd.io.rsn := id.io.rsn
    if_.io.pc_stall := hzd.io.stall
    if_id.io.pipe.stall := hzd.io.stall
    if_id.io.pipe.nop := hzd.io.id_nop
    id_exe.io.pipe.nop := hzd.io.exe_nop

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

class PipelineDebugBundle extends Bundle {
    val have_inst = Output(Bool())
    val pc = Output(T.Addr)
}

class IF_ID_Bundle extends Bundle {
    val inst = Output(T.Inst)
    val pc = Output(T.Addr)
    val pred = new BPD_PIPE_Bundle

    val debug = new PipelineDebugBundle
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

    val debug = new PipelineDebugBundle
}

class EXE_MEM_Bundle extends Bundle {
    val pc = Output(T.Addr)     // used for debug
    val rd = Output(T.RegNo)
    val is_load = Output(Bool())
    
    val result = Output(T.Word)
    val memw_data = Output(T.Word)

    val ctl_mem = new CTL_MEM_Bundle
    val ctl_wb = new CTL_WB_Bundle

    val debug = new PipelineDebugBundle
}

class MEM_WB_Bundle extends Bundle {
    val rd = Output(T.RegNo)
    val result = Output(T.Word)
    val memr_data = Output(T.Word)

    val ctl_wb = new CTL_WB_Bundle

    val debug = new PipelineDebugBundle
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

    io.out.debug.have_inst := true.B
    io.out.debug.pc := pc
}

class IF_ID extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new IF_ID_Bundle)
        val out = new IF_ID_Bundle
        val pipe = new Bundle {
            val nop = Input(Bool())
            val stall = Input(Bool())
        }
    })

    private val reg = RegInit(0.U.asTypeOf(new IF_ID_Bundle))
    when (!io.pipe.stall) {
        reg := io.in
    }
    when (io.pipe.nop) {
        reg.debug.have_inst := false.B
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
        val fwd = Flipped(new FWD_ID_Bundle)
        val reg = Flipped(new RF_Read_Bundle)
        val out = new ID_EXE_Bundle
        val rsn = new ID_RSN_Bundle
    })

    private val decoder = Module(new InstDecoder)
    decoder.io.inst := io.in.inst

    io.reg.addr_1 := decoder.io.rs1
    io.reg.addr_2 := decoder.io.rs2

    private val reg_rs1 =
        Mux(io.fwd.reg_rs1.valid, io.fwd.reg_rs1.bits, io.reg.data_1)
    private val reg_rs2 =
        Mux(io.fwd.reg_rs2.valid, io.fwd.reg_rs2.bits, io.reg.data_2)

    io.out.pc := io.in.pc
    io.out.reg_rs1 := reg_rs1
    io.out.reg_rs2 := reg_rs2
    io.out.imm := decoder.io.imm
    io.out.rd := decoder.io.rd
    io.out.debug := io.in.debug

    io.out.is_load := decoder.io.is_load
    io.out.is_br_like := decoder.io.is_br_like
    io.out.is_jalr := decoder.io.is_jalr

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
        }
    })

    private val reg = RegInit(0.U.asTypeOf(new ID_EXE_Bundle))

    reg := io.in
    when (io.pipe.nop) {
        reg.debug.have_inst := false.B
    }

    io.out := reg
}

class EXE extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new ID_EXE_Bundle)
        val out = new EXE_MEM_Bundle
        val pred = new EXE_BPD_Bundle
        val hzd = new EXE_HZD_Bundle
        val fwd = new EXE_FWD_Bundle
    })

    // lhs/rhs/-rhs select
    private val lhs = Wire(T.Word)
    M.mux(lhs, 0.U, io.in.ctl_exe.lhs_sel, 
        C.lhs_sel.pc -> io.in.pc,
        C.lhs_sel.rs1 -> io.in.reg_rs1,
        C.lhs_sel.zero -> 0.U
    )
    
    private val rhs_raw = Wire(T.Word)
    M.mux(rhs_raw, 0.U, io.in.ctl_exe.rhs_sel,
        C.rhs_sel.imm -> io.in.imm,
        C.rhs_sel.rs2 -> io.in.reg_rs2,
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
        C.result_sel.lti_flag -> Cat(0.U(T.Word.getWidth - 1), alu.io.lti.asUInt),
        C.result_sel.ltu_flag -> Cat(0.U(T.Word.getWidth - 1), alu.io.ltu.asUInt)
    )

    // output for data path
    io.out.is_load := io.in.is_load
    io.out.rd := io.in.rd
    io.out.result := result
    io.out.memw_data := io.in.reg_rs2
    io.out.ctl_wb := io.in.ctl_wb
    io.out.ctl_mem := io.in.ctl_mem
    io.out.debug := io.in.debug

    // bru
    private val bru = Module(new BRU)
    bru.io.op := io.in.ctl_exe.bru_sel
    bru.io.eq := alu.io.eq
    bru.io.lti := alu.io.lti
    bru.io.ltu := alu.io.ltu
    private val br_fail =
        io.in.debug.have_inst &
        io.in.is_br_like & ((io.in.pred.br_pred =/= bru.io.should_br) || io.in.is_jalr)

    // output for branch prediction
    io.pred.br_fail := br_fail
    io.pred.real_npc_offset := Mux(bru.io.should_br | io.in.is_jalr, io.in.imm, 4.U)
    io.pred.real_npc_base := Mux(io.in.is_jalr, io.in.reg_rs1, io.in.pc)

    // output for hazard
    io.hzd.br_fail := br_fail
    io.hzd.is_load := io.in.is_load
    io.hzd.rd := io.in.rd

    // output for forwarding
    io.fwd.alu_result := result
    io.fwd.rd := io.in.rd
    io.fwd.ctl_wb := io.in.ctl_wb
    io.fwd.have_inst := io.in.debug.have_inst
}

class EXE_MEM extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new EXE_MEM_Bundle)
        val out = new EXE_MEM_Bundle
    })

    private val reg = RegInit(0.U.asTypeOf(new EXE_MEM_Bundle))
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

    io.bus.wen := io.in.debug.have_inst & io.in.ctl_mem.memw_en
    io.bus.addr := io.in.result
    io.bus.wdata := io.in.memw_data

    io.out.rd := io.in.rd
    io.out.result := io.in.result
    io.out.memr_data := io.bus.rdata
    io.out.ctl_wb := io.in.ctl_wb
    io.out.debug := io.in.debug

    io.fwd.is_load := io.in.debug.have_inst & io.in.is_load
    io.fwd.alu_result := io.in.result
    io.fwd.rd := io.in.rd
    io.fwd.memr_data := io.bus.rdata
    io.fwd.ctl_wb := io.in.ctl_wb
    io.fwd.have_inst := io.in.debug.have_inst
}

class MEM_WB extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new MEM_WB_Bundle)
        val out = new MEM_WB_Bundle
    })

    private val reg = RegInit(0.U.asTypeOf(new MEM_WB_Bundle))
    reg := io.in
    io.out := reg
}

class WB extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new MEM_WB_Bundle)
        val reg = Flipped(new RF_Write_Bundle)
        val debug = new PipelineDebugBundle
    })

    io.reg.enable := 
        io.in.debug.have_inst & (io.in.ctl_wb.rfw_en === C.rfw_en.yes)
    io.reg.addr := io.in.rd
    M.mux(io.reg.data, 0.U, io.in.ctl_wb.rfw_sel,
        C.rfw_sel.alu_result -> io.in.result,
        C.rfw_sel.memory -> io.in.memr_data
    )

    io.debug := io.in.debug
}