package com.internship.scritto.documents

object CodeFormatter {
    fun alignForDisplay(source: String, language: String?): String {
        if (source.isBlank()) return source
        val lines = source.replace("\r\n", "\n").split("\n")
        val normalized = when (language) {
            "Python", "Ruby" -> indentBlockLanguage(lines)
            "C", "C++", "Java", "Kotlin", "JavaScript", "JavaScript React",
            "TypeScript", "TypeScript React", "Go", "Rust", "Swift", "C#",
            "PHP", "Dart", "Scala", "Groovy", "Objective-C", "Objective-C++",
            "Vue", "Svelte" -> indentBraceLanguage(lines)
            else -> lines
        }
        return normalized.joinToString("\n")
    }

    private fun indentBraceLanguage(lines: List<String>): List<String> {
        var level = 0
        return lines.map { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@map ""
            if (line.startsWith("}") || line.startsWith(")") || line.startsWith("]")) {
                level = (level - 1).coerceAtLeast(0)
            }
            val result = "    ".repeat(level) + line
            var opens = 0
            var closes = 0
            var quoted = false
            var escaped = false
            line.forEach { char ->
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"' || char == '\'') quoted = !quoted
                else if (!quoted && char == '{') opens++
                else if (!quoted && char == '}') closes++
            }
            level = (level + opens - closes).coerceAtLeast(0)
            result
        }
    }

    private fun indentBlockLanguage(lines: List<String>): List<String> {
        var level = 0
        return lines.map { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@map ""
            if (line.startsWith("return ") || line.startsWith("break") ||
                line.startsWith("continue") || line.startsWith("raise") ||
                line.startsWith("pass") || line.startsWith("else") ||
                line.startsWith("elif") || line.startsWith("except") ||
                line.startsWith("finally")
            ) level = (level - 1).coerceAtLeast(0)
            val result = "    ".repeat(level) + line
            if (line.endsWith(":") && !line.startsWith("#")) level++
            if (line == "end") level = (level - 1).coerceAtLeast(0)
            result
        }
    }
}
