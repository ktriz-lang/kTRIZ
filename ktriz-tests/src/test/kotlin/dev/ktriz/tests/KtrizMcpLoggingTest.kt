package dev.ktriz.tests

import dev.ktriz.mcp.buildServer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import java.io.ByteArrayOutputStream
import java.io.PrintStream

@OptIn(ExperimentalMcpApi::class)
private suspend fun connectedClient(server: Server): Client {
    val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()
    server.createSession(serverTransport)
    val client = Client(clientInfo = Implementation(name = "ktriz-mcp-logging-test-client", version = "test"))
    client.connect(clientTransport)
    return client
}

// slf4j-simple's zero-config output choice re-reads System.err on every call instead of caching
// it at class-init time, so this swap genuinely captures one call's output (same technique kSTEP
// uses in its own McpLoggingTest -- see that file's comment for the OutputChoice-caching pitfall
// a `simplelogger.properties` file would silently reintroduce; ktriz-mcp deliberately ships none).
@OptIn(ExperimentalMcpApi::class)
class KtrizMcpLoggingTest :
    StringSpec({
        "a structured tool error is logged via SLF4J on stderr, never on stdout, without the raw caller value" {
            val originalOut = System.out
            val originalErr = System.err
            val capturedOut = ByteArrayOutputStream()
            val capturedErr = ByteArrayOutputStream()
            System.setOut(PrintStream(capturedOut, true, Charsets.UTF_8))
            System.setErr(PrintStream(capturedErr, true, Charsets.UTF_8))
            try {
                val client = connectedClient(buildServer())
                client.callTool(
                    "resolve_contradiction",
                    mapOf("improving" to "STRENGTH", "worsening" to "STRUCTURAL_INTEGRITY"),
                )
            } finally {
                System.setOut(originalOut)
                System.setErr(originalErr)
            }

            val err = capturedErr.toString(Charsets.UTF_8)
            err shouldContain "resolve_contradiction"
            err shouldContain "unknown_parameter"
            err shouldNotContain "STRUCTURAL_INTEGRITY"

            // stdio protocol purity: nothing this tool call does may write to stdout -- stdout
            // *is* the JSON-RPC stream a real MCP client reads. ChannelTransport doesn't route
            // through real System.in/out, so this assertion is specifically about this module's
            // own code path (no println, no misdirected logger output), not the transport itself.
            capturedOut.toString(Charsets.UTF_8) shouldBe ""
        }

        "a build_su_field self_contradiction error is logged via SLF4J on stderr, never on stdout" {
            val originalOut = System.out
            val originalErr = System.err
            val capturedOut = ByteArrayOutputStream()
            val capturedErr = ByteArrayOutputStream()
            System.setOut(PrintStream(capturedOut, true, Charsets.UTF_8))
            System.setErr(PrintStream(capturedErr, true, Charsets.UTF_8))
            try {
                val client = connectedClient(buildServer())
                client.callTool(
                    "build_su_field",
                    mapOf("s1" to "Workpiece", "quality" to "COMPLETE"),
                )
            } finally {
                System.setOut(originalOut)
                System.setErr(originalErr)
            }

            val err = capturedErr.toString(Charsets.UTF_8)
            err shouldContain "build_su_field"
            err shouldContain "self_contradiction"

            capturedOut.toString(Charsets.UTF_8) shouldBe ""
        }
    })
