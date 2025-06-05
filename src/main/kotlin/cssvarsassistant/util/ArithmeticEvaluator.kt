package cssvarsassistant.util

/**
 * Very small evaluator for simple LESS/SCSS arithmetic expressions.
 * Supports +, -, *, / with parentheses and unit propagation.
 * Only allows operations between values with the same unit or a unit and a scalar.
 * Returns null if the expression can't be evaluated.
 */
object ArithmeticEvaluator {
    private data class Value(var number: Double, val unit: String?)

    private val tokenRegex = Regex("([0-9]*\\.?[0-9]+(?:[a-zA-Z%]+)?)|([()+\\-*/])")
    private val valueRegex = Regex("([+-]?[0-9]*\\.?[0-9]+)([a-zA-Z%]+)?")

    fun evaluate(raw: String): String? {
        // Quick check: skip evaluation if the string has no operators
        if (!raw.contains('+') && !raw.contains('-') &&
            !raw.contains('*') && !raw.contains('/') &&
            !raw.contains('(') && !raw.contains(')')) {
            return null
        }

        val tokens = tokenRegex.findAll(raw.replace("\u00A0", "").replace(" ", ""))
            .map { it.value }
            .toList()
        if (tokens.isEmpty()) return null

        val values = ArrayDeque<Value>()
        val ops = ArrayDeque<Char>()

        fun precedence(op: Char) = when (op) {
            '+', '-' -> 1
            '*', '/' -> 2
            else -> 0
        }

        fun applyOp(): Boolean {
            if (ops.isEmpty() || values.size < 2) return false
            val op = ops.removeLast()
            val b = values.removeLast()
            val a = values.removeLast()

            val res = when (op) {
                '+' -> if (a.unit == b.unit || b.unit == null || a.unit == null) {
                    val unit = a.unit ?: b.unit
                    Value(a.number + b.number, unit)
                } else return false
                '-' -> if (a.unit == b.unit || b.unit == null) {
                    Value(a.number - b.number, a.unit)
                } else return false
                '*' -> when {
                    a.unit != null && b.unit == null -> Value(a.number * b.number, a.unit)
                    a.unit == null && b.unit != null -> Value(a.number * b.number, b.unit)
                    a.unit == null && b.unit == null -> Value(a.number * b.number, null)
                    else -> return false
                }
                '/' -> when {
                    b.number == 0.0 -> return false
                    a.unit != null && b.unit == null -> Value(a.number / b.number, a.unit)
                    a.unit == null && b.unit == null -> Value(a.number / b.number, null)
                    else -> return false
                }
                else -> return false
            }
            values.addLast(res)
            return true
        }

        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            when (t) {
                "(" -> ops.addLast('(')
                ")" -> {
                    while (ops.isNotEmpty() && ops.last() != '(') {
                        if (!applyOp()) return null
                    }
                    if (ops.isEmpty() || ops.removeLast() != '(') return null
                }
                "+", "-", "*", "/" -> {
                    val op = t[0]
                    while (ops.isNotEmpty() && precedence(ops.last()) >= precedence(op)) {
                        if (!applyOp()) return null
                    }
                    ops.addLast(op)
                }
                else -> {
                    val m = valueRegex.matchEntire(t) ?: return null
                    val num = m.groupValues[1].toDouble()
                    val unit = m.groupValues[2].takeIf { it.isNotEmpty() }
                    values.addLast(Value(num, unit))
                }
            }
            i++
        }
        while (ops.isNotEmpty()) {
            if (ops.last() == '(') return null
            if (!applyOp()) return null
        }
        if (values.size != 1) return null
        val res = values.first()
        val numStr = if (res.number % 1.0 == 0.0) res.number.toInt().toString() else res.number.toString()
        return numStr + (res.unit ?: "")
    }
}
