package single_cycle

import Chisel._



class PipelineStage[T <: Bundle](bundle: => T) extends Module {
    val io = IO(new Bundle {
        val in = Flipped(bundle)
        val out = bundle
    })

    protected val reg = Reg(bundle)
    io.out := reg
    reg := io.in
}

class IF_ID extends PipelineStage(new Bundle {
    val inst = Output(DataT.Inst)
    val pc = Output(DataT.Addr)
}) {}

//class ID_EXE extends PipelineStage(new Bundle {
//    val lhs = UInt(32.W)
//    val rhs = UInt(32.W)
//}) {}