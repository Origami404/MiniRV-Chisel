package top.origami404.miniRV

import Chisel._
import chisel3.util.ValidIO
import top.origami404.miniRV.{T, ALUOps}
import top.origami404.miniRV.utils.F
import top.origami404.miniRV.utils.M

class InstDecoder extends Module {
    val io = IO(new Bundle {
        val inst    = Input(T.Inst)
        val rd      = Output(T.RegNo)
        val rs1     = Output(T.RegNo)
        val rs2     = Output(T.RegNo)
        val imm     = Output(T.Word)

        val is_load    = Output(Bool())
        val is_br      = Output(Bool())
        val is_jal     = Output(Bool())
        val is_jalr    = Output(Bool())
    })

    private val inst = io.inst
    io.rd := inst(11, 7)
    io.rs1 := inst(19, 15)
    io.rs2 := inst(24, 20)

    private val opcode = inst(6, 0)

    private val is_I = opcode === Opcodes.ARITH_IMM ||
        opcode === Opcodes.LOAD || opcode === Opcodes.JALR || opcode === Opcodes.SYSTEM
    private val is_S = opcode === Opcodes.STORE
    private val is_B = opcode === Opcodes.BRANCH
    private val is_U = opcode === Opcodes.LUI || opcode === Opcodes.AUIPC
    private val is_J = opcode === Opcodes.JAL
    private val is_R = opcode === Opcodes.ARITH

    private final val width = T.Word.getWidth
    when (is_I) {
        io.imm := F.signExtend(width, inst(31, 20))
    } .elsewhen (is_S) {
        io.imm := F.signExtend(width, Cat(inst(31, 25), inst(11, 7)))
    } .elsewhen (is_B) {
        io.imm := F.signExtend(width, Cat(inst(31, 31), inst(7, 7), inst(30, 25), inst(11, 8), 0.U(1.W)))
    } .elsewhen (is_U) {
        io.imm := Cat(inst(31, 12), 0.U(12.W))
    } .elsewhen (is_J) {
        val bits = Cat(inst(31, 31), inst(19, 12), inst(20, 20), inst(30, 21), 0.U(1.W))
        io.imm := F.signExtend(width, bits)
    } .otherwise {
        io.imm := 0.U(32.W)
    }

    io.is_br := is_B
    io.is_jal := opcode === Opcodes.JAL
    io.is_jalr := opcode === Opcodes.JALR
    io.is_load := opcode === Opcodes.LOAD
}

class RF_Read_Bundle extends Bundle {
    val addr_1 = Input(T.RegNo)
    val addr_2 = Input(T.RegNo)
    val data_1 = Output(T.Word)
    val data_2 = Output(T.Word)
}

class RF_Write_Bundle extends Bundle {
    val enable = Input(Bool())
    val addr = Input(T.RegNo)
    val data = Input(T.Word)
}

class RegFile extends Module {
    val io = IO(new Bundle {
        val r = new RF_Read_Bundle
        val w = new RF_Write_Bundle
    })

    private val reg_file = Mem(32, UInt(32.W))

    private val write_addr = io.w.addr
    private val write_enable = io.w.enable && write_addr =/= 0.U
    private val write_data = io.w.data
    when (write_enable) {
        reg_file(write_addr) := write_data
    }

    private val ra1 = io.r.addr_1
    private val rd1 = io.r.data_1
    when (ra1 === 0.U) {
        rd1 := 0.U
    } .elsewhen (write_enable & (ra1 === write_addr)) {
        rd1 := write_data
    } .otherwise {
        rd1 := reg_file(ra1)
    }

    private val ra2 = io.r.addr_2
    private val rd2 = io.r.data_2
    when (ra2 === 0.U) {
        rd2 := 0.U
    } .elsewhen (write_enable & (ra2 === write_addr)) {
        rd2 := write_data
    } .otherwise {
        rd2 := reg_file(ra2)
    }
}

class ALU extends Module {
    val io = IO(new Bundle {
        val op      = Input(ALUOps.dataT)
        val arg_1   = Input(T.Word)
        val arg_2   = Input(T.Word)
        val result  = Output(T.Word)
        val eq    = Output(Bool())
        val lti     = Output(Bool())
        val ltu     = Output(Bool())
    })

    private val op = io.op
    private val lhs = io.arg_1
    private val rhs = io.arg_2

    private val res = Wire(T.Word)
    when (op === ALUOps.AND) {
        res := lhs & rhs
    } .elsewhen (op === ALUOps.OR) {
        res := lhs | rhs
    } .elsewhen (op === ALUOps.XOR) {
        res := lhs ^ rhs
    } .elsewhen (op === ALUOps.SLL) {
        res := lhs << rhs(4, 0)
    } .elsewhen (op === ALUOps.SRL) {
        res := lhs >> rhs(4, 0)
    } .elsewhen (op === ALUOps.SRA) {
        res := F.tcSra(lhs, rhs(4, 0))
    } .otherwise { // ADD
        res := F.tcAdd(lhs, rhs)
    }

    io.result   := res
    io.eq       := lhs === rhs
    io.lti      := lhs.asSInt < rhs.asSInt
    io.ltu      := lhs < rhs
}

class BRU extends Module {
    val io = IO(new Bundle {
        val op = Input(BRUOps.dataT)
        val eq = Input(Bool())
        val lti = Input(Bool())
        val ltu = Input(Bool())
        val should_br = Output(Bool())
    })

    import BRUOps._
    io.should_br := MuxLookup(io.op, false.B, Seq( 
        EQ -> io.eq,
        NE -> !io.eq,
        GE -> !io.lti,
        LT -> io.lti,
        GEU -> !io.ltu,
        LTU -> io.ltu
    ))
}
