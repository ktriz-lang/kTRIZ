package dev.ktriz.mcp

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration

/**
 * Every `KLogger` in this module must be created through this function, never through a bare
 * `KotlinLogging.logger { }` call.
 *
 * kotlin-logging prints a one-time `"kotlin-logging: initializing... active logger factory: ..."`
 * banner straight to `System.out` -- unconditionally, bypassing SLF4J entirely -- the very first
 * time `KotlinLogging.logger(...)` is called anywhere in the process (gated only by
 * [KotlinLoggingConfiguration.logStartupMessage], which defaults to `true`). For a stdio MCP
 * server, `System.out` *is* the JSON-RPC transport (see [KtrizMcpServer]'s KDoc): that banner
 * landing on stdout ahead of the first real message would corrupt the protocol stream for
 * whichever real MCP client connects first -- the exact "stdout poisoning" failure mode this
 * module's tests specifically guard against ([KtrizMcpLoggingTest]).
 *
 * [SuppressStartupBanner.applied] flips [KotlinLoggingConfiguration.logStartupMessage] to `false`
 * exactly once, the first time *any* file in this module asks for a logger -- by
 * [lazy]'s default `SYNCHRONIZED` mode, this is safe regardless of which of this module's several
 * top-level `logger` properties happens to initialize first (each lives in a different file/JVM
 * class, so there is no reliable single "first" file to special-case instead).
 */
internal fun ktrizMcpLogger(name: () -> Unit): KLogger {
    SuppressStartupBanner.applied
    return KotlinLogging.logger(name)
}

private object SuppressStartupBanner {
    val applied: Boolean by lazy {
        KotlinLoggingConfiguration.logStartupMessage = false
        true
    }
}
