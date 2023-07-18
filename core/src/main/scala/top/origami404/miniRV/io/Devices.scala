package top.origami404.miniRV.io

import chisel3._
import chisel3.util._
import top.origami404.miniRV.T

/** 0 => on */
class SevenSeg_Bundle extends Bundle {
    val dot = Output(Bool())
    val bits = Output(UInt(7.W))

    def AN = bits(6)

    def BN = bits(5)

    def CN = bits(4)

    def DN = bits(3)

    def EN = bits(2)

    def FN = bits(1)

    def GN = bits(0)
}

class DigDecoder extends Module {
    val io = IO(new Bundle {
        val data = Input(UInt(4.W))
        val led = new SevenSeg_Bundle
    })

    io.led.bits := ~MuxLookup(io.data, 0.U, Seq(
        0x0.U -> "b1111110".U,
        0x1.U -> "b0110000".U,
        0x2.U -> "b1101101".U,
        0x3.U -> "b1111001".U,
        0x4.U -> "b0110011".U,
        0x5.U -> "b1011011".U,
        0x6.U -> "b1011111".U,
        0x7.U -> "b1110000".U,
        0x8.U -> "b1111111".U,
        0x9.U -> "b1111011".U,
        0xA.U -> "b1110111".U,
        0xB.U -> "b0011111".U,
        0xC.U -> "b1001110".U,
        0xD.U -> "b0111101".U,
        0xE.U -> "b1001111".U,
        0xF.U -> "b1000111".U
    ))
    io.led.dot := true.B
}

class CycleShiftRegister(width: Int) extends Module {
    val io = IO(new Bundle {
        val out = Output(UInt(width.W))
    })

    private val reg = RegInit(UInt(width.W), 1.U)
    when(reg(width - 1) === 1.U) {
        reg := 1.U
    }.otherwise {
        reg := reg << 1
    }

    io.out := reg
}

class SevenSegDigital extends Module {
    val io = IO(new Bundle {
        val input_en = Input(UInt(4.W))
        val input = Input(T.Word)
        val led_enable = Output(UInt(8.W))
        val led = new SevenSeg_Bundle
    })

    private val reg = RegInit(VecInit(Seq.fill(4)(0.U(8.W))))
    for (i <- 0 until 4) {
        when(io.input_en(i)) {
            reg(i) := io.input(8 * i + 7, 8 * i)
        }
    }

    private val enable_reg = Module(new CycleShiftRegister(8))
    private val led_en = enable_reg.io.out
    io.led_enable := led_en

    private val reg_bits = reg.asUInt
    private val decoder = Module(new DigDecoder)
    decoder.io.data := Mux1H(Seq.tabulate(8) { i =>
        led_en(i) -> reg_bits(4 * i + 3, 4 * i)
    })
    io.led := decoder.io.led
}

class DRAM_Bundle(val addr_w: Int, val enable_w: Int, val data_w: Int) extends Bundle {
    val addr = Input(UInt(addr_w.W))
    val write_enable = Input(UInt(enable_w.W))
    val write_data = Input(UInt(data_w.W))
    val read_data = Output(UInt(data_w.W))
}

class InterleavedDRAM(self_B: => DRAM_Bundle) extends Module {
    private val sub_cnt = self_B.enable_w
    if (self_B.data_w % sub_cnt != 0) {
        throw new RuntimeException("Unsupported interleaved DRAM param")
    }

    private val sub_data_w = self_B.data_w / sub_cnt

    private def sub_B = new DRAM_Bundle(self_B.addr_w, 1, sub_data_w)

    val io = IO(new Bundle {
        val subs = Vec(sub_cnt, Flipped(sub_B))
        val self = self_B
    })

    private val read_data = Wire(Vec(sub_cnt, UInt(sub_data_w.W)))
    private val write_data = Wire(Vec(sub_cnt, UInt(sub_data_w.W)))
    for (i <- 0 until sub_cnt) {
        io.subs(i).addr := io.self.addr
        io.subs(i).write_enable := io.self.write_enable(i)
        io.subs(i).write_data := write_data(i)
        read_data(i) := io.subs(i).read_data
    }

    // connect the read_data/write_data
    val w = sub_data_w
    for (i <- 0 until sub_cnt) {
        write_data(i) := io.self.write_data(w * (i + 1) - 1, w * i)
    }
    io.self.read_data := read_data.asUInt
}
