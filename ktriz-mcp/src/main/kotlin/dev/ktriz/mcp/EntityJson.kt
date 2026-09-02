package dev.ktriz.mcp

import dev.ktriz.core.EngineeringParameter
import dev.ktriz.core.InventivePrinciple
import dev.ktriz.sufield.FieldType
import dev.ktriz.sufield.StandardSolutionClass
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Compact `{id, symbol, labelEn, labelDe}` representation shared by every MCP tool response that
 * surfaces an [EngineeringParameter]. `symbol` is always the raw Kotlin enum member name -- the
 * exact identifier a caller must use verbatim as `EngineeringParameter.<symbol>` for generated
 * Kotlin code to compile. This is the Typed Domain Grounding few-shot catalogue described in the
 * vault note "kTRIZ - DSL-Surface (Hello World)", section 5: `list_engineering_parameters`
 * exposes it so a caller never has to guess a symbol from its own knowledge of TRIZ literature.
 */
fun EngineeringParameter.toJson(): JsonObject =
    buildJsonObject {
        put("id", id)
        put("symbol", name)
        put("labelEn", labelEn)
        put("labelDe", labelDe)
    }

/** Same shape as [EngineeringParameter.toJson], for [InventivePrinciple]. */
fun InventivePrinciple.toJson(): JsonObject =
    buildJsonObject {
        put("id", id)
        put("symbol", name)
        put("labelEn", labelEn)
        put("labelDe", labelDe)
    }

/** Same shape as [EngineeringParameter.toJson], plus `abbreviation`, for [FieldType]. */
fun FieldType.toJson(): JsonObject =
    buildJsonObject {
        put("id", id)
        put("symbol", name)
        put("labelEn", labelEn)
        put("labelDe", labelDe)
        put("abbreviation", abbreviation)
    }

/** Same shape as [EngineeringParameter.toJson], plus `description` and `solutionCount`, for
 *  [StandardSolutionClass]. */
fun StandardSolutionClass.toJson(): JsonObject =
    buildJsonObject {
        put("id", id)
        put("symbol", name)
        put("labelEn", labelEn)
        put("labelDe", labelDe)
        put("description", description)
        put("solutionCount", solutionCount)
    }
