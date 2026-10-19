package dev.xispana.safari

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

/** [player] is "You" for your own catch, or the teammate's name if their share completed it. */
data class BestRecord(val timeMs: Long, val player: String)

/**
 * Fastest-ever biome-complete time per biome, plus who completed it. Persisted to a small
 * JSON file under the Fabric config directory so it survives game restarts, not just this
 * session. Gson comes from Minecraft's own dependency graph - no extra library needed.
 */
object BiomeBests {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file = FabricLoader.getInstance().configDir.resolve("critter-safari-bests.json")
    private val allFile = FabricLoader.getInstance().configDir.resolve("critter-safari-bests-all.json")

    private val bests: MutableMap<Biome, BestRecord> = mutableMapOf()
    private var allBiomesBest: BestRecord? = null

    init {
        load()
        loadAll()
    }

    private fun load() {
        if (!Files.exists(file)) return
        runCatching {
            val text = Files.readString(file)
            val type = object : com.google.gson.reflect.TypeToken<Map<String, BestRecord>>() {}.type
            val raw: Map<String, BestRecord> = gson.fromJson(text, type) ?: return
            raw.forEach { (name, record) -> runCatching { Biome.valueOf(name) }.getOrNull()?.let { bests[it] = record } }
        }
    }

    private fun loadAll() {
        if (!Files.exists(allFile)) return
        runCatching {
            allBiomesBest = gson.fromJson(Files.readString(allFile), BestRecord::class.java)
        }
    }

    private fun save() {
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(bests.mapKeys { it.key.name }))
        }
    }

    private fun saveAll() {
        runCatching {
            Files.createDirectories(allFile.parent)
            Files.writeString(allFile, gson.toJson(allBiomesBest))
        }
    }

    fun best(biome: Biome): BestRecord? = bests[biome]

    /** Records a new best for [biome] if [timeMs] beats the existing one. Returns true if it did. */
    fun recordIfBest(biome: Biome, timeMs: Long, player: String): Boolean {
        val current = bests[biome]
        if (current != null && timeMs >= current.timeMs) return false
        bests[biome] = BestRecord(timeMs, player)
        save()
        return true
    }

    fun allBiomesBest(): BestRecord? = allBiomesBest

    /** Records a new best for completing all 4 biomes in one run. Returns true if it did. */
    fun recordAllBiomesIfBest(timeMs: Long, player: String): Boolean {
        val current = allBiomesBest
        if (current != null && timeMs >= current.timeMs) return false
        allBiomesBest = BestRecord(timeMs, player)
        saveAll()
        return true
    }
}
