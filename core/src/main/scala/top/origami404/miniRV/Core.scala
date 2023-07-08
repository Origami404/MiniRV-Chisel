package top.origami404.miniRV

import Chisel._
import top.origami404.miniRV.utils.M
import top.origami404.miniRV.{DataT, Controls}
import top.origami404.miniRV.Control
import top.origami404.miniRV.InstDecoder
import top.origami404.miniRV.RegFile
import top.origami404.miniRV.{InstRAMBundle, BusBundle, DebugBundle}
import top.origami404.miniRV.utils.F

class CPUCore extends Module {
    val io = IO(new Bundle {
        val inst_rom = new InstRAMBundle
        val bus = new BusBundle
        val debug_wb = new DebugBundle
    })

    val CTL = Module(new Control)
    CTL.io.inst := this.io.inst_rom.inst

    // =============== IF ===============
    val pc = Reg(DataT.Addr)
    val pc_next = pc + 4.U

    private val pc_offset = Wire(DataT.Addr)
    M.mux(pc_offset, 0.U, CTL.io.pc_sel,
        Controls.pc_sel.next -> pc_next,
        Controls.pc_sel.alu -> 1.U,
        Controls.pc_sel.imm -> 2.U,
    )

    pc := pc + pc_offset

    this.io.inst_rom.inst_addr := pc

    // =============== ID ===============
    val stage_if_id = Module(new IF_ID)
    stage_if_id.io.in.inst := this.io.inst_rom.inst
    stage_if_id.io.in.pc := pc

    val inst_decorer = Module(new InstDecoder)
    inst_decorer.io.inst := stage_if_id.io.out.inst

    val reg_file = Module(new RegFile)
    reg_file.io.read_addr_1 := inst_decorer.io.rs1
    reg_file.io.read_addr_2 := inst_decorer.io.rs2
    
    val lhs = Wire(DataT.Word)
    M.mux(lhs, 0.U, CTL.io.lhs_sel,
        Controls.lhs_sel.zero -> 0.U,
        Controls.lhs_sel.pc -> stage_if_id.io.out.pc,
        Controls.lhs_sel.rs1 -> reg_file.io.read_data_1
    )

    val rhs_raw = Wire(DataT.Word)
    M.mux(rhs_raw, 0.U, CTL.io.rhs_sel, 
        Controls.rhs_sel.rs2 -> reg_file.io.read_data_2,
        Controls.rhs_sel.imm -> inst_decorer.io.imm
    )

    val rhs = Wire(DataT.Word)
    M.mux(rhs, 0.U, CTL.io.rhs_neg,
        Controls.rhs_neg.yes -> F.tcNeg(rhs_raw),
        Controls.rhs_neg.no -> rhs_raw
    )

    // ============== EXE ===============
    val stage_id_exe = Module(new ID_EXE)
    stage_id_exe.io.in.pc := stage_if_id.io.out.pc
    stage_id_exe.io.in.lhs := lhs
    stage_id_exe.io.in.rhs := rhs

    val alu = Module(new ALU)
    alu.io.arg_1 := stage_id_exe.io.out.lhs
    alu.io.arg_2 := stage_id_exe.io.out.rhs
    alu.io.op := CTL.io.alu_sel
    
    // ============== MEM ===============
    val stage_exe_mem = Module(new EXE_MEM)
    stage_exe_mem.io.in.pc := stage_id_exe.io.out.pc
    stage_exe_mem.io.in.memw_data := stage_id_exe.io.out.rhs
    stage_exe_mem.io.in.result := alu.io.result

    this.io.bus.wen := CTL.io.memw_en
    this.io.bus.addr := stage_exe_mem.io.out.result
    this.io.bus.wdata := stage_exe_mem.io.out.memw_data
    private val memr_data = this.io.bus.rdata

    // ============== WB ===============
    val stage_mem_wb = Module(new MEM_WB)
    stage_mem_wb.io.in.pc := stage_exe_mem.io.out.pc
    stage_mem_wb.io.in.memr_data := memr_data
    stage_mem_wb.io.in.result := stage_exe_mem.io.out.result

    private val wb_pc_next = stage_mem_wb.io.out.pc + 4.U
    private val wb_data = Wire(DataT.Word)
    M.mux(wb_data, 0.U, CTL.io.rfw_sel, 
        Controls.rfw_sel.alu_result -> stage_mem_wb.io.out.result,
        Controls.rfw_sel.alu_neg_flag -> 0.U, // TODO
        Controls.rfw_sel.memory -> stage_mem_wb.io.out.memr_data,
        Controls.rfw_sel.pc_next -> wb_pc_next
    )
    reg_file.io.write_data := wb_data
}

class IF_ID_Bundle extends Bundle {
    val inst = Output(DataT.Inst)
    val pc = Output(DataT.Addr)
}

class ID_EXE_Bundle extends Bundle {
    val pc = Output(DataT.Addr)
    val lhs = Output(DataT.Word)
    val rhs = Output(DataT.Word)
    // Controls

}


class PipelineStage[T <: Bundle](bundle: => T) extends Module {
    val io = IO(new Bundle {
        val in = Flipped(bundle)
        val out = bundle
    })

    protected val reg = Reg(bundle)
    io.out := reg
    reg := io.in
}

class IF_ID extends PipelineStage(new Bundle {
    val inst = Output(DataT.Inst)
    val pc = Output(DataT.Addr)
}) {}

class ID_EXE extends PipelineStage(new Bundle {
    val pc = Output(DataT.Inst)
    val lhs = Output(DataT.Word)
    val rhs = Output(DataT.Word)
}) {}

class EXE_MEM extends PipelineStage(new Bundle {
    val pc = Output(DataT.Inst)
    val memw_data = Output(DataT.Word) 
    val result = Output(DataT.Word)
})

class MEM_WB extends PipelineStage(new Bundle {
    val pc = Output(DataT.Inst)
    val memr_data = Output(DataT.Word)
    val result = Output(DataT.Word)
})



