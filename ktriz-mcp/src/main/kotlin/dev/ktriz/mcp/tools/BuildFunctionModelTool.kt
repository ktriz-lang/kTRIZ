package dev.ktriz.mcp.tools

import dev.ktriz.function.Component
import dev.ktriz.function.FunctionModel
import dev.ktriz.function.FunctionQuality
import dev.ktriz.function.functionModel
import dev.ktriz.mcp.ToolInputException
import dev.ktriz.mcp.echoSafe
import dev.ktriz.mcp.mcpToolCall
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val TOOL_NAME = "build_function_model"

// MAX_NAME_LENGTH, requireBoundedName, requireNonBlank, and String.kotlinStringLiteral() live in
// ToolSupport.kt -- shared across every tool in this package, not duplicated per file.

const val MAX_COMPONENTS = 200
const val MAX_EDGES = 500

private val VALID_QUALITIES = FunctionQuality.entries.joinToString(", ") { it.name }

private val INPUT_SCHEMA =
    ToolSchema(
        properties =
            buildJsonObject {
                putJsonObject("components") {
                    put("description", "Component names to declare up front, even if some appear in no edge.")
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                }
                putJsonObject("edges") {
                    put(
                        "description",
                        "Functional relationships: 'from' performs 'verb' on 'to', judged as 'quality' " +
                            "(one of $VALID_QUALITIES, case-insensitive). Endpoints not already listed in " +
                            "'components' are auto-declared.",
                    )
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("from") { put("type", "string") }
                            putJsonObject("to") { put("type", "string") }
                            putJsonObject("quality") { put("type", "string") }
                            putJsonObject("verb") { put("type", "string") }
                        }
                        putJsonArray("required") {
                            add("from")
                            add("to")
                            add("quality")
                            add("verb")
                        }
                    }
                }
            },
        required = emptyList(),
    )

private fun asNonBlankString(
    element: JsonElement?,
    field: String,
): String {
    val primitive = element as? JsonPrimitive
    if (primitive == null || !primitive.isString) {
        throw ToolInputException("each edge requires a string '$field' field")
    }
    requireNonBlank(field, primitive.content)
    return primitive.content
}

private fun parseComponentNames(element: JsonElement?): List<String> {
    if (element == null || element is JsonNull) return emptyList()
    val array = element as? JsonArray ?: throw ToolInputException("field 'components' must be an array of strings")
    if (array.size > MAX_COMPONENTS) {
        throw ToolInputException("field 'components' exceeds the maximum allowed item count of $MAX_COMPONENTS")
    }
    return array.map { item ->
        val primitive = item as? JsonPrimitive
        if (primitive == null || !primitive.isString) {
            throw ToolInputException("field 'components' must contain only strings")
        }
        requireNonBlank("components", primitive.content)
        requireBoundedName("components", primitive.content)
        primitive.content
    }
}

private data class RawEdge(
    val from: String,
    val to: String,
    val quality: FunctionQuality,
    val verb: String,
)

private fun parseEdges(element: JsonElement?): List<RawEdge> {
    if (element == null || element is JsonNull) return emptyList()
    val array = element as? JsonArray ?: throw ToolInputException("field 'edges' must be an array of objects")
    if (array.size > MAX_EDGES) {
        throw ToolInputException("field 'edges' exceeds the maximum allowed item count of $MAX_EDGES")
    }
    return array.map { item ->
        val obj = item as? JsonObject ?: throw ToolInputException("each entry in 'edges' must be an object")
        val from = asNonBlankString(obj["from"], "from")
        val to = asNonBlankString(obj["to"], "to")
        val verb = asNonBlankString(obj["verb"], "verb")
        val qualityRaw = asNonBlankString(obj["quality"], "quality")
        requireBoundedName("edges.from", from)
        requireBoundedName("edges.to", to)
        requireBoundedName("edges.verb", verb)
        val quality =
            FunctionQuality.entries.firstOrNull { it.name.equals(qualityRaw, ignoreCase = true) }
                ?: throw ToolInputException(
                    "field 'edges.quality' has invalid value '${echoSafe(qualityRaw)}' -- must be one of " +
                        VALID_QUALITIES,
                )
        RawEdge(from, to, quality, verb)
    }
}

/** Kotlin hard keywords -- syntactically illegal as a plain identifier (`val object = ...` does not
 *  parse). Soft/modifier keywords (`data`, `value`, `import`, ...) are deliberately excluded: they
 *  parse fine as identifiers in this position. Source: Kotlin grammar's `hardKeyword` production. */
private val KOTLIN_HARD_KEYWORDS =
    setOf(
        "as",
        "break",
        "class",
        "continue",
        "do",
        "else",
        "false",
        "for",
        "fun",
        "if",
        "in",
        "interface",
        "is",
        "null",
        "object",
        "package",
        "return",
        "super",
        "this",
        "throw",
        "true",
        "try",
        "typealias",
        "typeof",
        "val",
        "var",
        "when",
        "while",
    )

/** Generates a valid, unique Kotlin identifier from a component's display name, e.g. `"Cylinder block"` ->
 *  `cylinderBlock`. Falls back to `component`/`component2`/... for a name with no letters/digits at all.
 *  A result that collides with a Kotlin hard keyword (e.g. `"Object"` -> `object`) gets an underscore
 *  suffix, since `val object = ...` is not valid Kotlin. */
private fun kotlinIdentifier(
    name: String,
    used: MutableSet<String>,
): String {
    val words = name.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
    val base =
        if (words.isEmpty()) {
            "component"
        } else {
            val first = words.first().replaceFirstChar { it.lowercaseChar() }
            val rest = words.drop(1).joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }
            (first + rest).let { if (it.first().isDigit()) "c$it" else it }
        }.let { if (it in KOTLIN_HARD_KEYWORDS) "${it}_" else it }
    var candidate = base
    var suffix = 2
    while (!used.add(candidate)) {
        candidate = "$base$suffix"
        suffix++
    }
    return candidate
}

private fun functionModelKotlinSnippet(model: FunctionModel): String {
    val used = mutableSetOf<String>()
    val identifiers = model.components.associateWith { kotlinIdentifier(it.name, used) }
    val lines = mutableListOf("functionModel {")
    for (component in model.components) {
        lines += "    val ${identifiers.getValue(component)} = component(${component.name.kotlinStringLiteral()})"
    }
    for (edge in model.edges) {
        val functionName =
            when (edge.quality) {
                FunctionQuality.USEFUL -> "useful"
                FunctionQuality.HARMFUL -> "harmful"
                FunctionQuality.INSUFFICIENT -> "insufficient"
                FunctionQuality.EXCESSIVE -> "excessive"
            }
        val fromIdent = identifiers.getValue(edge.from)
        val toIdent = identifiers.getValue(edge.to)
        lines += "    $functionName(from = $fromIdent, to = $toIdent, verb = ${edge.verb.kotlinStringLiteral()})"
    }
    lines += "}"
    return lines.joinToString("\n")
}

private fun Component.toJson(): JsonObject = buildJsonObject { put("name", name) }

/** Pure handler -- directly unit-testable, no server/transport involved. */
fun buildFunctionModelHandler(arguments: JsonObject?): CallToolResult =
    mcpToolCall(TOOL_NAME) {
        val componentNames = parseComponentNames(arguments?.get("components"))
        val rawEdges = parseEdges(arguments?.get("edges"))

        // Endpoints not already in `componentNames` are auto-declared here via the same
        // component(name) idempotent-dedup call the DSL itself exposes -- not a separate
        // mechanism, so this handler's normalized `components` output is exactly what
        // functionModel { } would have produced from equivalent hand-written Kotlin.
        val model =
            functionModel {
                componentNames.forEach { component(it) }
                for (edge in rawEdges) {
                    val from = component(edge.from)
                    val to = component(edge.to)
                    when (edge.quality) {
                        FunctionQuality.USEFUL -> useful(from, to, edge.verb)
                        FunctionQuality.HARMFUL -> harmful(from, to, edge.verb)
                        FunctionQuality.INSUFFICIENT -> insufficient(from, to, edge.verb)
                        FunctionQuality.EXCESSIVE -> excessive(from, to, edge.verb)
                    }
                }
            }

        val summaryText =
            "Function model with ${model.components.size} component(s) and ${model.edges.size} edge(s)."
        CallToolResult(
            content = listOf(TextContent(text = summaryText)),
            structuredContent =
                buildJsonObject {
                    put("components", buildJsonArray { model.components.forEach { add(it.toJson()) } })
                    put(
                        "edges",
                        buildJsonArray {
                            model.edges.forEach { edge ->
                                add(
                                    buildJsonObject {
                                        put("from", edge.from.name)
                                        put("to", edge.to.name)
                                        put("quality", edge.quality.name)
                                        put("verb", edge.verb)
                                    },
                                )
                            }
                        },
                    )
                    put("kotlin", functionModelKotlinSnippet(model))
                },
        )
    }

fun registerBuildFunctionModelTool(server: Server) {
    server.addTool(
        name = TOOL_NAME,
        description =
            "Builds and validates a TRIZ function model (components + useful/harmful/insufficient/excessive " +
                "edges) without saving or rendering it -- returns the normalized model plus an equivalent, " +
                "directly compilable Kotlin `functionModel { }` snippet. Edge endpoints not already listed " +
                "in 'components' are auto-declared (same semantics as the functionModel DSL's own " +
                "component() call), so 'components' may be omitted entirely when every component already " +
                "appears in at least one edge. An empty model (no components, no edges) is a valid result, " +
                "not an error. Self-loops ('from' == 'to') are allowed -- classical TRIZ function analysis " +
                "explicitly models self-directed effects (e.g. a part overheating itself). 'quality' must " +
                "be one of $VALID_QUALITIES (case-insensitive); anything else is a structured malformed_input " +
                "error naming the valid values.",
        inputSchema = INPUT_SCHEMA,
    ) { request ->
        buildFunctionModelHandler(request.arguments)
    }
}
