package com.littlefarm.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SaveCodecTest {
    private val now = 1_000_000L
    private val initial = GameState.initial(now)

    private fun root(): JsonObject = Json.parseToJsonElement(GameSaveCodec.encode(initial)) as JsonObject

    private fun decodeWith(key: String, value: JsonElement): GameState? =
        GameSaveCodec.decode(JsonObject(root() + (key to value)).toString())

    private fun decodePlot(index: Int, changes: Map<String, JsonElement>): GameState? {
        val base = root()
        val plots = (base["plots"] as JsonArray).toMutableList()
        plots[index] = JsonObject((plots[index] as JsonObject) + changes)
        return GameSaveCodec.decode(JsonObject(base + ("plots" to JsonArray(plots))).toString())
    }

    private fun GameResult.success(): GameState = assertIs<GameResult.Success>(this).state

    @Test
    fun initialStateRoundTripsWithoutLosingAnyFields() {
        val encoded = GameSaveCodec.encode(initial)
        assertEquals(initial, GameSaveCodec.decode(encoded))
        assertEquals(JsonPrimitive(1), (Json.parseToJsonElement(encoded) as JsonObject)["schemaVersion"])
    }

    @Test
    fun progressedStateRoundTripsInventoryOrderUpgradeAndActiveTimers() {
        var state = GameEngine.water(initial, 3, now).success()
        state = GameEngine.advanceDemoTime(state).success()
        for (index in listOf(3, 4, 5)) state = GameEngine.harvest(state, index, now).success()
        state = GameEngine.fulfillOrder(state).success()
        state = GameEngine.sellProduce(state, CropType.RADISH).success()
        state = GameEngine.buyUpgrade(state, UpgradeType.EXTRA_PLOTS).success()
        state = GameEngine.buySeeds(state, CropType.LETTUCE, 2).success()
        state = GameEngine.plant(state, 2, CropType.LETTUCE).success()
        state = GameEngine.water(state, 2, now).success()

        val decoded = assertNotNull(GameSaveCodec.decode(GameSaveCodec.encode(state)))
        assertEquals(state, decoded)
        assertEquals(3, decoded.coins)
        assertEquals(20, decoded.xp)
        assertEquals(9, decoded.plots.size)
        assertEquals(now + 420_000L, decoded.plots[2].readyAtMillis)
        assertEquals(300_000L, decoded.demoOffsetMillis)
    }

    @Test
    fun bothUpgradeFlagsAndEmptyCropMapsRoundTrip() {
        val state = initial.copy(
            coins = 0,
            seeds = CropType.entries.associateWith { 0 },
            produce = CropType.entries.associateWith { 0 },
            plots = List(9) { Plot() },
            upgrades = Upgrades(expandedPlots = true, largeWateringCan = true),
        )
        assertEquals(state, GameSaveCodec.decode(GameSaveCodec.encode(state)))
    }

    @Test
    fun malformedOrNonObjectJsonReturnsNull() {
        for (raw in listOf("", "{", "not json", "null", "[]", "123", "\"a save\"", "{\"coins\":}")) {
            assertNull(GameSaveCodec.decode(raw), raw)
        }
    }

    @Test
    fun unknownSchemaAndMissingRequiredFieldsAreRejected() {
        assertNull(decodeWith("schemaVersion", JsonPrimitive(2)))
        assertNull(decodeWith("schemaVersion", JsonPrimitive(0)))
        for (key in root().keys - "startedAtMillis") {
            assertNull(GameSaveCodec.decode(JsonObject(root() - key).toString()), key)
        }
    }

    @Test
    fun optionalFarmStartIsBackwardCompatibleAndValidated() {
        assertEquals(initial.copy(startedAtMillis = null), GameSaveCodec.decode(JsonObject(root() - "startedAtMillis").toString()))
        assertEquals(initial.copy(startedAtMillis = null), decodeWith("startedAtMillis", JsonNull))
        assertNull(decodeWith("startedAtMillis", JsonPrimitive(-1)))
        assertNull(decodeWith("startedAtMillis", JsonPrimitive("1000")))
        assertNull(decodeWith("startedAtMillis", JsonPrimitive(true)))
    }

    @Test
    fun negativeCountersAndOverflowingNumbersAreRejected() {
        for (key in listOf("coins", "xp", "demoOffsetMillis")) {
            assertNull(decodeWith(key, JsonPrimitive(-1)), key)
        }
        assertNull(decodeWith("coins", JsonPrimitive(Int.MAX_VALUE.toLong() + 1)))
        assertNull(decodeWith("xp", JsonPrimitive(Int.MAX_VALUE.toLong() + 1)))
        assertNull(GameSaveCodec.decode(GameSaveCodec.encode(initial).replace(
            "\"demoOffsetMillis\":0", "\"demoOffsetMillis\":9223372036854775808",
        )))
    }

    @Test
    fun primitiveTypesAreNotSilentlyCoerced() {
        assertNull(decodeWith("schemaVersion", JsonPrimitive("1")))
        assertNull(decodeWith("coins", JsonPrimitive("120")))
        assertNull(decodeWith("xp", JsonPrimitive(1.5)))
        assertNull(decodeWith("orderClaimed", JsonPrimitive("false")))
        assertNull(decodeWith("orderClaimed", JsonPrimitive(0)))
        assertNull(decodeWith("demoOffsetMillis", JsonPrimitive("0")))
        assertNull(decodeWith("upgrades", JsonArray(emptyList())))
        assertNull(decodeWith("plots", JsonObject(emptyMap())))
    }

    @Test
    fun cropMapsRequireAllKnownCropsAndNonnegativeIntegerCounts() {
        for (mapName in listOf("seeds", "produce")) {
            val counts = root()[mapName] as JsonObject
            assertNull(decodeWith(mapName, JsonObject(counts + ("LETTUCE" to JsonPrimitive(-1)))))
            assertNull(decodeWith(mapName, JsonObject(counts + ("LETTUCE" to JsonPrimitive("3")))))
            assertNull(decodeWith(mapName, JsonObject(counts + ("POTATO" to JsonPrimitive(1)))))
            assertNull(decodeWith(mapName, JsonObject(counts - "CARROT")))
            assertNull(decodeWith(mapName, JsonNull))
        }
    }

    @Test
    fun unknownCropAndStageNamesAreRejected() {
        assertNull(decodePlot(3, mapOf("crop" to JsonPrimitive("POTATO"))))
        assertNull(decodePlot(3, mapOf("crop" to JsonPrimitive("lettuce"))))
        assertNull(decodePlot(3, mapOf("stage" to JsonPrimitive("READY"))))
        assertNull(decodePlot(3, mapOf("stage" to JsonPrimitive(3))))
    }

    @Test
    fun everyPlotStageHasAnEnforcedCropAndTimestampShape() {
        for (index in listOf(0, 2)) {
            assertNull(decodePlot(index, mapOf("crop" to JsonPrimitive("LETTUCE"))))
            assertNull(decodePlot(index, mapOf("readyAtMillis" to JsonPrimitive(now))))
        }
        assertNull(decodePlot(3, mapOf("crop" to JsonNull)))
        assertNull(decodePlot(3, mapOf("readyAtMillis" to JsonPrimitive(now))))
        assertNull(decodePlot(4, mapOf("crop" to JsonNull)))
        assertNull(decodePlot(4, mapOf("readyAtMillis" to JsonNull)))
        assertNull(decodePlot(4, mapOf("readyAtMillis" to JsonPrimitive(-1))))
        assertNull(decodePlot(4, mapOf("readyAtMillis" to JsonPrimitive("120000"))))
        assertNotNull(decodePlot(4, mapOf("readyAtMillis" to JsonPrimitive(0))))
    }

    @Test
    fun plotCountMustMatchExpansionFlagExactly() {
        val plots = root()["plots"] as JsonArray
        assertNull(decodeWith("plots", JsonArray(plots.dropLast(1))))
        assertNull(decodeWith("plots", JsonArray(plots + plots.take(1))))
        assertNull(decodeWith("plots", JsonArray(plots + plots.take(3))))
        val upgrades = root()["upgrades"] as JsonObject
        assertNull(decodeWith("upgrades", JsonObject(upgrades + ("expandedPlots" to JsonPrimitive(true)))))
    }

    @Test
    fun missingNestedFieldsAndNonObjectPlotEntriesAreRejected() {
        val base = root()
        val originalPlots = base["plots"] as JsonArray
        for (key in listOf("stage", "crop", "readyAtMillis")) {
            val plots = originalPlots.toMutableList()
            plots[4] = JsonObject((plots[4] as JsonObject) - key)
            assertNull(decodeWith("plots", JsonArray(plots)), key)
        }
        assertNull(decodeWith("plots", JsonArray(originalPlots.toMutableList().apply { this[0] = JsonNull })))
        val upgrades = base["upgrades"] as JsonObject
        for (key in listOf("expandedPlots", "largeWateringCan")) {
            assertNull(decodeWith("upgrades", JsonObject(upgrades - key)), key)
            assertNull(decodeWith("upgrades", JsonObject(upgrades + (key to JsonPrimitive("true")))), key)
        }
    }
}
