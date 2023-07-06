package top.origami404.miniRV.utils

import Chisel._

object F {
    def signExtend(target_width: Int, source: UInt): SInt = {
        val source_width = source.getWidth
        if (target_width < source_width) {
            throw new RuntimeException("target width < source width")
        } else if (target_width == source_width) {
            source.asSInt
        } else {
            val sign_bit = source(source_width - 1).asUInt
            val sign_ext = Fill(target_width - source_width, sign_bit)
            (sign_ext + source).asSInt
        }
    }
}
