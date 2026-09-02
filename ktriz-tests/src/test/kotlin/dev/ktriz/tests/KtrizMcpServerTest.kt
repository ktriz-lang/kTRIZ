package dev.ktriz.tests

import dev.ktriz.mcp.buildServer
import dev.ktriz.mcp.runSession
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

// ChannelTransport (the SDK's own in-memory, no-subprocess client/server transport, from
// kotlin-sdk-testing) is `@ExperimentalMcpApi`; @OptIn is required on every spec/helper that
// touches it (M1 Welle 5 pitfall #4).
@OptIn(ExperimentalMcpApi::class)
private suspend fun connectedClient(server: Server): Client {
    val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()
    server.createSession(serverTransport)
    val client = Client(clientInfo = Implementation(name = "ktriz-mcp-test-client", version = "test"))
    client.connect(clientTransport)
    return client
}

private fun CallToolResult.errorKind(): String? = structuredContent?.get("errorKind")?.jsonPrimitive?.content

@OptIn(ExperimentalMcpApi::class)
class KtrizMcpServerTest :
    StringSpec({
        "tools/list lists exactly the seven registered tools, each with a description and an object schema" {
            val client = connectedClient(buildServer())
            val tools = client.listTools().tools

            tools.map { it.name } shouldContainExactlyInAnyOrder
                listOf(
                    "list_engineering_parameters",
                    "list_inventive_principles",
                    "resolve_contradiction",
                    "build_function_model",
                    "build_su_field",
                    "list_field_types",
                    "list_standard_solution_classes",
                )
            tools.forEach { tool ->
                tool.description.isNullOrBlank() shouldBe false
                tool.inputSchema.type shouldBe "object"
            }
        }

        "resolve_contradiction over a real client/server session returns the classical rank order" {
            val client = connectedClient(buildServer())
            val result =
                client.callTool(
                    "resolve_contradiction",
                    mapOf("improving" to "WEIGHT_OF_MOVING_OBJECT", "worsening" to "STRENGTH"),
                )
            result.isError shouldBe null
            val principleIds =
                result.structuredContent!!["principles"]!!
                    .jsonArray
                    .map { it.jsonObject["id"]!!.jsonPrimitive.int }
            principleIds shouldBe listOf(28, 27, 18, 40)
        }

        "an error tool call over a real session carries structuredContent.errorKind, never a raw exception" {
            val client = connectedClient(buildServer())
            val result =
                client.callTool(
                    "resolve_contradiction",
                    mapOf("improving" to "STRENGTH", "worsening" to "NOT_A_REAL_PARAMETER"),
                )
            result.isError shouldBe true
            result.errorKind() shouldBe "unknown_parameter"
        }

        "build_su_field over a real client/server session returns a structured model" {
            val client = connectedClient(buildServer())
            val result =
                client.callTool(
                    "build_su_field",
                    mapOf(
                        "s1" to "Workpiece",
                        "s2" to "Grinding wheel",
                        "fieldType" to "MECHANICAL",
                        "quality" to "INSUFFICIENT",
                    ),
                )
            result.isError shouldBe null
            val structured = result.structuredContent!!
            structured["s1"]!!.jsonObject["name"]!!.jsonPrimitive.content shouldBe "Workpiece"
            structured["fieldType"]!!.jsonObject["symbol"]!!.jsonPrimitive.content shouldBe "MECHANICAL"
            structured["quality"]!!.jsonPrimitive.content shouldBe "INSUFFICIENT"
        }

        "build_su_field over a real session surfaces self_contradiction for an inconsistent quality" {
            val client = connectedClient(buildServer())
            val result =
                client.callTool(
                    "build_su_field",
                    mapOf("s1" to "Workpiece", "quality" to "COMPLETE"),
                )
            result.isError shouldBe true
            result.errorKind() shouldBe "self_contradiction"
        }

        "calling an unknown tool name does not end the session -- a following valid call still succeeds" {
            val client = connectedClient(buildServer())
            // Either the SDK surfaces an unknown tool name as a protocol-error CallToolResult or
            // throws -- both are acceptable outcomes here; what this test actually pins is that
            // the session itself stays usable for a subsequent, valid call either way.
            runCatching { client.callTool("no_such_tool", emptyMap()) }

            val result = client.callTool("list_engineering_parameters", emptyMap())
            result.isError shouldBe null
        }

        // -- runSession lifecycle (regression coverage for the never-terminates bug, OF-1) ---
        //
        // `runStdioServer`/`main` themselves are not directly unit-testable -- they own real
        // stdin/stdout -- so this exercises the exact same session-lifecycle logic they delegate
        // to (`runSession`) over an in-memory `ChannelTransport` instead, wrapped in `withTimeout`:
        // before the OF-1 fix, the buggy version of this logic hung on `done.join()` forever, so a
        // regression here fails as a timeout, not a wrong assertion.
        //
        // OF-1's specific race (the session's transport closing so early that hooking
        // `ServerSession.onClose` *after* `createSession` returns can lose the callback entirely --
        // reproduced live via `ktriz-mcp < /dev/null`, see `KtrizMcpServer.kt`'s `runSession` doc
        // comment) is *not* pinned by this particular test: closing `ChannelTransport`'s client side
        // before the server session exists does not reproduce the same tight ordering
        // `StdioServerTransport` has -- the server-side reader coroutine never observes the close
        // and the fixed code legitimately keeps waiting, same as it would for a client that is
        // merely slow to send its first message. That race *is* pinned below, over a real
        // `StdioServerTransport`.

        "runSession returns once the client disconnects after a real round trip, not hanging" {
            val server = buildServer()
            val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()
            val client = Client(clientInfo = Implementation(name = "runsession-test-client", version = "test"))
            withTimeout(10_000L) {
                val sessionJob = launch { runSession(server, serverTransport) }
                // `launch` only schedules `runSession` -- it does not guarantee
                // `server.createSession(serverTransport)` (see KtrizMcpServer.kt) has actually run
                // before we proceed. Without waiting here, a client request can race ahead of the
                // server-side session wiring and get back a "Transport is not ready" protocol error
                // instead of a real response, under scheduling pressure (observed live under CPU
                // load). This poll is pure test synchronization -- it does not change what the
                // production `runSession` code considers authoritative for session lifecycle (see
                // its doc comment on why post-hoc `server.sessions` checks are unsound *there*); it
                // only delays this test's own client from connecting until a session is visible.
                while (server.sessions.isEmpty()) delay(1)
                client.connect(clientTransport)
                client.listTools() // a real request/response over the session before closing it
                client.close()
                sessionJob.join()
            }
        }

        // OF-1's actual race, pinned directly: a real `StdioServerTransport` whose input is already
        // at EOF races its own reader/processor/writer pumps against `Server.createSession`'s
        // internal bookkeeping (`sessionRegistry.add(session)` runs only *after*
        // `session.connect(transport)` returns) the same way the real `ktriz-mcp` binary does when
        // invoked with `< /dev/null` (measured ~50% hang rate before the fix -- see
        // `KtrizMcpServer.kt`'s `runSession` doc comment). `ChannelTransport` above cannot
        // reproduce this tight ordering, which is exactly why that race was previously only
        // verified manually against the built binary instead of in this suite.
        //
        // The fix (hooking `Transport.onClose` *before* calling `createSession`, instead of hooking
        // `ServerSession.onClose` or checking `server.sessions` membership afterwards) makes
        // `runSession` termination unconditional on this ordering, so this no longer needs to be
        // flaky to be a faithful regression test -- it should pass every time. Still repeated 20x:
        // a regression back to a post-hoc hook would only hang intermittently, not on every run, so
        // a single iteration could pass by luck and mask it.
        "runSession terminates for a real StdioServerTransport already at EOF (regression coverage for the OF-1 race)" {
            repeat(20) {
                val transport =
                    StdioServerTransport(
                        input = ByteArrayInputStream(ByteArray(0)).asSource().buffered(),
                        output = ByteArrayOutputStream().asSink().buffered(),
                    )
                withTimeout(5_000L) {
                    runSession(buildServer(), transport)
                }
            }
        }
    })
