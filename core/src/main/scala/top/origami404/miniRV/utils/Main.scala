package top.origami404.miniRV.utils

import chisel3.stage.ChiselStage
import top.origami404.miniRV.{ALU, CPUCore, Control, InstDecoder, RegFile, Toplevel}

object Main {
  def main(args: Array[String]): Unit = {
    val verilogCode = ChiselStage.emitVerilog(new CPUCore)
    print(verilogCode)
  }
}
