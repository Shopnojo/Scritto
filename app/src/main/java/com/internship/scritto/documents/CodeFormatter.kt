package com.internship.scritto.documents

object CodeFormatter {
    fun alignForDisplay(source: String, language: String?): String {
        if (source.isBlank()) return source

        val lines = source.replace("\r\n", "\n").split("\n")
        if (!shouldAutoAlign(lines)) return source.replace("\r\n", "\n")

        val normalized = when (language) {
            "Python" -> indentPython(lines)
            "Ruby" -> indentRuby(lines)
            "C", "C++", "Java", "Kotlin", "JavaScript", "JavaScript React",
            "TypeScript", "TypeScript React", "Go", "Rust", "Swift", "C#",
            "PHP", "Dart", "Scala", "Groovy", "Objective-C", "Objective-C++",
            "Vue", "Svelte" -> indentBraceLanguage(lines)
            else -> lines
        }
        return normalized.joinToString("\n")
    }

    private fun shouldAutoAlign(lines: List<String>): Boolean {
        val nonBlank = lines.filter { it.isNotBlank() }
        if (nonBlank.size < 2) return false

        val indented = nonBlank.count { it.firstOrNull()?.isWhitespace() == true }
        return indented == 0
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

    private fun indentPython(lines: List<String>): List<String> {
        var level = 0
        return lines.map { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@map ""

            val dedent = line.startsWith("else:") ||
                line.startsWith("elif ") ||
                line.startsWith("except") ||
                line.startsWith("finally:") ||
                line.startsWith("case ")

            if (dedent) level = (level - 1).coerceAtLeast(0)

            val result = "    ".repeat(level) + line
            if (line.endsWith(":") && !line.startsWith("#")) level++
            result
        }
    }

    private fun indentRuby(lines: List<String>): List<String> {
        var level = 0
        return lines.map { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@map ""

            val dedent = line == "end" ||
                line == "else" ||
                line == "elsif" ||
                line.startsWith("elsif ") ||
                line == "rescue" ||
                line.startsWith("rescue ") ||
                line == "ensure" ||
                line.startsWith("when ")

            if (dedent) level = (level - 1).coerceAtLeast(0)

            val result = "    ".repeat(level) + line

            val opens = line.matches(Regex("^(class|module|def|if|unless|case|begin|for|while|until)\\b.*")) ||
                line.endsWith(" do") || line.endsWith(" do |")
            if (opens && line != "end") level++

            // else / elsif / rescue / ensure / when close the previous branch but
            // keep the enclosing block open, so the body that follows indents again.
            if (dedent && line != "end") level++
            result
        }
    }
}
