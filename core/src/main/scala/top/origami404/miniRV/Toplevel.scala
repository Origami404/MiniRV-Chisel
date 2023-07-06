package top.origami404.miniRV

import chisel3._
import chisel3.experimental.FlatIO

class InstRAMBundle extends Bundle {
  val inst_addr   = Output(UInt(13.W))
  val inst        = Input(UInt(32.W))
}

class BusBundle extends Bundle {
  val addr    = Output(UInt(32.W))
  val rdata   = Input(UInt(32.W))
  val wen     = Output(Bool())
  val wdata   = Output(UInt(32.W))
}

class DebugBundle extends Bundle {
  val have_inst   = Output(Bool())
  val pc          = Output(UInt(32.W))
  val ena         = Output(Bool())
  val reg         = Output(UInt(5.W))
  val value       = Output(UInt(32.W))
}

class Toplevel extends Module {
  override def desiredName = "toplevel"
  val inst = FlatIO(new InstRAMBundle)
  val bus = IO(new BusBundle)
  val debug_wb = IO(new DebugBundle)

  inst.inst_addr := 0.U

  bus.addr  := 0.U
  bus.wen   := false.B
  bus.wdata := 0.U

  debug_wb.have_inst  := false.B
  debug_wb.pc         := 0.U
  debug_wb.ena        := false.B
  debug_wb.reg        := 0.U
  debug_wb.value      := 0.U
}