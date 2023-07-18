package top.origami404.miniRV.io

import Chisel._
import chisel3.{Bundle, Module, Output, VecInit, withClock}
import top.origami404.miniRV.{BusBundle, CPUCore}

class IROM extends DistributedSinglePortROM(16384, 32) {}

class DRAM0 extends DistributedSinglePortRAM(16384, 8) {}

class DRAM1 extends DistributedSinglePortRAM(16384, 8) {}

class DRAM2 extends DistributedSinglePortRAM(16384, 8) {}

class DRAM3 extends DistributedSinglePortRAM(16384, 8) {}


class SoC extends Module {
    private def hex(s: String): BigInt = BigInt(s.filter(c => c != '_'), 16)

    val io = IO(new Bundle {
        val switch = Input(UInt(24.W))
        val button = Input(UInt(5.W))
        val dig_en = Output(UInt(8.W))
        val DN_A = Output(Bool())
        val DN_B = Output(Bool())
        val DN_C = Output(Bool())
        val DN_D = Output(Bool())
        val DN_E = Output(Bool())
        val DN_F = Output(Bool())
        val DN_G = Output(Bool())
        val DN_DP = Output(Bool())
        val led = Output(UInt(24.W))
    })

    private val cpu_clock = Wire(Clock())
    private val clkgen = Module(new PLL)
    clkgen.io.clk_in1 := this.clock
    cpu_clock := (clkgen.io.clk_out1.asBool & clkgen.io.locked).asClock

    withClock(cpu_clock) {
        val cpu_core = Module(new CPUCore)

        val ADDR_DIG = hex("FFFF_F000")
        val ADDR_LED = hex("FFFF_F060")
        val ADDR_SWITCH = hex("FFFF_F070")
        val ADDR_BUTTON = hex("FFFF_F078")
        val addr_space_range = Seq(
            (hex("0000_0000"), hex("FFFF_F000")), // memory
            (ADDR_DIG, ADDR_DIG + 4),
            (ADDR_LED, ADDR_LED + 3),
            (ADDR_SWITCH, ADDR_SWITCH + 3),
            (ADDR_BUTTON, ADDR_BUTTON + 1)
        )
        val bridge = Module(new Bridge(addr_space_range))
        bridge.io.cpu := cpu_core.io.bus

        val rom = Module(new IROM)
        rom.io.a := cpu_core.io.inst_rom.inst_addr(15, 2)
        cpu_core.io.inst_rom.inst := rom.io.spo

        // IO devices
        // RAM
        val bus0 = bridge.io.devices(0)
        val ram = Module(new InterleavedDRAM(new DRAM_Bundle(14, 4, 32)))

        // fuck Vivado's IP Core
        val dram0 = Module(new DRAM0)
        val dram1 = Module(new DRAM1)
        val dram2 = Module(new DRAM2)
        val dram3 = Module(new DRAM3)
        val drams = Seq(
            dram0, dram1, dram2, dram3
        )
        for (i <- 0 until 4) {
            val sub_ram = drams(i)
            val sub = ram.io.subs(i)
            sub_ram.io.a := sub.addr
            sub_ram.io.d := sub.write_data
            sub_ram.io.we := sub.write_enable
            sub.read_data := sub_ram.io.spo
        }
        val ram_self = ram.io.self
        // manually fix the data segment offset in program
        ram_self.addr := (bus0.addr - "h4000".U)(15, 2)
        ram_self.write_enable := bus0.wen
        ram_self.write_data := bus0.wdata
        bus0.rdata := ram_self.read_data

        // Dig
        val bus1 = bridge.io.devices(1)
        val dig = Module(new SevenSegDigital)
        dig.io.input_en := bus1.wen
        dig.io.input := bus1.wdata

        this.io.dig_en := dig.io.led_enable
        this.io.DN_A := dig.io.led.AN
        this.io.DN_B := dig.io.led.BN
        this.io.DN_C := dig.io.led.CN
        this.io.DN_D := dig.io.led.DN
        this.io.DN_E := dig.io.led.EN
        this.io.DN_F := dig.io.led.FN
        this.io.DN_G := dig.io.led.GN
        this.io.DN_DP := dig.io.led.dot

        // LED
        val bus2 = bridge.io.devices(2)
        val reg = RegInit(VecInit(Seq.fill(3)(0.U(8.W))))
        for (i <- 0 until 3) {
            when(bus2.wen(i)) {
                reg(i) := bus2.wdata(8 * i + 7, 8 * i)
            }
        }
        this.io.led := reg.asUInt

        // Switches
        val bus3 = bridge.io.devices(3)
        bus3.rdata := Cat(0.U((32 - 24).W), this.io.switch)

        // buttons
        val bus4 = bridge.io.devices(4)
        bus4.rdata := Cat(0.U((32 - 5).W), this.io.button)
    }
}

class BridgeDev_Bundle extends Bundle {
    val addr = Output(UInt(32.W))
    val wen = Output(UInt(4.W))
    val wdata = Output(UInt(32.W))
    val rdata = Input(UInt(32.W))
}

class Bridge(ranges: Seq[(BigInt, BigInt)]) extends Module {
    if (ranges.size > 10) {
        println("Warning: too many sub-devices")
    }

    val io = IO(new Bundle {
        val cpu = Flipped(new BusBundle)
        val devices = Vec(ranges.size, new BridgeDev_Bundle)
    })

    private val addr = io.cpu.addr
    private val within_range = Wire(Vec(ranges.size, Bool()))
    ranges.zipWithIndex.foreach { case ((beg, end), idx) =>
        within_range(idx) := (beg.U <= addr && addr < end.U)
    }

    io.devices.zipWithIndex.foreach { case (b, i) =>
        b.addr := addr
        b.wen := Fill(4, within_range(i)) & io.cpu.wen
        b.wdata := io.cpu.wdata
    }
    io.cpu.rdata := Mux1H(within_range, io.devices.map(_.rdata))
}