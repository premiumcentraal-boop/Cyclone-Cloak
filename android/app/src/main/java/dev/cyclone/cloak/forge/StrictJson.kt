package dev.cyclone.cloak.forge

import org.json.JSONTokener

/** Android's JSONTokener accepts non-JSON syntax. Reject it, duplicate keys and trailing data. */
internal class StrictJson(private val text: String) {
    private var index = 0
    private fun space() { while (index < text.length && text[index] in " \t\r\n") index++ }
    private fun token(char: Char) { space(); require(index < text.length && text[index++] == char) { "Malformed JSON." } }
    fun check() { value(0); space(); require(index == text.length) { "Trailing JSON data." } }
    private fun string(): String {
        space(); val start = index; token('"')
        while (index < text.length) {
            val char = text[index++]
            require(char >= ' ') { "Control character in JSON string." }
            if (char == '"') return JSONTokener(text.substring(start, index)).nextValue() as String
            if (char == '\\') {
                require(index < text.length) { "Incomplete JSON escape." }
                val escape = text[index++]
                require(escape in "\"\\/bfnrtu") { "Invalid JSON escape." }
                if (escape == 'u') { require(index + 4 <= text.length && text.substring(index, index + 4).all { it in "0123456789abcdefABCDEF" }) { "Invalid Unicode escape." }; index += 4 }
            }
        }
        throw IllegalArgumentException("Unterminated JSON string.")
    }
    private fun value(depth: Int) {
        require(depth <= 32) { "JSON is too deeply nested." }
        space(); require(index < text.length) { "Incomplete JSON." }
        when (text[index]) {
            '{' -> {
                index++; space(); val keys = mutableSetOf<String>()
                if (index < text.length && text[index] == '}') { index++; return }
                while (true) {
                    require(keys.add(string())) { "Duplicate JSON key." }; token(':'); value(depth + 1); space()
                    if (index < text.length && text[index] == '}') { index++; return }
                    token(',')
                }
            }
            '[' -> {
                index++; space(); if (index < text.length && text[index] == ']') { index++; return }
                while (true) { value(depth + 1); space(); if (index < text.length && text[index] == ']') { index++; return }; token(',') }
            }
            '"' -> string()
            else -> {
                val literal = Regex("(?:true|false|null|-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)").find(text, index)
                require(literal != null && literal.range.first == index) { "Invalid JSON value." }
                index += literal.value.length
            }
        }
    }
}
