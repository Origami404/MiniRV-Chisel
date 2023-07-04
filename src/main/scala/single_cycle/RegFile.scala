package single_cycle

import Chisel._
import chisel3.util.ValidIO

class RegFile extends Module {
    val in = IO(Input(new Bundle {
        val read_addr_1 = UInt(5.W)
        val read_addr_2 = UInt(5.W)
        val write_addr = ValidIO(UInt(5.W))
        val write_data = UInt(32.W)
    }))

    val out = IO(Output(new Bundle {
        val read_data_1 = UInt(32.W)
        val read_data_2 = UInt(32.W)
    }))

    private val reg_file = Mem(32, UInt(32.W))

    out.read_data_1 := Mux(in.read_addr_1 === 0.U, 0.U, reg_file(in.read_addr_1))
    out.read_data_2 := Mux(in.read_addr_2 === 0.U, 0.U, reg_file(in.read_addr_2))

    private val write_addr = in.write_addr.bits
    private val write_enable = in.write_addr.valid && write_addr =/= 0.U
    when (write_enable) {
        reg_file(write_addr) := in.write_data
    }
}
