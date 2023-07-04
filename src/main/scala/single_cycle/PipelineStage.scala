package single_cycle

import Chisel._

class PipelineStage[T <: Bundle](bundle: T) extends Module {
    val in = IO(Input(bundle))
    val out = IO(Output(bundle))
    protected val reg = Reg(bundle)
    out := reg
    reg := in
}

class IF_ID extends PipelineStage(new Bundle {
    val inst = UInt(32.W)
    val pc = UInt(32.W)
}) {}

class ID_EX extends PipelineStage(new Bundle {
    val lhs = UInt(32.W)
    val rhs = UInt(32.W)
}) {}