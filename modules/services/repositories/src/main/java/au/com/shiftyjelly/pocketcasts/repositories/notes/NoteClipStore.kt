package au.com.shiftyjelly.pocketcasts.repositories.notes

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import timber.log.Timber

/**
 * Saved clips, one JSON file each in the app's private files. Kept out of the Room database so the
 * upstream schema and its migrations stay untouched.
 */
class NoteClipStore private constructor(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "notes/clips")
    private val lock = Any()
    private val _clips = MutableStateFlow(loadAll())

    val clips: StateFlow<List<NoteClip>> = _clips.asStateFlow()

    fun get(id: String): NoteClip? = _clips.value.firstOrNull { it.id == id }

    fun save(clip: NoteClip) {
        synchronized(lock) {
            directory.mkdirs()
            val file = fileFor(clip.id)
            val temp = File(directory, "${clip.id}.json.tmp")
            temp.writeText(clip.toJson().toString())
            if (!temp.renameTo(file)) {
                file.writeText(clip.toJson().toString())
                temp.delete()
            }
            _clips.value = (_clips.value.filterNot { it.id == clip.id } + clip).sortedWith(clipOrder)
        }
    }

    fun update(id: String, transform: (NoteClip) -> NoteClip): NoteClip? {
        synchronized(lock) {
            val current = get(id) ?: return null
            val updated = transform(current)
            save(updated)
            return updated
        }
    }

    fun delete(id: String) {
        synchronized(lock) {
            fileFor(id).delete()
            _clips.value = _clips.value.filterNot { it.id == id }
        }
    }

    private fun fileFor(id: String) = File(directory, "$id.json")

    private fun loadAll(): List<NoteClip> {
        val files = directory.listFiles { file -> file.extension == "json" } ?: return emptyList()
        return files.mapNotNull { file ->
            try {
                NoteClip.fromJson(JSONObject(file.readText()))
            } catch (e: Exception) {
                Timber.e(e, "Could not read note clip ${file.name}")
                null
            }
        }.sortedWith(clipOrder)
    }

    companion object {
        private val clipOrder = compareByDescending<NoteClip> { it.createdAt }

        @Volatile
        private var instance: NoteClipStore? = null

        fun get(context: Context): NoteClipStore {
            return instance ?: synchronized(this) {
                instance ?: NoteClipStore(context).also { instance = it }
            }
        }
    }
}
