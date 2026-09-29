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
        "Ruby" to "alias|and|begin|break|case|class|def|defined|do|else|elsif|end|ensure|false|for|if|in|module|next|nil|not|or|redo|rescue|retry|return|self|super|then|true|undef|unless|until|when|while|yield",
        "SQL" to "add|all|alter|and|as|asc|begin|between|by|case|check|column|commit|constraint|create|delete|desc|distinct|drop|else|end|exists|from|full|group|having|if|in|index|inner|insert|into|is|join|left|like|limit|not|null|on|or|order|outer|primary|references|rename|right|rollback|select|set|table|then|union|unique|update|values|view|when|where|with",
        "HTML" to "doctype|html|head|title|meta|link|style|script|body|header|main|section|article|nav|footer|div|span|p|a|img|button|form|input|label|table|tr|td|th|ul|ol|li|h1|h2|h3|h4|h5|h6",
        "CSS" to "and|or|not|important|inherit|initial|unset|auto|none|block|inline|flex|grid|absolute|relative|fixed|sticky",
        "SCSS" to "and|or|not|important|inherit|initial|unset|auto|none|block|inline|flex|grid|absolute|relative|fixed|sticky",
        "YAML" to "true|false|null",
        "TOML" to "true|false",
        "GraphQL" to "query|mutation|subscription|fragment|on|schema|type|interface|union|enum|input|scalar|directive|extend|implements|repeatable",
        "Protocol Buffers" to "syntax|package|import|option|message|enum|service|rpc|returns|repeated|map|oneof|reserved|extend",
        "Haskell" to "case|class|data|default|deriving|do|else|foreign|if|import|in|infix|infixl|infixr|instance|let|module|newtype|of|then|type|where|qualified",
        "F#" to "abstract|and|as|assert|begin|class|default|delegate|do|done|downcast|elif|else|end|exception|extern|false|finally|for|fun|function|if|inherit|interface|internal|in|let|match|member|module|mutable|namespace|new|not|null|of|open|or|override|private|public|rec|return|static|struct|then|this|throw|to|true|try|type|upcast|use|val|void|when|while|with|yield",
        "Clojure" to "def|defn|fn|if|let|loop|recur|do|when|case|cond|for|doseq|defmacro|ns|require|use|import|new|try|catch|throw|finally|true|false|nil",
        "ClojureScript" to "def|defn|fn|if|let|loop|recur|do|when|case|cond|for|doseq|defmacro|ns|require|use|import|new|try|catch|throw|finally|true|false|nil",
        "Elixir" to "def|defp|defmodule|defmacro|defstruct|do|end|fn|case|cond|if|unless|else|for|with|receive|try|catch|rescue|after|raise|throw|alias|import|require|use|true|false|nil|when",
        "Erlang" to "after|begin|case|catch|cond|end|fun|if|let|of|receive|try|when|andalso|orelse|true|false",
        "Perl" to "my|our|local|sub|use|require|package|if|elsif|else|unless|while|until|for|foreach|given|when|default|continue|return|last|next|redo|die|eval|do|undef",
        "Julia" to "abstract|baremodule|begin|break|catch|const|continue|do|else|elseif|end|export|finally|for|function|global|if|import|let|local|macro|module|quote|return|struct|try|using|while|where|mutable|primitive|true|false|nothing",
        "Solidity" to "pragma|solidity|contract|interface|library|is|using|for|struct|enum|event|function|modifier|constructor|fallback|receive|returns|return|mapping|memory|storage|calldata|public|private|internal|external|view|pure|payable|virtual|override|abstract|import|if|else|while|do|for|break|continue|require|revert|assert|true|false",
        "Verilog" to "module|endmodule|input|output|inout|wire|reg|logic|always|assign|begin|end|if|else|case|endcase|for|while|parameter|localparam|generate|endgenerate|posedge|negedge|function|endfunction|task|endtask",
        "SystemVerilog" to "module|endmodule|interface|endinterface|input|output|inout|wire|logic|always|always_ff|always_comb|assign|begin|end|if|else|case|endcase|for|foreach|while|parameter|localparam|generate|endgenerate|posedge|negedge|class|endclass|function|endfunction|task|endtask|package|endpackage",
        "VHDL" to "architecture|begin|case|component|configuration|constant|downto|else|elsif|end|entity|file|for|function|generate|if|in|is|library|loop|map|of|on|others|out|package|port|process|range|record|report|return|signal|subtype|then|to|type|use|variable|wait|when|while|with|xnor|xor|and|or|not",
        "PowerShell" to "begin|break|catch|class|continue|data|define|do|dynamicparam|else|elseif|end|exit|filter|finally|for|foreach|from|function|if|in|param|process|return|switch|throw|trap|try|until|using|var|while|workflow",
        "Batch" to "call|cd|chcp|cls|copy|del|dir|echo|else|endlocal|exit|for|goto|if|md|move|pause|popd|pushd|rd|rem|ren|set|setlocal|shift|start|title|type|where|xcopy",
        "Dockerfile" to "from|as|run|cmd|entrypoint|copy|add|workdir|env|arg|expose|volume|user|label|healthcheck|shell|stopsignal|onbuild",
        "Makefile" to "if|ifdef|ifndef|ifeq|ifneq|else|endif|include|define|endef|override|export|unexport|private|vpath|value|eval|origin|flavor|foreach|call|if|error|warning|info",
        "Git" to "true|false"
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
        val tokenRegex = Regex("""//.*|--.*|/\\*[\\s\\S]*?\\*/|<!--[\\s\\S]*?-->|#.*|"(?:\\\\.|[^"\\\\])*"|'(?:\\\\.|[^'\\\\])*'|\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Z][A-Za-z0-9_]*\\b""")
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
                    value.startsWith("//") || value.startsWith("--") || value.startsWith("/*") || value.startsWith("<!--") || value.startsWith("#") ->
                        SpanStyle(color = commentColor)
                    value.startsWith("\"") || value.startsWith("'") -> SpanStyle(color = stringColor)
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
