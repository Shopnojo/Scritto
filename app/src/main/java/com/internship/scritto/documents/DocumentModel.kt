package com.internship.scritto.documents

enum class DocumentKind { TEXT, MARKDOWN, JSON, XML, CODE, CSV, XLSX, DOCX, PDF, IMAGE, UNKNOWN }

data class DocumentCapabilities(
    val canEdit: Boolean,
    val canConvert: Boolean,
    val canShare: Boolean = true
)

data class DocumentDescriptor(
    val name: String,
    val uri: String,
    val kind: DocumentKind,
    val language: String? = null,
    val capabilities: DocumentCapabilities
)

object DocumentTypes {
    private val codeLanguages = mapOf(
        "c" to "C", "h" to "C", "cc" to "C++", "cpp" to "C++", "cxx" to "C++",
        "hpp" to "C++", "java" to "Java", "kt" to "Kotlin", "kts" to "Kotlin",
        "py" to "Python", "js" to "JavaScript", "jsx" to "JavaScript React",
        "ts" to "TypeScript", "tsx" to "TypeScript React", "html" to "HTML",
        "htm" to "HTML", "css" to "CSS", "scss" to "SCSS", "sass" to "Sass",
        "sql" to "SQL", "rs" to "Rust", "go" to "Go", "php" to "PHP",
        "swift" to "Swift", "dart" to "Dart", "rb" to "Ruby", "sh" to "Shell",
        "bash" to "Shell", "zsh" to "Shell", "cs" to "C#", "scala" to "Scala",
        "groovy" to "Groovy", "gradle" to "Gradle", "lua" to "Lua", "r" to "R",
        "m" to "Objective-C", "mm" to "Objective-C++", "vue" to "Vue",
        "svelte" to "Svelte", "asm" to "Assembly", "s" to "Assembly", "yml" to "YAML",
        "yaml" to "YAML", "toml" to "TOML", "ini" to "INI", "cfg" to "INI",
        "proto" to "Protocol Buffers", "graphql" to "GraphQL", "gql" to "GraphQL",
        "hs" to "Haskell", "lhs" to "Haskell", "fs" to "F#", "fsx" to "F#",
        "clj" to "Clojure", "cljs" to "ClojureScript", "ex" to "Elixir", "exs" to "Elixir",
        "erl" to "Erlang", "hrl" to "Erlang", "pl" to "Perl", "pm" to "Perl",
        "rkt" to "Racket", "jl" to "Julia", "sol" to "Solidity", "v" to "Verilog",
        "sv" to "SystemVerilog", "vhd" to "VHDL", "vhdl" to "VHDL", "ps1" to "PowerShell",
        "bat" to "Batch", "cmd" to "Batch"
    )

    fun conversionTargets(kind: DocumentKind): List<String> = when (kind) {
        DocumentKind.CSV -> listOf("xlsx", "txt")
        DocumentKind.XLSX -> listOf("csv", "txt")
        DocumentKind.DOCX -> listOf("pdf", "txt")
        DocumentKind.PDF -> listOf("docx", "txt")
        DocumentKind.IMAGE -> listOf("png", "jpg", "webp")
        else -> emptyList()
    }

    fun mimeTypeForExtension(extension: String): String = when (extension.lowercase()) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "csv" -> "text/csv"
        "txt" -> "text/plain"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "*/*"
    }

    fun describe(name: String, uri: String): DocumentDescriptor {
        val extension = name.substringAfterLast('.', "").lowercase()
        val specialName = name.substringAfterLast('/').lowercase()
        val kind = when {
            specialName == "dockerfile" -> DocumentKind.CODE
            specialName == "makefile" -> DocumentKind.CODE
            specialName == ".gitignore" || specialName == ".gitattributes" -> DocumentKind.CODE
            else -> when (extension) {
                "txt", "log" -> DocumentKind.TEXT
                "md", "markdown" -> DocumentKind.MARKDOWN
                "json" -> DocumentKind.JSON
                "xml" -> DocumentKind.XML
                "csv", "tsv" -> DocumentKind.CSV
                "xlsx", "xlsm" -> DocumentKind.XLSX
                "docx" -> DocumentKind.DOCX
                "pdf" -> DocumentKind.PDF
                "jpg", "jpeg", "png", "webp", "gif", "bmp", "ico", "svg" -> DocumentKind.IMAGE
                else -> if (codeLanguages.containsKey(extension)) DocumentKind.CODE else DocumentKind.UNKNOWN
            }
        }
        val editable = kind in setOf(
            DocumentKind.TEXT, DocumentKind.CSV, DocumentKind.XLSX, DocumentKind.DOCX,
            DocumentKind.PDF, DocumentKind.IMAGE
        )
        return DocumentDescriptor(
            name = name,
            uri = uri,
            kind = kind,
            language = when {
                specialName == "dockerfile" -> "Dockerfile"
                specialName == "makefile" -> "Makefile"
                specialName == ".gitignore" || specialName == ".gitattributes" -> "Git"
                else -> codeLanguages[extension]
            },
            capabilities = DocumentCapabilities(
                canEdit = editable,
                canConvert = conversionTargets(kind).isNotEmpty()
            )
        )
    }
}
