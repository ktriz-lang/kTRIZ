package dev.ktriz.mcp

import dev.ktriz.mcp.tools.registerBuildFunctionModelTool
import dev.ktriz.mcp.tools.registerListEngineeringParametersTool
import dev.ktriz.mcp.tools.registerListInventivePrinciplesTool
import dev.ktriz.mcp.tools.registerResolveContradictionTool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

private val logger = ktrizMcpLogger {}

/**
 * Assembles the kTRIZ MCP server: `list_engineering_parameters`, `list_inventive_principles`,
 * `resolve_contradiction`, and `build_function_model`.
 *
 * Deliberately stateless -- unlike kSTEP's `kstep-mcp`, there is no `EntityStore` parameter here.
 * Every tool in this module is a pure function over [dev.ktriz.core.EngineeringParameter.entries],
 * [dev.ktriz.core.InventivePrinciple.entries], and the immutable [dev.ktriz.core.ContradictionMatrix]
 * singleton -- no tool call here ever depends on, or mutates, anything another call produced. This
 * is also the strongest possible security position: no cross-session state, no capacity limits to
 * enforce, no concurrent-write races to reason about.
 */
fun buildServer(): Server {
    val server =
        Server(
            serverInfo = Implementation(name = "ktriz-mcp", version = "0.1.0"),
            options = ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools())),
        )
    registerListEngineeringParametersTool(server)
    registerListInventivePrinciplesTool(server)
    registerResolveContradictionTool(server)
    registerBuildFunctionModelTool(server)
    logger.info { "kTRIZ MCP server assembled: 4 tools registered" }
    return server
}

/**
 * Runs [server] over [transport] until the session closes. `Server.createSession` returns as soon
 * as the transport is connected -- its reader/processor/writer coroutines already run in the
 * background -- so without the `Job()`/`onClose`/`join()` idiom below, this function (and the
 * process, for [runStdioServer] called from `main`) would return before a single message is ever
 * read.
 *
 * `createSession` registers its own internal `session.onClose { ... }` (unsubscribe + registry
 * cleanup) *before* it calls `session.connect(transport)` -- and `connect()` can run the whole
 * read-loop-to-EOF-to-close sequence synchronously before `createSession` ever returns to us
 * (verified via `javap` against kotlin-sdk 0.14.0: a client that closes stdin immediately, e.g.
 * `ktriz-mcp < /dev/null`, has already closed and deregistered its session by the time this
 * function gets the `ServerSession` back -- also reproduced live against the built binary, both
 * with an immediately-closed stdin and with a full handshake followed by stdin EOF). Registering
 * a `session.onClose { }` *after* `createSession` returns races that same close: whichever thread
 * runs the read-loop-to-EOF synchronously inside `connect()` can already have driven `close()` to
 * completion by the time we get here, and a callback added after a close chain already ran simply
 * never fires -- `server.sessions` membership is not a reliable substitute either, because
 * `createSession` calls `sessionRegistry.add(session)` only *after* `session.connect(transport)`
 * returns, so in that same race the registry is still empty when our post-hoc "is it still
 * present" check runs, the check finds nothing to flag, and `createSession` adds the already-dead
 * session right afterwards -- `done.complete()` never gets called and `done.join()` hangs forever
 * (measured against the built binary: ~50% of runs hang past a 20s timeout, both for
 * `ktriz-mcp < /dev/null` and for a full handshake followed by stdin EOF; `Server.onClose { }`
 * does not help either, since per the same bytecode inspection it is wired only to an explicit,
 * whole-server `Server.close()` we never call).
 *
 * The fix is to never race the close at all: [Transport.onClose] is on the public `Transport`
 * interface (not just `ServerSession`), and `AbstractTransport.onClose` -- the implementation
 * every SDK transport (including `StdioServerTransport` and the SDK's own `ChannelTransport`)
 * derives from -- *composes* onto the transport's existing `_onClose` callback rather than
 * replacing it (verified via `javap`: `onClose(block)` reads `_onClose`, wraps it in a lambda that
 * invokes the old callback then `block`, and writes that back), and a transport's `close()` can
 * only run once its `AbstractTransport.onCloseCalled` `CompareAndSet` flag flips, which cannot
 * happen before we get here because the read loop that would trigger it only starts once
 * `session.connect(transport)` runs *inside* `server.createSession(transport)`, i.e. strictly
 * after this function has already returned from registering below. So: hook `transport.onClose { }`
 * -- not `session.onClose { }` -- and do it *before* calling `createSession`, while we still
 * exclusively hold the transport and nothing has had a chance to close it yet. That ordering makes
 * the race structurally impossible rather than papering over one arm of it.
 *
 * Exposed as a `Transport`-parameterized function (rather than folded directly into
 * [runStdioServer]) so tests can drive this exact session-lifecycle logic via an in-memory
 * `ChannelTransport` instead of real stdin/stdout. Public (not `internal`) because `ktriz-tests`
 * is a separate Gradle module, where `internal` would not be visible.
 */
suspend fun runSession(
    server: Server,
    transport: Transport,
) {
    val done = Job()
    transport.onClose {
        logger.info { "kTRIZ MCP server session closed" }
        done.complete()
    }
    server.createSession(transport)
    done.join()
}

/**
 * Runs [server] over stdio until the session closes -- see [runSession] for the lifecycle/race
 * details this delegates to.
 *
 * Deliberately does *not* wrap `System.\`in\`"`/`System.out` in a `use {}` block or otherwise close
 * them: they are the process's own standard streams, not a resource this function owns. Closing
 * them would look like a resource-leak fix but would actually kill the server's only transport
 * mid-session. The session's lifecycle is already handled correctly via `onClose`/`Job.complete()`.
 */
suspend fun runStdioServer(server: Server = buildServer()) {
    val transport =
        StdioServerTransport(
            input = System.`in`.asSource().buffered(),
            output = System.out.asSink().buffered(),
        )
    logger.info { "kTRIZ MCP server starting stdio session" }
    runSession(server, transport)
}

// Process entry point for `./ktriz-mcp/build/install/ktriz-mcp/bin/ktriz-mcp` (built via
// `./gradlew :ktriz-mcp:installDist`) -- see README, "MCP server", for why real MCP clients must
// use that startup path rather than `./gradlew :ktriz-mcp:run` (Gradle's own console output
// shares stdout with the JSON-RPC stream this process speaks).
fun main() =
    runBlocking {
        runStdioServer()
    }
