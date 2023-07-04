package single_cycle

import Chisel._

class CPUCore extends Module {
    val inst_rom = IO(new InstRAMBundle)
    val bus = IO(new BusBundle)
    val debug_wb = IO(new DebugBundle)

    val CTL = Module(new Control)

    // =============== IF ===============

    // =============== ID ===============

    // ============== EXE ===============

    // ============== MEM ===============

    // ============== WB ===============
}




