package top.origami404.miniRV.utils

import chisel3.stage.ChiselStage
import top.origami404.miniRV.{ALU, Control, InstDecoder, RegFile, Toplevel}
import top.origami404.miniRV.CPUCore
import top.origami404.miniRV.PC

object Main {
  def main(args: Array[String]): Unit = {
    val verilogCode = ChiselStage.emitVerilog(new PC)
    print(verilogCode)
  }
}
