package dev.ktriz.mcp.tools

import dev.ktriz.mcp.ToolInputException
import dev.ktriz.mcp.echoSafe
import dev.ktriz.mcp.mcpToolCall
import dev.ktriz.mcp.requireBoundedString
import dev.ktriz.mcp.toJson
import dev.ktriz.mcp.unknownStandardSolutionError
import dev.ktriz.sufield.StandardSolution
import dev.ktriz.sufield.StandardSolutionClass
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val TOOL_NAME = "lookup_standard_solution"

private val VALID_CLASS_SYMBOLS = StandardSolutionClass.entries.joinToString(", ") { it.name }

private val INPUT_SCHEMA =
    ToolSchema(
        properties =
            buildJsonObject {
                putJsonObject("code") {
                    put("description", "Dot-numbered code of one standard solution, e.g. \"1.1.1\" or \"5.1.1\".")
                    put("type", "string")
                }
                putJsonObject("standardClass") {
                    put(
                        "description",
                        "Class 1..5 (integer or digit string) or its symbol ($VALID_CLASS_SYMBOLS, " +
                            "case-insensitive): returns every solution of that class.",
                    )
                }
            },
        required = emptyList(),
    )

private fun StandardSolution.toLine(): String = "$code  $name — $labelEn"

private fun resolveClass(element: JsonElement): StandardSolutionClass {
    val primitive =
        element as? JsonPrimitive
            ?: throw ToolInputException("field 'standardClass' must be an integer 1..5 or a class symbol")
    val resolved =
        if (primitive.isString) {
            val raw = primitive.content.trim()
            requireBoundedString("standardClass", raw)
            raw.toIntOrNull()?.let { id -> StandardSolutionClass.entries.firstOrNull { it.id == id } }
                ?: StandardSolutionClass.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        } else {
            primitive.intOrNull?.let { id -> StandardSolutionClass.entries.firstOrNull { it.id == id } }
        }
    return resolved
        ?: throw ToolInputException(
            "field 'standardClass' has invalid value '${echoSafe(primitive.content)}' -- valid: 1..5 or " +
                VALID_CLASS_SYMBOLS,
        )
}

/**
 * Pure handler -- directly unit-testable, no server/transport involved.
 *
 * Deliberately avoids [StandardSolution]`.ofCode`-style throwing lookups: their error messages
 * embed the raw caller value. Lookup is `entries.firstOrNull`, and unknown values are reported
 * through [echoSafe].
 */
fun lookupStandardSolutionHandler(arguments: JsonObject?): CallToolResult =
    mcpToolCall(TOOL_NAME) {
        val codeElement = arguments?.get("code")?.takeUnless { it is JsonNull }
        val classElement = arguments?.get("standardClass")?.takeUnless { it is JsonNull }

        if (codeElement != null && classElement != null) {
            throw ToolInputException("fields 'code' and 'standardClass' are mutually exclusive")
        }

        if (codeElement != null) {
            val primitive = codeElement as? JsonPrimitive
            if (primitive == null || !primitive.isString) throw ToolInputException("field 'code' must be a string")
            requireBoundedString("code", primitive.content)
            val code = primitive.content.trim()
            val solution =
                StandardSolution.entries.firstOrNull { it.code == code }
                    ?: return@mcpToolCall unknownStandardSolutionError(code)
            return@mcpToolCall CallToolResult(
                content = listOf(TextContent(text = solution.toLine())),
                structuredContent =
                    buildJsonObject {
                        put("count", 1)
                        put("solution", solution.toJson())
                    },
            )
        }

        val selectedClass = classElement?.let { resolveClass(it) }
        val solutions =
            StandardSolution.entries
                .filter { selectedClass == null || it.standardClass == selectedClass }
        CallToolResult(
            content = listOf(TextContent(text = solutions.joinToString("\n") { it.toLine() })),
            structuredContent =
                buildJsonObject {
                    put("count", solutions.size)
                    selectedClass?.let { put("standardClass", it.toJson()) }
                    put("solutions", buildJsonArray { solutions.forEach { add(it.toJson()) } })
                },
        )
    }

fun registerLookupStandardSolutionTool(server: Server) {
    server.addTool(
        name = TOOL_NAME,
        description =
            "Looks up Altshuller's 76 TRIZ standard solutions for Su-Field analysis. Pass 'code' (e.g. " +
                "\"1.1.1\") for one solution, or 'standardClass' (1..5 or its symbol, see " +
                "list_standard_solution_classes) for all solutions of a class; the two are mutually " +
                "exclusive. With neither, all 76 are returned grouped by class. Returns code, enum " +
                "symbol, owning class and the English statement. Source: the 76 statements come from a " +
                "single source (Terninko, Domb & Miller, 'The Seventy-Six Standard Solutions, with " +
                "Examples', TRIZ Journal 2000), which the authors call their own interpretation of " +
                "Altshuller-era material -- see docs/standard-solutions-provenance.adoc. Code 5.1.1 is " +
                "the one entry that compresses nine sub-items into a single semicolon-joined statement. " +
                "There is deliberately NO mapping from a Su-Field model or SuFieldQuality to a solution: " +
                "choosing the applicable solution is a human judgement this tool never guesses. An " +
                "unknown code is a structured unknown_standard_solution error listing all valid codes. " +
                "Unrecognized arguments are ignored.",
        inputSchema = INPUT_SCHEMA,
    ) { request ->
        lookupStandardSolutionHandler(request.arguments)
    }
}
