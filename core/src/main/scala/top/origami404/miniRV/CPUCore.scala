package top.origami404.miniRV

import Chisel._
import top.origami404.miniRV.utils.M
import top.origami404.miniRV.{DataT, Controls}
import top.origami404.miniRV.Control
import top.origami404.miniRV.InstDecoder
import top.origami404.miniRV.IF_ID
import top.origami404.miniRV.RegFile
import top.origami404.miniRV.{InstRAMBundle, BusBundle, DebugBundle}

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

    io.inst_rom.inst_addr := pc

    // =============== ID ===============
    val stage_if_id = Module(new IF_ID)
    stage_if_id.io.in.inst := io.inst_rom.inst
    stage_if_id.io.in.pc := pc

    val inst_decorer = Module(new InstDecoder)
    inst_decorer.io.inst := stage_if_id.io.out.inst

    val reg_file = Module(new RegFile)
    reg_file.io.read_addr_1 := inst_decorer.io.rs1
    reg_file.io.read_addr_2 := inst_decorer.io.rs2
    
    val lhs = Wire(DataT.SWord)
    M.mux(lhs, 0.S, CTL.io.lhs_sel,
        Controls.lhs_sel.zero -> 0.S,
        Controls.lhs_sel.pc -> stage_if_id.io.out.pc.asSInt,
        Controls.lhs_sel.rs1 -> reg_file.io.read_data_1.asSInt
    )

    val rhs_raw = Wire(DataT.SWord)
    M.mux(rhs_raw, 0.S, CTL.io.rhs_sel, 
        Controls.rhs_sel.rs2 -> reg_file.io.read_data_2.asSInt,
        Controls.rhs_sel.imm -> inst_decorer.io.imm.asSInt
    )

    val rhs = Wire(DataT.SWord)
    M.mux(rhs, 0.S, CTL.io.rhs_neg,
        Controls.rhs_neg.yes -> -rhs_raw,
        Controls.rhs_neg.no -> rhs_raw
    )

    // ============== EXE ===============
    val stage_id_exe = 0
    
    // ============== MEM ===============
    val stage_exe_mem = 0

    // ============== WB ===============
    val stage_mem_wb = 0
}




