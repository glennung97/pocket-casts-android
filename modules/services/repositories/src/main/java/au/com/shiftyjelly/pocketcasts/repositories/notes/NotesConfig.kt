package au.com.shiftyjelly.pocketcasts.repositories.notes

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

enum class AiProvider {
    Claude,
    OpenAi,
}

/**
 * API keys and options for the save-to-notes feature. Everything stays in this app's private storage on the phone.
 */
class NotesConfig private constructor(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var elevenLabsKey: String
        get() = string(KEY_ELEVENLABS)
        set(value) = putString(KEY_ELEVENLABS, value)

    var aiProvider: AiProvider
        get() = AiProvider.entries.firstOrNull { it.name == prefs.getString(KEY_AI_PROVIDER, null) } ?: AiProvider.Claude
        set(value) = putString(KEY_AI_PROVIDER, value.name)

    var claudeKey: String
        get() = string(KEY_CLAUDE)
        set(value) = putString(KEY_CLAUDE, value)

    var claudeModel: String
        get() = string(KEY_CLAUDE_MODEL)
        set(value) = putString(KEY_CLAUDE_MODEL, value)

    var openAiKey: String
        get() = string(KEY_OPENAI)
        set(value) = putString(KEY_OPENAI, value)

    var openAiModel: String
        get() = string(KEY_OPENAI_MODEL)
        set(value) = putString(KEY_OPENAI_MODEL, value)

    var tickTickClientId: String
        get() = string(KEY_TICKTICK_CLIENT_ID)
        set(value) = putString(KEY_TICKTICK_CLIENT_ID, value)

    var tickTickClientSecret: String
        get() = string(KEY_TICKTICK_CLIENT_SECRET)
        set(value) = putString(KEY_TICKTICK_CLIENT_SECRET, value)

    var tickTickToken: String
        get() = string(KEY_TICKTICK_TOKEN)
        set(value) = putString(KEY_TICKTICK_TOKEN, value)

    var tickTickTokenExpiresAt: Long
        get() = prefs.getLong(KEY_TICKTICK_EXPIRES, 0L)
        set(value) = prefs.edit { putLong(KEY_TICKTICK_EXPIRES, value) }

    val effectiveClaudeModel: String
        get() = claudeModel.trim().ifEmpty { DEFAULT_CLAUDE_MODEL }

    val effectiveOpenAiModel: String
        get() = openAiModel.trim().ifEmpty { DEFAULT_OPENAI_MODEL }

    /** The provider to call: the chosen one when it has a key, otherwise whichever has a key. */
    val effectiveProvider: AiProvider?
        get() {
            val hasClaude = claudeKey.isNotBlank()
            val hasOpenAi = openAiKey.isNotBlank()
            return when {
                aiProvider == AiProvider.Claude && hasClaude -> AiProvider.Claude
                aiProvider == AiProvider.OpenAi && hasOpenAi -> AiProvider.OpenAi
                hasClaude -> AiProvider.Claude
                hasOpenAi -> AiProvider.OpenAi
                else -> null
            }
        }

    val isTickTickConnected: Boolean
        get() = tickTickToken.isNotBlank()

    /** The TickTick task an episode was sent to before, so sending again updates it. */
    fun tickTickTask(episodeUuid: String): TickTickTaskRef? {
        val value = prefs.getString(KEY_TICKTICK_TASK_PREFIX + episodeUuid, null) ?: return null
        val parts = value.split('|')
        if (parts.size != 2) return null
        return TickTickTaskRef(projectId = parts[0], taskId = parts[1])
    }

    fun setTickTickTask(episodeUuid: String, ref: TickTickTaskRef) {
        putString(KEY_TICKTICK_TASK_PREFIX + episodeUuid, "${ref.projectId}|${ref.taskId}")
    }

    private fun string(key: String): String = prefs.getString(key, null).orEmpty()

    private fun putString(key: String, value: String) {
        prefs.edit { putString(key, value.trim()) }
    }

    companion object {
        const val DEFAULT_CLAUDE_MODEL = "claude-haiku-5-5"
        const val DEFAULT_OPENAI_MODEL = "gpt-6-luna"

        private const val PREFS_NAME = "tingji_notes_config"
        private const val KEY_ELEVENLABS = "elevenlabs_key"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_CLAUDE = "claude_key"
        private const val KEY_CLAUDE_MODEL = "claude_model"
        private const val KEY_OPENAI = "openai_key"
        private const val KEY_OPENAI_MODEL = "openai_model"
        private const val KEY_TICKTICK_CLIENT_ID = "ticktick_client_id"
        private const val KEY_TICKTICK_CLIENT_SECRET = "ticktick_client_secret"
        private const val KEY_TICKTICK_TOKEN = "ticktick_token"
        private const val KEY_TICKTICK_EXPIRES = "ticktick_expires_at"
        private const val KEY_TICKTICK_TASK_PREFIX = "ticktick_task_"

        @Volatile
        private var instance: NotesConfig? = null

        fun get(context: Context): NotesConfig {
            return instance ?: synchronized(this) {
                instance ?: NotesConfig(context).also { instance = it }
            }
        }
    }
}

data class TickTickTaskRef(
    val projectId: String,
    val taskId: String,
)
