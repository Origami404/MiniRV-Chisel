package top.origami404.miniRV.io

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class SevenSegDigitalTest extends AnyFlatSpec 
    with ChiselScalatestTester 
    with Matchers 
{
    def intToSevenSet(x: Int): UInt = {
        x match {
            case 0x0 => "b1111110".U
            case 0x1 => "b0110000".U
            case 0x2 => "b1101101".U
            case 0x3 => "b1111001".U
            case 0x4 => "b0110011".U
            case 0x5 => "b1011011".U
            case 0x6 => "b1011111".U
            case 0x7 => "b1110000".U
            case 0x8 => "b1111111".U
            case 0x9 => "b1111011".U
            case 0xA => "b1110111".U
            case 0xB => "b0011111".U
            case 0xC => "b1001110".U
            case 0xD => "b0111101".U
            case 0xE => "b1001111".U
            case 0xF => "b1000111".U
        }
    }

    behavior of "SevenSegDigital"

    it should "show the in input content" in {
        test(new SevenSegDigital) { c =>
            c.io.input_en.poke("hF".U)
            c.io.input.poke("h87654321".U)
            // don't know why, the first cycle is unstable
            c.clock.step(8)
            for (cnt <- 1 to 10) {
                for (i <- 1 to 8) {
                    c.io.led_enable.expect((1 << (i - 1)).U)
                    c.io.led.bits.expect(intToSevenSet(i))
                    c.clock.step()
                }
            }

            c.io.input.poke("h12345678".U)
            c.clock.step(8)
            for (cnt <- 1 to 10) {
                for (i <- 1 to 8) {
                    c.io.led_enable.expect((1 << (i - 1)).U)
                    c.io.led.bits.expect(intToSevenSet(8 - i + 1))
                    c.clock.step()
                }
            }
        }
    }
}