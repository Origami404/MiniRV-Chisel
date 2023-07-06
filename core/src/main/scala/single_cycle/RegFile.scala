package single_cycle

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

    io.read_data_1 := Mux(io.read_addr_1 === 0.U, 0.U, reg_file(io.read_addr_1))
    io.read_data_2 := Mux(io.read_addr_2 === 0.U, 0.U, reg_file(io.read_addr_2))

    private val write_addr = io.write_addr.bits
    private val write_enable = io.write_addr.valid && write_addr =/= 0.U
    when (write_enable) {
        reg_file(write_addr) := io.write_data
    }
}
