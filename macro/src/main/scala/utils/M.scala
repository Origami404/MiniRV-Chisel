package utils

import Chisel.Data

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object M {

    def implMux[T: c.WeakTypeTag, R: c.WeakTypeTag](c: blackbox.Context)(out: c.Expr[R], default: c.Expr[R], in: c.Expr[T], limbs: c.Expr[(T, R)]*): c.Expr[Unit] = {
        import c.universe._

        // !!!!!!! MAGIC HERE, DO NOT TOUCH !!!!!!!!!
        def unpack_assoc(assoc: Tree): (Tree, Tree) = {
            val first = assoc.children(0).children(0).children(0).children(1)
            val second = assoc.children(1)
            (first, second)
        }

        if (limbs.isEmpty) {
            c.Expr[Unit](q"$out := $default")
        } else {
            // when ($in === $limbs(0)._1) { $out := $limbs(0)._2 }
            // .elsewhen ($in === $limbs(1)._1) { $out := $limbs(1)._2 }
            // ...
            // .otherwise { $out := $default }
            val when_expr = {
                val (in_v, out_v) = unpack_assoc(limbs(0).tree)
                q"when ($in === $in_v) { $out := $out_v }"
            }
            val elsewhen_expr = limbs.drop(0).foldLeft(when_expr) ((left, limb) => {
                val (in_v, out_v) = unpack_assoc(limb.tree)
                q"$left .elsewhen ($in === $in_v) { $out := $out_v }"
            })
            val otherwise_expr = q"$elsewhen_expr .otherwise { $out := $default }"
            c.Expr[Unit](otherwise_expr)
        }
    }
    def mux[T <: Data, R <: Data](out: R, default: R, in: T, limbs: (T, R)*): Unit = macro implMux[T, R]
}