import chisel3.stage.ChiselStage
import single_cycle.{InstDecoder, Toplevel}

object Main {
  def main(args: Array[String]): Unit = {
    val verilogCode = ChiselStage.emitVerilog(new InstDecoder)
    print(verilogCode)
  }
}
