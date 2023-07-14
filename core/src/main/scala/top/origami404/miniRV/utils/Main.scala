package top.origami404.miniRV.utils

import chisel3.Module
import chisel3.stage.ChiselStage
import top.origami404.miniRV.CPUCore
import top.origami404.miniRV.io.{DRAM_Bundle, InterleavedDRAM, LED, SevenSegDigital}

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
        emitVerilog("vivado/proj_pipeline.srcs/sources_1/new/myCPU.v", new CPUCore)
        emitVerilog("vivado/proj_pipeline.srcs/sources_1/new/SegDig.v", new SevenSegDigital)
        emitVerilog("vivado/proj_pipeline.srcs/sources_1/new/LED.v", new LED(24))
        emitVerilog("vivado/proj_pipeline.srcs/sources_1/new/InterleavedDRAM.v",
            new InterleavedDRAM(new DRAM_Bundle(14, 4, 32)))
    }
}
