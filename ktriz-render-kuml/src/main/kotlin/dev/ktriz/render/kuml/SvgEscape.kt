package dev.ktriz.render.kuml

/**
 * XML-escapes [s] for SVG *text content* (between opening/closing tags, e.g. inside
 * `<text>...</text>`). Escapes `&`, `<`, `>` in that order (order matters: escaping `&` first
 * is what prevents a later `<` replacement from re-matching the `&` just inserted for a
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
internal fun xmlEscapeText(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** XML-escapes [s] for a double-quoted SVG attribute value. Adds `"` escaping on top of [xmlEscapeText]. */
internal fun xmlEscapeAttr(s: String): String = xmlEscapeText(s).replace("\"", "&quot;")
