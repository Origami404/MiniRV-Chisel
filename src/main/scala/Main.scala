import chisel3.stage.ChiselStage
import single_cycle.Toplevel

object Main {
  def main(args: Array[String]): Unit = {
    val verilogCode = ChiselStage.emitVerilog(new Toplevel)
    print(verilogCode)
  }
}
