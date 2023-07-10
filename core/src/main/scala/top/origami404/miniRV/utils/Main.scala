package top.origami404.miniRV.utils

import chisel3.stage.ChiselStage
import top.origami404.miniRV.{CPUCore}

object Main {
  final val PATH = "trace/mySoC/CPUCore.v"

  def main(args: Array[String]): Unit = {
    val verilogCode = ChiselStage.emitVerilog(new CPUCore)
    // wirte verilog code to file
    import java.io._
    val writer = new PrintWriter(new File(PATH))
    writer.write(verilogCode)
    writer.close()
  }
}
