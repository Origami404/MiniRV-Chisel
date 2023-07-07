package top.origami404.miniRV

import Chisel._
import chisel3.util.ValidIO

class RegFile extends Module {
    val io = IO(new Bundle {
        val read_addr_1 = Input(UInt(5.W))
        val read_addr_2 = Input(UInt(5.W))
        val write_addr  = Input(ValidIO(UInt(5.W)))
        val write_data  = Input(UInt(32.W))
        val read_data_1 = Output(UInt(32.W))
        val read_data_2 = Output(UInt(32.W))
    })

    private val reg_file = Mem(32, UInt(32.W))

    private val write_addr = io.write_addr.bits
    private val write_enable = io.write_addr.valid && write_addr =/= 0.U
    private val write_data = io.write_data
    when (write_enable) {
        reg_file(write_addr) := write_data
    }

    private val ra1 = io.read_addr_1
    private val rd1 = io.read_data_1
    when (ra1 === 0.U) {
        rd1 := 0.U
    } .elsewhen (write_enable & (ra1 === write_addr)) {
        rd1 := write_data
    } .otherwise {
        rd1 := reg_file(ra1)
    }

    private val ra2 = io.read_addr_2
    private val rd2 = io.read_data_2
    when (ra2 === 0.U) {
        rd2 := 0.U
    } .elsewhen (write_enable & (ra2 === write_addr)) {
        rd2 := write_data
    } .otherwise {
        rd2 := reg_file(ra2)
    }
}
