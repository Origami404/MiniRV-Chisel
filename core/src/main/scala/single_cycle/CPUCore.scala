package single_cycle

import Chisel._
import utils.M

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
    private val pc_sel = CTL.io.pc_sel
    M.mux(pc_offset, 0.U, pc_sel,
        Controls.pc_sel.next -> pc_next,
        Controls.pc_sel.alu -> 1.U,
        Controls.pc_sel.imm -> 2.U,
    )

    pc := pc + pc_offset

    io.inst_rom.inst_addr := pc

    // =============== ID ===============
    val stage_if_id = Module(new IF_ID)

    // ============== EXE ===============
    val stage_id_exe = 0
    
    // ============== MEM ===============
    val stage_exe_mem = 0

    // ============== WB ===============
    val stage_mem_wb = 0
}




