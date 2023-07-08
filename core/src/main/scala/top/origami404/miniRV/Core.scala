package top.origami404.miniRV

import Chisel._
import chisel3.util.ValidIO
import top.origami404.miniRV.utils.M
import top.origami404.miniRV.{T, C}
import top.origami404.miniRV.Control
import top.origami404.miniRV.InstDecoder
import top.origami404.miniRV.RegFile
import top.origami404.miniRV.{InstRAMBundle, BusBundle, DebugBundle}
import top.origami404.miniRV.utils.F

class CPUCore extends Module {
    val io = IO(new Bundle {
        val inst_rom = new InstRAMBundle
        val bus = new BusBundle
        val debug_wb = new DebugBundle
    })
}

class IF_ID_Bundle extends Bundle {
    val inst = Output(T.Inst)
    val pc = Output(T.Addr)
}

class ID_EXE_Bundle extends Bundle {
    val pc = Output(T.Addr)
    val rd = Output(T.RegNo)
    val is_load = Output(Bool())

    val reg_rs1 = Output(T.Word)
    val reg_rs2 = Output(T.Word)
    val imm = Output(T.Word)

    val ctl_exe = new CTL_EXE_Bundle
    val ctl_mem = new CTL_MEM_Bundle
    val ctl_wb = new CTL_WB_Bundle
}

class EXE_MEM_Bundle extends Bundle {
    val rd = Output(T.RegNo)
    val is_load = Output(Bool())
    
    val result = Output(T.Word)
    val memw_data = Output(T.Word)

    val ctl_mem = new CTL_MEM_Bundle
    val ctl_wb = new CTL_WB_Bundle
}

class MEM_WB_Bundle extends Bundle {
    val result = Output(T.Word)
    val memr_data = Output(T.Word)

    val ctl_wb = new CTL_WB_Bundle
}

class PC extends Module {
    val io = IO(new Bundle {
        val npc_base = Input(T.Addr)
        val npc_offset = Input(T.Addr)
        val pc = Output(T.Addr)
        val pipe = new Bundle {
            val stall = Input(Bool())
        }
    })

    private val reg_pc = Reg(T.Addr, init = Inits.pc)

    reg_pc := io.npc_base + io.npc_offset
    io.pc := reg_pc
}

class IF extends Module {
    val io = IO(new Bundle {
        val rom = new InstRAMBundle
        val pc = Input(T.Addr)
        val out = new IF_ID_Bundle
    })

    io.rom.inst_addr := io.pc
    io.out.inst := io.rom.inst
    io.out.pc := io.pc
}

class IF_ID extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new IF_ID_Bundle)
        val out = new IF_ID_Bundle
        val pipe = new Bundle {
            val stall = Input(Bool())
        }
    })

    private val reg = Reg(new IF_ID_Bundle)
    when (!io.pipe.stall) {
        reg := io.in
    }
    io.out := reg
}

class ID_HZD_Bundle {
    val rs1 = Output(T.RegNo)
    val rs2 = Output(T.RegNo)
}

class ID extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new IF_ID_Bundle)
        val out = new ID_EXE_Bundle
        val reg = new RF_Read_Bundle
        val ctl_exe = Flipped(new CTL_EXE_Bundle)
        val ctl_mem = Flipped(new CTL_MEM_Bundle)
        val ctl_wb = Flipped(new CTL_WB_Bundle)
        val hzd = new ID_HZD_Bundle
    })

    private val decoder = Module(new InstDecoder)
    decoder.io.inst := io.in.inst

    io.reg.addr_1 := decoder.io.rs1
    io.reg.addr_2 := decoder.io.rs2

    io.out.pc := io.in.pc
    io.out.reg_rs1 := io.reg.data_1
    io.out.reg_rs2 := io.reg.data_2
    io.out.imm := decoder.io.imm
    io.out.rd := decoder.io.rd

    io.out.ctl_exe := io.ctl_exe
    io.out.ctl_mem := io.ctl_mem
    io.out.ctl_wb := io.ctl_wb

    io.hzd.rs1 := decoder.io.rs1
    io.hzd.rs2 := decoder.io.rs2
}

class ID_EXE extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new ID_EXE_Bundle)
        val out = new ID_EXE_Bundle
        val pipe = new Bundle {
            val nop = Input(Bool())
            val next_nop = Input(Bool())
        }
    })

    private val reg = Reg(new ID_EXE_Bundle)

    reg.pc      := io.in.pc 
    reg.rd      := io.in.rd 
    reg.is_load := io.in.is_load
    reg.reg_rs1     := io.in.reg_rs1 
    reg.reg_rs2     := io.in.reg_rs2 
    reg.imm     := io.in.imm 
    reg.ctl_exe := io.in.ctl_exe
    when (io.pipe.next_nop) {
        reg.ctl_mem := Inits.ctl_mem_nop
        reg.ctl_wb := Inits.ctl_wb_nop
    } .otherwise {
        reg.ctl_mem := io.in.ctl_mem
        reg.ctl_wb := io.in.ctl_wb
    }

    io.out.pc      := reg.pc 
    io.out.rd      := reg.rd 
    io.out.is_load := reg.is_load
    io.out.reg_rs1     := reg.reg_rs1 
    io.out.reg_rs2     := reg.reg_rs2 
    io.out.imm     := reg.imm 
    io.out.ctl_exe := reg.ctl_exe
    when (io.pipe.nop) {
        io.out.ctl_mem := Inits.ctl_mem_nop
        io.out.ctl_wb := Inits.ctl_wb_nop
    } .otherwise {
        io.out.ctl_mem := reg.ctl_mem
        io.out.ctl_wb := reg.ctl_wb
    }
}

class FWD_EXE_Bundle extends Bundle {
    val reg_rs1 = Output(ValidIO(T.Word))
    val reg_rs2 = Output(ValidIO(T.Word)) 
}

class PRED_EXE_Bundle extends Bundle {
    val br_real = Output(Bool())
}

class EXE extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new ID_EXE_Bundle)
        val out = new EXE_MEM_Bundle
        val fwd = Flipped(new FWD_EXE_Bundle)
        val pred = new PRED_EXE_Bundle
    })

    // forward select
    val reg_rs1 = Mux(io.fwd.reg_rs1.valid, io.fwd.reg_rs1.bits, io.in.reg_rs1)
    val reg_rs2 = Mux(io.fwd.reg_rs2.valid, io.fwd.reg_rs2.bits, io.in.reg_rs2)
    
    // lhs/rhs/-rhs select
    val lhs = Wire(T.Word)
    M.mux(lhs, 0.U, io.in.ctl_exe.lhs_sel, 
        C.lhs_sel.pc -> io.in.pc,
        C.lhs_sel.rs1 -> reg_rs1,
        C.lhs_sel.zero -> 0.U
    )
    
    val rhs_raw = Wire(T.Word)
    M.mux(rhs_raw, 0.U, io.in.ctl_exe.rhs_sel,
        C.rhs_sel.imm -> io.in.imm,
        C.rhs_sel.rs2 -> reg_rs2
    )
    
    val rhs = Wire(T.Word)
    M.mux(rhs, 0.U, io.in.ctl_exe.rhs_neg,
        C.rhs_neg.no -> rhs_raw,
        C.rhs_neg.yes -> F.tcNeg(rhs_raw)
    )
    
    // alu
    val alu = Module(new ALU)
    alu.io.op := io.in.ctl_exe.alu_sel
    alu.io.arg_1 := lhs
    alu.io.arg_2 := rhs


}