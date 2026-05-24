package interpretation

import abstract_syntax.AddExpr
import abstract_syntax.Expr
import abstract_syntax.NumExpr

fun eval(expr: Expr): Int =
    when (expr) {
        is NumExpr -> expr.value
        is AddExpr -> eval(expr.left) + eval(expr.right)
        else -> error("Unknown expression type: ${expr.javaClass.name}")
    }