package top.origami404.miniRV.utils

import chisel3.Module
import chisel3.stage.ChiselStage
import top.origami404.miniRV.CPUCore
import top.origami404.miniRV.io.SoC

object Main {
    private def emitVerilog(path: String, mod: => Module) = {
        val code = ChiselStage.emitVerilog(mod)
        import java.io._
        val writer = new PrintWriter(new File(path))
        writer.write(code)
        writer.close()
    }

    def main(args: Array[String]): Unit = {
        emitVerilog("trace/mySoC/CPUCore.v", new CPUCore)
        emitVerilog("vivado/proj_pipeline.srcs/sources_1/new/SoC.v", new SoC)
    }
}
