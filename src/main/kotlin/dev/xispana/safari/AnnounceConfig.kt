package dev.xispana.safari

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

private data class AnnounceState(
    val enabled: Boolean = true,
    val completeToPartyChat: Boolean = true,
    val marks: Set<Int> = emptySet()
)

/**
 * Persists the /csafari announce configuration (the on/off toggle, the complete-to-partychat
 * toggle, and the scheduled second-marks) to a small JSON file under the Fabric config
 * directory. This used to live in memory only inside SafariTracker, which reset to empty
 * every game restart - your marks would silently vanish and "announce" would stop firing
 * with no error, no message, nothing to indicate why.
 */
object AnnounceConfig {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file = FabricLoader.getInstance().configDir.resolve("critter-safari-announce.json")

    var enabled: Boolean = true
        set(value) {
            field = value
            save()
        }

    var completeToPartyChat: Boolean = true
        set(value) {
            field = value
            save()
        }

    val marks: MutableSet<Int> = mutableSetOf()

    init {
        load()
    }

    private fun load() {
        if (!Files.exists(file)) return
        runCatching {
            val text = Files.readString(file)
            val state = gson.fromJson(text, AnnounceState::class.java) ?: return
            enabled = state.enabled
            completeToPartyChat = state.completeToPartyChat
            marks.addAll(state.marks)
        }
    }

    fun save() {
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(AnnounceState(enabled, completeToPartyChat, marks.toSet())))
        }
    }

    fun addMark(seconds: Int) {
        marks.add(seconds)
        save()
    }

    fun removeMark(seconds: Int) {
        marks.remove(seconds)
        save()
    }

    fun clearMarks() {
        marks.clear()
        save()
    }
}
