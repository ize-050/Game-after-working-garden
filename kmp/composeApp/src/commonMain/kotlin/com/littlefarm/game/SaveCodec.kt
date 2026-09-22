package com.littlefarm.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Versioned, validated save data. Platform storage is deliberately separate. */
object GameSaveCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(state: GameState): String = buildJsonObject {
        put("schemaVersion", SCHEMA_VERSION)
        put("coins", state.coins)
        put("xp", state.xp)
        put("seeds", cropMap(state.seeds))
        put("produce", cropMap(state.produce))
        put("plots", JsonArray(state.plots.map { plot ->
            buildJsonObject {
                put("stage", plot.stage.name)
                put("crop", plot.crop?.let { JsonPrimitive(it.name) } ?: JsonNull)
                put("readyAtMillis", plot.readyAtMillis?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        }))
        put("upgrades", buildJsonObject {
            put("expandedPlots", state.upgrades.expandedPlots)
            put("largeWateringCan", state.upgrades.largeWateringCan)
        })
        put("orderClaimed", state.orderClaimed)
        put("demoOffsetMillis", state.demoOffsetMillis)
        put("startedAtMillis", state.startedAtMillis?.let { JsonPrimitive(it) } ?: JsonNull)
    }.toString()

    fun decode(raw: String): GameState? = try {
        decodeObject(Json.parseToJsonElement(raw) as? JsonObject)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun decodeObject(root: JsonObject?): GameState? {
        root ?: return null
        if (root.int("schemaVersion") != SCHEMA_VERSION) return null
        val coins = root.int("coins")?.takeIf { it >= 0 } ?: return null
        val xp = root.int("xp")?.takeIf { it >= 0 } ?: return null
        val seeds = readCropMap(root["seeds"]) ?: return null
        val produce = readCropMap(root["produce"]) ?: return null
        val upgradesJson = root["upgrades"] as? JsonObject ?: return null
        val expanded = upgradesJson.boolean("expandedPlots") ?: return null
        val wateringCan = upgradesJson.boolean("largeWateringCan") ?: return null
        val orderClaimed = root.boolean("orderClaimed") ?: return null
        val demoOffset = root.long("demoOffsetMillis")?.takeIf { it >= 0 } ?: return null
        val startedAt = when (root["startedAtMillis"]) {
            null, JsonNull -> null // Backward-compatible optional metadata in save schema 1.
            else -> root.long("startedAtMillis")?.takeIf { it >= 0 } ?: return null
        }
        val plotsJson = root["plots"] as? JsonArray ?: return null
        if (plotsJson.size != if (expanded) 9 else 6) return null
        val plots = plotsJson.map { readPlot(it) ?: return null }
        return GameState(
            coins = coins,
            xp = xp,
            seeds = seeds,
            produce = produce,
            plots = plots,
            upgrades = Upgrades(expandedPlots = expanded, largeWateringCan = wateringCan),
            orderClaimed = orderClaimed,
            demoOffsetMillis = demoOffset,
            startedAtMillis = startedAt,
        )
    }

    private fun readPlot(element: JsonElement): Plot? {
        val obj = element as? JsonObject ?: return null
        val stageName = obj.string("stage") ?: return null
        val stage = PlotStage.entries.find { it.name == stageName } ?: return null
        val cropElement = obj["crop"] ?: return null
        val crop = if (cropElement == JsonNull) {
            null
        } else {
            val cropName = obj.string("crop") ?: return null
            CropType.entries.find { it.name == cropName } ?: return null
        }
        val timestampElement = obj["readyAtMillis"] ?: return null
        val timestamp = if (timestampElement == JsonNull) {
            null
        } else {
            obj.long("readyAtMillis")?.takeIf { it >= 0 } ?: return null
        }
        val validShape = when (stage) {
            PlotStage.UNTILLED, PlotStage.TILLED -> crop == null && timestamp == null
            PlotStage.PLANTED -> crop != null && timestamp == null
            PlotStage.GROWING -> crop != null && timestamp != null
        }
        if (!validShape) return null
        return Plot(stage = stage, crop = crop, readyAtMillis = timestamp)
    }

    private fun cropMap(counts: Map<CropType, Int>): JsonObject = buildJsonObject {
        CropType.entries.forEach { crop -> put(crop.name, counts[crop] ?: 0) }
    }

    private fun readCropMap(element: JsonElement?): Map<CropType, Int>? {
        val obj = element as? JsonObject ?: return null
        if (obj.keys != CropType.entries.map { it.name }.toSet()) return null
        return CropType.entries.associateWith { crop ->
            obj.int(crop.name)?.takeIf { it >= 0 } ?: return null
        }
    }

    private fun JsonObject.int(key: String): Int? =
        (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

    private fun JsonObject.long(key: String): Long? =
        (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
}
