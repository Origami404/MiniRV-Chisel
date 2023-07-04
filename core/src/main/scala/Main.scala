import chisel3.stage.ChiselStage
import single_cycle.{ALU, CPUCore, Control, InstDecoder, RegFile, Toplevel}

object Main {
  def main(args: Array[String]): Unit = {
    val verilogCode = ChiselStage.emitVerilog(new CPUCore)
    print(verilogCode)
  }
}