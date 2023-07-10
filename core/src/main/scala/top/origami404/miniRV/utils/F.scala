package top.origami404.miniRV.utils

import Chisel._

object F {
    def signExtend(target_width: Int, source: UInt): UInt = {
        val source_width = source.getWidth
        if (target_width < source_width) {
            throw new RuntimeException("target width < source width")
        } else if (target_width == source_width) {
            source
        } else {
            val sign_bit = source(source_width - 1).asUInt
            val sign_ext = Fill(target_width - source_width, sign_bit)
            Cat(sign_ext, source)
        }
    }

    /** negative in two's complement way */ 
    def tcNeg(source: UInt): UInt = (-(source.asSInt)).asUInt
    /** add in two's complement way */
    def tcAdd(lhs: UInt, rhs: UInt): UInt = (lhs.asSInt + rhs.asSInt).asUInt
    /** shift right arith in two's complement way */
    def tcSra(lhs: UInt, rhs: UInt): UInt = (lhs.asSInt >> rhs).asUInt
}
