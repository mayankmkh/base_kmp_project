package dev.mayankmkh.basekmpproject.convention.validation

/**
 * The 1-based lines of a Kotlin file's public top-level declarations -- those carrying neither
 * `private` nor `internal` -- each at its first modifier, or at its keyword when it has none.
 *
 * The file is lexed into tokens, with comments dropped and every string or character literal
 * (templates included) collapsed to one token, so a declaration is read the same however its
 * annotations, modifiers, comments and `;` are spread over lines. Only tokens outside every bracket
 * are examined: a member, or a declaration inside a call's arguments, is never top level.
 *
 * A declaration's `=` or `by` expression is followed to its end, so a value in it is never a
 * modifier: `private val pair = 1 to private` ends in a value named `private`, not in the prefix of
 * what comes next. An operator, or an infix function's name, leaves the expression waiting for an
 * operand, across a newline too; a value it was not waiting for, at the start of a line, begins the
 * next declaration, as it does for the compiler.
 *
 * Not the Kotlin PSI: it lives in `kotlin-compiler-embeddable` (about 58 MB), which build-logic
 * reaches only through KGP's runtime classpath, and it needs an IntelliJ core environment, which
 * detekt and ktfmt each load from a classpath of their own. A `.kt` file's top level holds nothing
 * but declarations, so tokens and bracket depth are enough to tell where each one starts.
 */
internal fun publicTopLevelDeclarations(text: String): List<Int> {
    val tokens = kotlinTokens(text)
    val result = mutableListOf<Int>()
    val modifiers = mutableListOf<Token>()
    var depth = 0
    var previous: Token? = null
    // Inside a top-level `=` or `by` expression, and whether it still waits for an operand.
    var initializer = false
    var awaitingOperand = false
    // The keyword of the declaration last started: a typealias's `=` is followed by a type.
    var declaration: Token? = null
    var i = 0
    while (i < tokens.size) {
        val token = tokens[i]
        val next = tokens.getOrNull(i + 1)
        if (depth == 0 && initializer && token.text !in OPENING_BRACKETS) {
            // What may follow a whole operand on a new line, but `else` or an operator, starts the
            // next declaration.
            val nextDeclaration =
                !awaitingOperand &&
                    token.line != previous?.line &&
                    (token.kind != TokenKind.SYMBOL || token.text == "@") &&
                    !token.expectsOperand()
            when {
                token.text == ";" || nextDeclaration -> initializer = false
                token.text == "@" -> {
                    // An annotated expression: the annotation leaves the operand still due.
                    i = afterAnnotation(tokens, i)
                    previous = tokens[i - 1]
                    continue
                }
                // A type, read whole: `x as List<*>?` and `object : Comparator<Int>` end in an
                // operand, never in a `<`, `>`, `*` or `?` waiting for one.
                token.isWord("as") || token.isWord("is") || token.text == ":" -> {
                    val type = if (token.isWord("as") && next?.text == "?") i + 2 else i + 1
                    i = afterType(tokens, type)
                    awaitingOperand = false
                    modifiers.clear()
                    previous = tokens[i - 1]
                    continue
                }
                // A call's type arguments (`listOf<Int>()`, `Foo<Bar>::class`), as the compiler
                // reads them: a type list closed by `>` and followed by what may follow a call.
                token.text == "<" &&
                    previous?.kind == TokenKind.WORD &&
                    tokens.getOrNull(typeArgumentsEnd(tokens, i) ?: -1)?.text in
                        TYPE_ARGUMENT_FOLLOWERS -> {
                    i = checkNotNull(typeArgumentsEnd(tokens, i))
                    awaitingOperand = false
                    modifiers.clear()
                    previous = tokens[i - 1]
                    continue
                }
                else -> {
                    awaitingOperand =
                        when {
                            token.expectsOperand() -> true
                            // Types are read whole above, so these are the binary operators.
                            token.text in BINARY_OUTSIDE_TYPES -> true
                            token.kind == TokenKind.SYMBOL -> awaitingOperand
                            // A name after a whole operand, on its line, is an infix call's.
                            else -> !awaitingOperand
                        }
                    modifiers.clear()
                    previous = token
                    i++
                    continue
                }
            }
        }
        // A soft keyword after `=`, `.` or an operator is a name in an expression, not a
        // modifier, and `fun`, `object` or `class` there is an anonymous function, an object
        // expression or `::class`.
        val inExpression = previous?.expectsOperand() == true
        when {
            token.text in OPENING_BRACKETS -> {
                if (depth == 0) modifiers.clear()
                depth++
            }
            token.text in CLOSING_BRACKETS -> {
                depth = maxOf(0, depth - 1)
                // A closed group is a whole operand.
                if (depth == 0) awaitingOperand = false
            }
            depth > 0 -> Unit
            token.text == "=" && declaration?.isWord("typealias") == true -> {
                // `typealias Pairing = Pair<Int, Int>`: its `<` and `>` are a type's, read whole.
                i = afterType(tokens, i + 1)
                declaration = null
                modifiers.clear()
                previous = tokens[i - 1]
                continue
            }
            // `by` delegates only in a property or a class's header; elsewhere -- an alias's name
            // or type parameter, `typealias by = List<Int>` -- it is a plain name.
            token.text == "=" ||
                (token.isWord("by") && declaration?.text in DELEGATING_DECLARATIONS) -> {
                initializer = true
                awaitingOperand = true
                modifiers.clear()
            }
            token.text == "@" -> {
                // Annotations sit among the modifiers without ending them.
                i = afterAnnotation(tokens, i)
                previous = tokens[i - 1]
                continue
            }
            !inExpression && token.isWord("context") && next?.text == "(" -> {
                i = afterGroup(tokens, i + 1)
                previous = tokens[i - 1]
                continue
            }
            !inExpression && token.isWord("fun") && next?.isWord("interface") == true ->
                modifiers += token
            !inExpression && token.kind == TokenKind.WORD && token.text in MODIFIERS ->
                modifiers += token
            !inExpression && startsDeclaration(tokens, i) -> {
                if (modifiers.none { it.text in HIDDEN }) {
                    result += (modifiers.firstOrNull() ?: token).line
                }
                declaration = token
                modifiers.clear()
            }
            else -> modifiers.clear()
        }
        previous = token
        i++
    }
    return result
}

private enum class TokenKind {
    WORD,
    LITERAL,
    SYMBOL,
}

private class Token(val text: String, val kind: TokenKind, val line: Int) {
    fun isWord(word: String): Boolean = kind == TokenKind.WORD && text == word

    fun expectsOperand(): Boolean =
        when (kind) {
            TokenKind.SYMBOL -> text in OPERAND_SYMBOLS
            TokenKind.WORD -> text in OPERAND_WORDS
            TokenKind.LITERAL -> false
        }
}

private val OPENING_BRACKETS = setOf("(", "[", "{")
private val CLOSING_BRACKETS = setOf(")", "]", "}")
private val HIDDEN = setOf("private", "internal")
private val MODIFIERS =
    setOf(
        "public",
        "private",
        "internal",
        "protected",
        "expect",
        "actual",
        "open",
        "abstract",
        "final",
        "sealed",
        "data",
        "enum",
        "annotation",
        "inner",
        "value",
        "const",
        "lateinit",
        "inline",
        "suspend",
        "operator",
        "infix",
        "tailrec",
        "external",
    )
private val DECLARATION_KEYWORDS =
    setOf("class", "interface", "object", "fun", "val", "var", "typealias")
private val CALLABLE_KEYWORDS = setOf("fun", "val", "var")
private val DELEGATING_DECLARATIONS = setOf("val", "var", "class", "object")
// `String?.f` lexes as `String`, `?.`, `f`.
private val RECEIVER_DOTS = setOf(".", "?.")
private val ANNOTATION_TARGETS =
    setOf(
        "file",
        "field",
        "property",
        "get",
        "set",
        "receiver",
        "param",
        "setparam",
        "delegate",
        "all",
    )

// `>`, `<`, `*` and `?` are left out: they also close or decorate a type (`List<Int>`, `Int?`), and
// a declaration may follow a type directly.
private val OPERAND_SYMBOLS =
    setOf(
        "=",
        "+=",
        "-=",
        "*=",
        "/=",
        "%=",
        ".",
        "?.",
        "::",
        ",",
        ":",
        "+",
        "-",
        "/",
        "%",
        "&&",
        "||",
        "!",
        "?:",
        "==",
        "!=",
        "===",
        "!==",
        "<=",
        ">=",
        "->",
        "..",
        "..<",
    )
// Left out of OPERAND_SYMBOLS for a declaration's types; in an expression, outside a type, they are
// multiplication and comparison.
private val BINARY_OUTSIDE_TYPES = setOf("*", "<", ">")
private val TYPE_ARGUMENT_SYMBOLS = setOf(".", ",", "?", "*", "->", "@", "<", ">", "(", ")")
private val TYPE_ARGUMENT_FOLLOWERS = setOf("(", "::", ".", "?.", "{")
private val OPERAND_WORDS = setOf("return", "throw", "in", "is", "as", "else")

// Longest first, so `?.` is never read as `?` then `.`.
private val SYMBOLS =
    listOf(
        "===",
        "!==",
        "..<",
        "?.",
        "?:",
        "::",
        "->",
        "==",
        "!=",
        "<=",
        ">=",
        "&&",
        "||",
        "+=",
        "-=",
        "*=",
        "/=",
        "%=",
        "++",
        "--",
        "..",
        "!!",
    )

/**
 * Whether the declaration keyword at [at] starts a named declaration: `class`, `interface`,
 * `object` or `typealias` followed by its name, or `fun`, `val` or `var` whose type parameters,
 * receiver type annotations and dotted receiver type -- names and parenthesised types, each with
 * type arguments and `?` -- end in a bare name. An anonymous function (`fun (x: Int) = x`, `fun
 * String.() = 1`, `fun (String).() = 1`) ends in its parameter list instead.
 */
private fun startsDeclaration(tokens: List<Token>, at: Int): Boolean {
    val keyword = tokens[at]
    if (!(keyword.kind == TokenKind.WORD && keyword.text in DECLARATION_KEYWORDS)) return false
    if (keyword.text !in CALLABLE_KEYWORDS) return tokens.getOrNull(at + 1)?.kind == TokenKind.WORD
    var j = afterTypeArguments(tokens, at + 1)
    while (tokens.getOrNull(j)?.text == "@") j = afterAnnotation(tokens, j)
    while (true) {
        val part = tokens.getOrNull(j) ?: return false
        j =
            when {
                part.kind == TokenKind.WORD -> afterTypeArguments(tokens, j + 1)
                part.text == "(" -> afterGroup(tokens, j)
                else -> return false
            }
        while (tokens.getOrNull(j)?.text == "?") j++
        if (tokens.getOrNull(j)?.text !in RECEIVER_DOTS) {
            return part.kind == TokenKind.WORD && tokens[j - 1] === part
        }
        j++
    }
}

/**
 * The index after the annotation whose `@` is at [at]: `@[...]`, or an optional use-site target, a
 * dotted name, type arguments and an argument list. Kotlin binds a `<` or `(` after the name across
 * any whitespace, comment or newline (`fun @A (String).f()` does not compile: `(String)` is `@A`'s
 * arguments), and no declaration starts with either.
 */
private fun afterAnnotation(tokens: List<Token>, at: Int): Int {
    var j = at + 1
    if (
        tokens.getOrNull(j + 1)?.text == ":" &&
            tokens.getOrNull(j)?.let {
                it.kind == TokenKind.WORD && it.text in ANNOTATION_TARGETS
            } == true
    ) {
        j += 2
    }
    if (tokens.getOrNull(j)?.text == "[") return afterGroup(tokens, j)
    if (tokens.getOrNull(j)?.kind != TokenKind.WORD) return j
    j++
    while (tokens.getOrNull(j)?.text == "." && tokens.getOrNull(j + 1)?.kind == TokenKind.WORD) {
        j += 2
    }
    j = afterTypeArguments(tokens, j)
    if (tokens.getOrNull(j)?.text == "(") j = afterGroup(tokens, j)
    return j
}

/**
 * The index after the type arguments whose `<` is at [at], or null when what follows is no type
 * list -- a comparison, `a < b && c > d`, holds a symbol no type does.
 */
private fun typeArgumentsEnd(tokens: List<Token>, at: Int): Int? {
    var angles = 0
    var parentheses = 0
    for (j in at until tokens.size) {
        val token = tokens[j]
        if (token.kind != TokenKind.WORD && token.text !in TYPE_ARGUMENT_SYMBOLS) return null
        when (token.text) {
            "<" -> angles++
            ">" -> if (--angles == 0 && parentheses == 0) return j + 1
            "(" -> parentheses++
            ")" -> if (--parentheses < 0) return null
        }
    }
    return null
}

/**
 * The index after the type that starts at [at]: annotations and `suspend`, then a dotted name with
 * type arguments, a parenthesised type or a function type's parameters, or `*`; any `?`; and a
 * function type's `-> ...`, receiver (`A.() -> B`) included.
 */
private fun afterType(tokens: List<Token>, at: Int): Int {
    var j = at
    while (tokens.getOrNull(j)?.text == "@") j = afterAnnotation(tokens, j)
    if (tokens.getOrNull(j)?.isWord("suspend") == true) j++
    val start = tokens.getOrNull(j) ?: return j
    j =
        when {
            start.text == "(" -> afterGroup(tokens, j)
            start.text == "*" -> j + 1
            start.kind == TokenKind.WORD -> {
                var k = afterTypeArguments(tokens, j + 1)
                while (
                    tokens.getOrNull(k)?.text == "." &&
                        tokens.getOrNull(k + 1)?.kind == TokenKind.WORD
                ) {
                    k = afterTypeArguments(tokens, k + 2)
                }
                k
            }
            else -> return j
        }
    while (tokens.getOrNull(j)?.text == "?") j++
    if (tokens.getOrNull(j)?.text == "." && tokens.getOrNull(j + 1)?.text == "(") {
        j = afterGroup(tokens, j + 1)
    }
    return if (tokens.getOrNull(j)?.text == "->") afterType(tokens, j + 1) else j
}

/** The index after the `<...>` that opens at [at], or [at] when none does. */
private fun afterTypeArguments(tokens: List<Token>, at: Int): Int {
    if (tokens.getOrNull(at)?.text != "<") return at
    var angles = 0
    var j = at
    do {
        when (tokens[j].text) {
            "<" -> angles++
            ">" -> angles--
        }
        j++
    } while (angles > 0 && j < tokens.size)
    return j
}

/** The index after the bracket group that opens at [open]. */
private fun afterGroup(tokens: List<Token>, open: Int): Int {
    var depth = 0
    var j = open
    do {
        when (tokens[j].text) {
            in OPENING_BRACKETS -> depth++
            in CLOSING_BRACKETS -> depth--
        }
        j++
    } while (depth > 0 && j < tokens.size)
    return j
}

private fun kotlinTokens(text: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var line = 1
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val start = i
        val kind: TokenKind
        when {
            c.isWhitespace() || text.startsWith("//", i) || text.startsWith("/*", i) -> {
                i =
                    when {
                        c.isWhitespace() -> i + 1
                        text.startsWith("//", i) ->
                            text.indexOf('\n', i).let { if (it < 0) text.length else it }
                        else -> afterBlockComment(text, i)
                    }
                line += text.substring(start, i).count { it == '\n' }
                continue
            }
            text.startsWith("\"\"\"", i) -> {
                i = afterString(text, i + 3, raw = true)
                kind = TokenKind.LITERAL
            }
            c == '"' -> {
                i = afterString(text, i + 1, raw = false)
                kind = TokenKind.LITERAL
            }
            c == '\'' -> {
                i = afterCharLiteral(text, i)
                kind = TokenKind.LITERAL
            }
            c == '`' -> {
                i = text.indexOf('`', i + 1).let { if (it < 0) text.length else it + 1 }
                kind = TokenKind.WORD
            }
            c.isLetter() || c == '_' -> {
                while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                kind = TokenKind.WORD
            }
            c.isDigit() -> {
                while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                kind = TokenKind.LITERAL
            }
            else -> {
                i += SYMBOLS.firstOrNull { text.startsWith(it, i) }?.length ?: 1
                kind = TokenKind.SYMBOL
            }
        }
        // A backtick name is a WORD but never equals a keyword: its text keeps the backticks.
        tokens += Token(text.substring(start, i), kind, line)
        line += text.substring(start, i).count { it == '\n' }
    }
    return tokens
}

/** The index after the block comment opening at [from]; Kotlin block comments nest. */
private fun afterBlockComment(text: String, from: Int): Int {
    var depth = 0
    var i = from
    while (i < text.length) {
        when {
            text.startsWith("/*", i) -> {
                depth++
                i += 2
            }
            text.startsWith("*/", i) -> {
                depth--
                i += 2
                if (depth == 0) return i
            }
            else -> i++
        }
    }
    return text.length
}

/** The index after the string whose content starts at [from], skipping `${...}` templates. */
private fun afterString(text: String, from: Int, raw: Boolean): Int {
    var i = from
    while (i < text.length) {
        when {
            raw && text.startsWith("\"\"\"", i) -> {
                // Quotes beyond the closing three belong to the content: `"""a""""`.
                i += 3
                while (i < text.length && text[i] == '"') i++
                return i
            }
            !raw && text[i] == '"' -> return i + 1
            !raw && text[i] == '\\' -> i += 2
            !raw && text[i] == '\n' -> return i
            text.startsWith("\${", i) -> i = afterTemplate(text, i + 2)
            else -> i++
        }
    }
    return text.length
}

/** The index after the `}` closing a template expression whose code starts at [from]. */
private fun afterTemplate(text: String, from: Int): Int {
    var depth = 1
    var i = from
    while (i < text.length) {
        when {
            text.startsWith("//", i) ->
                i = text.indexOf('\n', i).let { if (it < 0) text.length else it }
            text.startsWith("/*", i) -> i = afterBlockComment(text, i)
            text.startsWith("\"\"\"", i) -> i = afterString(text, i + 3, raw = true)
            text[i] == '"' -> i = afterString(text, i + 1, raw = false)
            text[i] == '\'' -> i = afterCharLiteral(text, i)
            text[i] == '{' -> {
                depth++
                i++
            }
            text[i] == '}' -> {
                depth--
                i++
                if (depth == 0) return i
            }
            else -> i++
        }
    }
    return text.length
}

/** The index after the character literal opening at [from]. */
private fun afterCharLiteral(text: String, from: Int): Int {
    var i = from + 1
    while (i < text.length && text[i] != '\'' && text[i] != '\n') i += if (text[i] == '\\') 2 else 1
    return minOf(i + 1, text.length)
}
