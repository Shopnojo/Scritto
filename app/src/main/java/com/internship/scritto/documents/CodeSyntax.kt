package com.internship.scritto.documents

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

object CodeSyntax {
    private val keywords = mapOf(
        "Python" to "and|as|assert|async|await|break|case|class|continue|def|del|elif|else|except|finally|for|from|global|if|import|in|is|lambda|match|nonlocal|not|or|pass|raise|return|try|while|with|yield",
        "Java" to "abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|super|switch|synchronized|this|throw|throws|try|void|volatile|while",
        "Kotlin" to "as|break|class|continue|do|else|false|for|fun|if|in|interface|is|null|object|package|private|protected|public|return|super|this|throw|true|try|val|var|when|while|by|catch|constructor|data|enum|final|inline|internal|open|operator|override|sealed|suspend|typealias|where",
        "C" to "auto|break|case|char|const|continue|default|do|double|else|enum|extern|float|for|goto|if|inline|int|long|register|return|short|signed|sizeof|static|struct|switch|typedef|union|unsigned|void|volatile|while",
        "C++" to "alignas|alignof|and|asm|auto|bool|break|case|catch|char|class|const|constexpr|continue|default|delete|do|double|else|enum|explicit|export|extern|false|float|for|friend|if|inline|int|long|namespace|new|nullptr|operator|private|protected|public|return|short|signed|sizeof|static|struct|switch|template|this|throw|true|try|typedef|typename|union|unsigned|using|virtual|void|volatile|while",
        "JavaScript" to "as|async|await|break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|false|finally|for|from|function|get|if|import|in|instanceof|let|new|null|of|return|set|static|super|switch|this|throw|true|try|typeof|var|void|while|with|yield",
        "TypeScript" to "as|async|await|break|case|catch|class|const|continue|declare|default|delete|do|else|enum|export|extends|false|finally|for|from|function|if|implements|import|interface|in|instanceof|keyof|let|new|null|of|private|protected|public|readonly|return|static|string|super|switch|this|throw|true|type|typeof|undefined|var|void|while",
        "Go" to "break|default|func|interface|select|case|defer|go|map|struct|chan|else|goto|package|switch|const|fallthrough|if|range|type|continue|for|import|return|var",
        "Rust" to "as|async|await|break|const|continue|crate|dyn|else|enum|extern|false|fn|for|if|impl|in|let|loop|match|mod|move|mut|pub|ref|return|self|Self|static|struct|super|trait|true|type|unsafe|use|where|while",
        "Swift" to "class|defer|enum|extension|fileprivate|func|import|init|internal|let|open|operator|private|protocol|public|return|static|struct|typealias|var|break|case|continue|default|else|for|guard|if|in|repeat|switch|while|as|Any|catch|false|is|nil|super|throw|throws|true|try",
        "C#" to "abstract|as|base|bool|break|byte|case|catch|char|checked|class|const|continue|decimal|default|delegate|do|double|else|enum|event|explicit|extern|false|finally|float|for|foreach|goto|if|implicit|in|int|interface|internal|is|lock|long|namespace|new|null|object|operator|out|override|params|private|protected|public|readonly|ref|return|sealed|short|sizeof|static|string|struct|switch|this|throw|true|try|typeof|uint|ulong|unchecked|unsafe|ushort|using|virtual|void|volatile|while",
        "PHP" to "abstract|and|array|as|break|case|catch|class|const|continue|declare|default|do|echo|else|elseif|empty|endfor|endforeach|endif|endswitch|endwhile|extends|final|finally|for|foreach|function|global|if|implements|include|instanceof|interface|isset|list|match|namespace|new|or|private|protected|public|require|return|static|switch|throw|trait|try|unset|use|var|while|yield",
        "Ruby" to "alias|and|begin|break|case|class|def|defined|do|else|elsif|end|ensure|false|for|if|in|module|next|nil|not|or|redo|rescue|retry|return|self|super|then|true|undef|unless|until|when|while|yield"
    )

    fun highlight(
        source: String,
        language: String?,
        keywordColor: Color,
        stringColor: Color,
        commentColor: Color,
        numberColor: Color,
        typeColor: Color,
        plainColor: Color
    ): AnnotatedString {
        val keywordRegex = keywords[language]?.let { Regex("\\b(?:$it)\\b") }
        val tokenRegex = Regex("""//.*|/\\*[\\s\\S]*?\\*/|#.*|"(?:\\\\.|[^"\\\\])*"|'(?:\\\\.|[^'\\\\])*'|\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Z][A-Za-z0-9_]*\\b""")
        return buildAnnotatedString {
            var index = 0
            while (index < source.length) {
                val token = tokenRegex.find(source, index)
                val keyword = keywordRegex?.find(source, index)
                val match = when {
                    token == null && keyword == null -> null
                    token == null -> keyword
                    keyword == null -> token
                    token.range.first <= keyword.range.first -> token
                    else -> keyword
                }
                if (match == null) {
                    withStyle(SpanStyle(color = plainColor)) { append(source.substring(index)) }
                    break
                }
                if (match.range.first > index) {
                    withStyle(SpanStyle(color = plainColor)) { append(source.substring(index, match.range.first)) }
                }
                val value = match.value
                val style = when {
                    value.startsWith("//") || value.startsWith("/*") || value.startsWith("#") ->
                        SpanStyle(color = commentColor)
                    value.startsWith(""") || value.startsWith("'") -> SpanStyle(color = stringColor)
                    value.firstOrNull()?.isDigit() == true -> SpanStyle(color = numberColor)
                    keywordRegex?.matches(value) == true -> SpanStyle(color = keywordColor)
                    value.firstOrNull()?.isUpperCase() == true -> SpanStyle(color = typeColor)
                    else -> SpanStyle(color = plainColor)
                }
                withStyle(style) { append(value) }
                index = match.range.last + 1
            }
        }
    }
}
