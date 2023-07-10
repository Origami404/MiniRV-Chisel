package top.origami404.miniRV.utils

import chisel3.stage.ChiselStage
import top.origami404.miniRV.{CPUCore, Toplevel}

object Main {
  def main(args: Array[String]): Unit = {
    val verilogCode = ChiselStage.emitVerilog(new CPUCore)
    print(verilogCode)
  }
}
