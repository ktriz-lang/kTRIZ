package dev.ktriz.mcp.tools

import dev.ktriz.core.Contradiction
import dev.ktriz.core.ContradictionMatrix
import dev.ktriz.mcp.ParameterResolution
import dev.ktriz.mcp.mcpToolCall
import dev.ktriz.mcp.parameterResolutionErrors
import dev.ktriz.mcp.resolveParameter
import dev.ktriz.mcp.selfContradictionError
import dev.ktriz.mcp.toJson
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private const val TOOL_NAME = "resolve_contradiction"

private fun parameterPropertySchema(): JsonObject =
    buildJsonObject {
        put(
            "description",
            "An EngineeringParameter: its exact Kotlin enum symbol (e.g. \"STRENGTH\"), its English or " +
                "German label, or its 1..39 id.",
        )
        putJsonArray("anyOf") {
            add(buildJsonObject { put("type", "string") })
            add(buildJsonObject { put("type", "integer") })
        }
    }

private val INPUT_SCHEMA =
    ToolSchema(
        properties =
            buildJsonObject {
                put("improving", parameterPropertySchema())
                put("worsening", parameterPropertySchema())
            },
        required = listOf("improving", "worsening"),
    )

/** Pure handler -- directly unit-testable, no server/transport involved. */
fun resolveContradictionHandler(arguments: JsonObject?): CallToolResult =
    mcpToolCall(TOOL_NAME) {
        // Both fields are resolved before either error is reported (OF-5): a caller who gets
        // both wrong sees both problems in one round trip. See ParameterResolution and
        // McpToolError#parameterResolutionErrors for why.
        val improvingResolution = resolveParameter("improving", arguments?.get("improving"))
        val worseningResolution = resolveParameter("worsening", arguments?.get("worsening"))

        val problems =
            listOfNotNull(
                improvingResolution.takeUnless { it is ParameterResolution.Resolved },
                worseningResolution.takeUnless { it is ParameterResolution.Resolved },
            )
        if (problems.isNotEmpty()) {
            return@mcpToolCall parameterResolutionErrors(TOOL_NAME, problems)
        }

        val improving = (improvingResolution as ParameterResolution.Resolved).parameter
        val worsening = (worseningResolution as ParameterResolution.Resolved).parameter

        // Existence is already guaranteed by the resolutions above; the one remaining runtime
        // check ktriz-core performs is well-formedness (improving != worsening), enforced in
        // Contradiction's own init block. Constructing it directly here (rather than adding a
        // second, redundant improving == worsening check in this module) keeps that rule at
        // its one authoritative location and makes the "cannot contradict itself" test below
        // double as proof that ktriz-core's own check is the one actually firing.
        val contradiction =
            try {
                Contradiction(improving = improving, worsening = worsening)
            } catch (e: IllegalArgumentException) {
                return@mcpToolCall selfContradictionError(e.message)
            }

        val principles = ContradictionMatrix.lookup(contradiction)
        val kotlinSnippet =
            "contradiction(\n" +
                "    improving = EngineeringParameter.${improving.name},\n" +
                "    worsening = EngineeringParameter.${worsening.name},\n" +
                ")"

        val text =
            if (principles.isEmpty()) {
                "The classical matrix leaves this cell blank -- a legitimate 'no classical recommendation' " +
                    "result, not a failure."
            } else {
                "Recommended principles, in TRIZ rank order: " +
                    principles.joinToString(", ") { "#${it.id} ${it.name}" }
            }

        CallToolResult(
            content = listOf(TextContent(text = text)),
            structuredContent =
                buildJsonObject {
                    put("improving", improving.toJson())
                    put("worsening", worsening.toJson())
                    put("principleCount", principles.size)
                    put("empty", principles.isEmpty())
                    put(
                        "principles",
                        buildJsonArray {
                            principles.forEachIndexed { index, principle ->
                                add(principleWithRank(index + 1, principle.toJson()))
                            }
                        },
                    )
                    put("kotlin", kotlinSnippet)
                    put("provenance", ContradictionMatrix.provenance)
                },
        )
    }

/** [principleJson] plus a leading `rank` field (1-based position in the matrix cell's recommendation order). */
private fun principleWithRank(
    rank: Int,
    principleJson: JsonObject,
): JsonObject =
    buildJsonObject {
        put("rank", rank)
        for ((key, element) in principleJson) {
            put(key, element)
        }
    }

fun registerResolveContradictionTool(server: Server) {
    server.addTool(
        name = TOOL_NAME,
        description =
            "Looks up the classical Altshuller 39x39 contradiction matrix: improving `improving` makes " +
                "`worsening` worse -- returns the recommended inventive principles in TRIZ rank order, plus " +
                "a ready-to-use `contradiction(improving = ..., worsening = ...)` Kotlin snippet. `improving` " +
                "and `worsening` each accept an EngineeringParameter's exact Kotlin symbol, its English or " +
                "German label, or its 1..39 id -- if unsure of the exact symbol, call " +
                "list_engineering_parameters first rather than guessing. Direction matters: swapping " +
                "`improving` and `worsening` looks up a different, generally different-valued cell. Many " +
                "cells of the classical matrix are legitimately blank -- an empty `principles` list " +
                "(`empty: true`) is a valid, non-error result, not a failure to retry. The two parameters " +
                "must differ; passing the same parameter for both returns a structured self_contradiction " +
                "error instead of a lookup.",
        inputSchema = INPUT_SCHEMA,
    ) { request ->
        resolveContradictionHandler(request.arguments)
    }
}
