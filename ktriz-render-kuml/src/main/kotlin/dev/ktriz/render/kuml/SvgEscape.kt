package dev.ktriz.render.kuml

/**
 * Drops every character XML 1.0 forbids outright -- U+0000..U+0008, U+000B, U+000C,
 * U+000E..U+001F, U+FFFE, U+FFFF, and unpaired surrogates -- which no escaping can rescue: a
 * document containing them does not parse at all, regardless of `&`/`<`/`>` handling. Runs
 * before the `&`/`<`/`>` replacement in [xmlEscapeText].
 *
 * Iterates by *code point*, not by `Char`, so a valid surrogate pair (e.g. an emoji) survives
 * intact -- only an *unpaired* high or low surrogate (which [displayTextFor]'s prefix-based
 * truncation can produce by cutting a name mid-pair) is dropped. `\t`/`\n`/`\r` (U+0009,
 * U+000A, U+000D) stay legal, matching the XML 1.0 spec's `Char` production.
 */
private fun stripXmlIllegal(s: String): String {
    val out = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        val charCount = Character.charCount(cp)
        val legal =
            when {
                cp in 0x00..0x08 -> false
                cp == 0x0B || cp == 0x0C -> false
                cp in 0x0E..0x1F -> false
                cp == 0xFFFE || cp == 0xFFFF -> false
                // A lone surrogate reached here means charCount == 1 for a code unit in the
                // surrogate range -- Character.charCount only returns 2 for an actual valid pair.
                Character.isSurrogate(s[i]) && charCount == 1 -> false
                else -> true
            }
        if (legal) out.appendCodePoint(cp)
        i += charCount
    }
    return out.toString()
}

/**
 * XML-escapes [s] for SVG *text content* (between opening/closing tags, e.g. inside
 * `<text>...</text>`). First drops any character [stripXmlIllegal] identifies as outright
 * illegal in XML 1.0, then escapes `&`, `<`, `>` in that order (order matters: escaping `&`
 * first is what prevents a later `<` replacement from re-matching the `&` just inserted for a
 * previous `<`). `"`/`'` are not special outside attribute values, so they are left alone.
 *
 * kTRIZ writes its own escaping rather than reusing kUML's -- kUML's equivalent lives in
 * `kuml-io-svg`, a module this project never depends on (see `FunctionModelSvgRenderer.kt`'s
 * KDoc on `renderSvg`), and is `internal` to that module regardless. This function is the
 * *only* place [dev.ktriz.function.Component.name] / [dev.ktriz.function.FunctionEdge.verb]
 * get escaped in this module -- callers must not pre-escape before passing text here, or the
 * output double-escapes (kUML learned this lesson the hard way; see this project's CLAUDE.md
 * for the `xmlEscapeText` history that motivates this note).
 */
internal fun xmlEscapeText(s: String): String =
    stripXmlIllegal(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** XML-escapes [s] for a double-quoted SVG attribute value. Adds `"` escaping on top of [xmlEscapeText]. */
internal fun xmlEscapeAttr(s: String): String = xmlEscapeText(s).replace("\"", "&quot;")

/**
 * Full name for a `<title>` tooltip, hard-capped so a multi-megabyte name is never emitted
 * whole -- the tooltip only exists because [displayTextFor] already truncated the *visible*
 * label, so a title this long would defeat its own purpose anyway. Uses [MAX_MEASURED_CHARS]
 * (the same cap [displayTextFor]'s binary search bounds itself to) as the character budget, so
 * one constant governs both "how much of a name we ever measure" and "how much of a name we
 * ever emit into a tooltip".
 */
internal fun titleTextFor(fullName: String): String =
    if (fullName.length > MAX_MEASURED_CHARS) fullName.take(MAX_MEASURED_CHARS) + "…" else fullName
