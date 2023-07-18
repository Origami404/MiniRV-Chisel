package top.origami404.miniRV.io

import chisel3._
import chisel3.util._

class DistributedSinglePortRAM(depth: Int, data_width: Int) extends BlackBox {
    val io = IO(new Bundle {
        val a = Input(UInt(log2Ceil(depth).W))
        val d = Input(UInt(data_width.W))
        val clk = Input(Clock())
        val we = Input(Bool())
        val spo = Output(UInt(data_width.W))
    })
}

class DistributedSinglePortROM(depth: Int, data_width: Int) extends BlackBox {
    val io = IO(new Bundle {
        val a = Input(UInt(log2Ceil(depth).W))
        val spo = Output(UInt(data_width.W))
    })
}

class PLL extends BlackBox {
    val io = IO(new Bundle {
        val clk_in1 = Input(Clock())
        val clk_out1 = Output(Clock())
        val locked = Output(Bool())
    })
}