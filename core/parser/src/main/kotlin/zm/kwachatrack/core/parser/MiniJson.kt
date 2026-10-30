package zm.kwachatrack.core.parser

/**
 * A small, strict JSON reader so the parser module has zero dependencies and its tests
 * run on a plain JVM. Returns Map<String, Any?>, List<Any?>, String, Double, Boolean or null.
 */
internal class MiniJson private constructor(
    private val s: String,
) {
    private var i = 0

    companion object {
        fun parse(text: String): Any? {
            val p = MiniJson(text)
            p.ws()
            val v = p.value()
            p.ws()
            if (p.i != p.s.length) p.fail("Trailing content")
            return v
        }
    }

    private fun value(): Any? {
        ws()
        if (i >= s.length) fail("Unexpected end")
        return when (s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> num()
        }
    }

    private fun obj(): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        i++
        ws()
        if (peek() == '}') {
            i++
            return map
        }
        while (true) {
            ws()
            if (peek() != '"') fail("Expected key")
            val key = str()
            ws()
            expect(':')
            map[key] = value()
            ws()
            when (next()) {
                ',' -> continue
                '}' -> return map
                else -> fail("Expected , or }")
            }
        }
    }

    private fun arr(): List<Any?> {
        val list = ArrayList<Any?>()
        i++
        ws()
        if (peek() == ']') {
            i++
            return list
        }
        while (true) {
            list += value()
            ws()
            when (next()) {
                ',' -> continue
                ']' -> return list
                else -> fail("Expected , or ]")
            }
        }
    }

    private fun str(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            if (i >= s.length) fail("Unterminated string")
            val c = s[i++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    when (val e = next()) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (i + 4 > s.length) fail("Bad unicode escape")
                            sb.append(s.substring(i, i + 4).toInt(16).toChar())
                            i += 4
                        }
                        else -> fail("Bad escape \\$e")
                    }
                }
                else -> sb.append(c)
            }
        }
    }

    private fun num(): Double {
        val start = i
        while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
        return s.substring(start, i).toDoubleOrNull() ?: fail("Bad number")
    }

    private fun literal(
        word: String,
        v: Any?,
    ): Any? {
        if (!s.startsWith(word, i)) fail("Expected $word")
        i += word.length
        return v
    }

    private fun ws() {
        while (i < s.length && s[i].isWhitespace()) i++
    }

    private fun peek(): Char? = s.getOrNull(i)

    private fun next(): Char = s.getOrNull(i++) ?: fail("Unexpected end")

    private fun expect(c: Char) {
        if (next() != c) fail("Expected '$c'")
    }

    private fun fail(msg: String): Nothing = throw IllegalArgumentException("$msg at position $i")
}
