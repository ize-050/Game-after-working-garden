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
    private const val SCHEMA_VERSION = 2
    private val legacyCrops = setOf(CropType.LETTUCE, CropType.RADISH, CropType.CARROT, CropType.PUMPKIN)

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
        put("completedOrders", state.completedOrders)
        put("harvestCounts", cropMap(state.harvestCounts))
        put("ownedDecor", JsonArray(state.ownedDecor.sortedBy { it.ordinal }.map { JsonPrimitive(it.name) }))
        put("equippedDecor", buildJsonObject {
            state.equippedDecor.entries.sortedBy { it.key.ordinal }.forEach { (slot, decor) -> put(slot.name, decor.name) }
        })
        put("cat", buildJsonObject {
            put("name", state.cat.name)
            put("bond", state.cat.bond)
            put("lastPettedAtMillis", state.cat.lastPettedAtMillis?.let { JsonPrimitive(it) } ?: JsonNull)
        })
    }.toString()

    fun decode(raw: String): GameState? = try {
        decodeObject(Json.parseToJsonElement(raw) as? JsonObject)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun decodeObject(root: JsonObject?): GameState? {
        root ?: return null
        val schema = root.int("schemaVersion") ?: return null
        if (schema !in 1..SCHEMA_VERSION) return null
        val coins = root.int("coins")?.takeIf { it >= 0 } ?: return null
        val xp = root.int("xp")?.takeIf { it >= 0 } ?: return null
        val seeds = readCropMap(root["seeds"], legacy = schema == 1) ?: return null
        val produce = readCropMap(root["produce"], legacy = schema == 1) ?: return null
        val upgradesJson = root["upgrades"] as? JsonObject ?: return null
        val expanded = upgradesJson.boolean("expandedPlots") ?: return null
        val wateringCan = upgradesJson.boolean("largeWateringCan") ?: return null
        val orderClaimed = root.boolean("orderClaimed") ?: return null
        val completedOrders = if (schema == 1) {
            if (orderClaimed) 1 else 0
        } else {
            root.int("completedOrders")?.takeIf { it >= 0 && orderClaimed == (it > 0) } ?: return null
        }
        val harvestCounts = if (schema == 1) CropType.entries.associateWith { 0 }
            else readCropMap(root["harvestCounts"]) ?: return null
        val ownedDecor = if (schema == 1) emptySet() else readOwnedDecor(root["ownedDecor"]) ?: return null
        val equippedDecor = if (schema == 1) emptyMap()
            else readEquippedDecor(root["equippedDecor"], ownedDecor) ?: return null
        val cat = if (schema == 1) CatState() else readCat(root["cat"]) ?: return null
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
            completedOrders = completedOrders,
            harvestCounts = harvestCounts,
            ownedDecor = ownedDecor,
            equippedDecor = equippedDecor,
            cat = cat,
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

    private fun readCropMap(element: JsonElement?, legacy: Boolean = false): Map<CropType, Int>? {
        val obj = element as? JsonObject ?: return null
        val knownNames = CropType.entries.map { it.name }.toSet()
        if (legacy) {
            if (!obj.keys.containsAll(legacyCrops.map { it.name }) || !knownNames.containsAll(obj.keys)) return null
        } else if (obj.keys != knownNames) return null
        return CropType.entries.associateWith { crop ->
            if (legacy && crop.name !in obj) 0 else obj.int(crop.name)?.takeIf { it >= 0 } ?: return null
        }
    }

    private fun readOwnedDecor(element: JsonElement?): Set<Decoration>? {
        val array = element as? JsonArray ?: return null
        val items = array.map { value ->
            val name = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            Decoration.entries.find { it.name == name } ?: return null
        }
        if (items.size != items.toSet().size) return null
        return items.toSet()
    }

    private fun readEquippedDecor(element: JsonElement?, owned: Set<Decoration>): Map<DecorationSlot, Decoration>? {
        val obj = element as? JsonObject ?: return null
        return obj.entries.associate { (key, _) ->
            val slot = DecorationSlot.entries.find { it.name == key } ?: return null
            val name = obj.string(key) ?: return null
            val decor = Decoration.entries.find { it.name == name } ?: return null
            if (decor.slot != slot || decor !in owned) return null
            slot to decor
        }
    }

    private fun readCat(element: JsonElement?): CatState? {
        val obj = element as? JsonObject ?: return null
        val name = obj.string("name")?.takeIf { CatState.isValidName(it) } ?: return null
        val bond = obj.int("bond")?.takeIf { it >= 0 } ?: return null
        val last = when (obj["lastPettedAtMillis"] ?: return null) {
            JsonNull -> null
            else -> obj.long("lastPettedAtMillis")?.takeIf { it >= 0 } ?: return null
        }
        return CatState(name, bond, last)
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
