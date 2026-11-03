package au.com.shiftyjelly.pocketcasts.repositories.notes

import android.icu.text.Transliterator
import android.os.Build
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/** A problem the user can fix, such as a missing API key. The message is shown as is. */
class NotesSetupException(message: String) : Exception(message)

/** A failed call to one of the outside services. */
class NotesApiException(val service: String, val code: Int, body: String) :
    IOException("$service HTTP $code: ${body.take(300)}") {
    val userMessage: String
        get() = when (code) {
            401, 403 -> "$service 的 API key 無效或沒有權限"
            429 -> "$service 額度用完或請求太頻繁，稍後再試"
            else -> "$service 回傳錯誤（$code）"
        }
}

internal object NotesHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun jsonBody(json: JSONObject) = json.toString().toRequestBody(jsonType)

    fun execute(service: String, request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.bodyText()
            if (!response.isSuccessful) throw NotesApiException(service, response.code, text)
            return JSONObject(text)
        }
    }

    private fun Response.bodyText(): String = body.string()
}

internal object ElevenLabsTranscriber {
    private const val URL = "https://api.elevenlabs.io/v1/speech-to-text"
    private const val MODEL = "scribe_v2"

    class Result(val words: List<NoteWord>)

    /** Transcribes [wav] and shifts the word times by [offsetMs] so they match the episode. */
    fun transcribe(apiKey: String, wav: File, offsetMs: Long): Result {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model_id", MODEL)
            .addFormDataPart("diarize", "true")
            .addFormDataPart("timestamps_granularity", "word")
            .addFormDataPart("tag_audio_events", "false")
            .addFormDataPart("file", "clip.wav", wav.asRequestBody("audio/wav".toMediaType()))
            .build()
        val request = Request.Builder()
            .url(URL)
            .header("xi-api-key", apiKey)
            .post(body)
            .build()
        val json = NotesHttp.execute("ElevenLabs", request)
        val language = json.nullableString("language_code")?.lowercase()
        val isChinese = language != null && (language.startsWith("zh") || language in setOf("cmn", "zho", "yue", "chi"))
        val wordsJson = json.optJSONArray("words") ?: JSONArray()
        val words = (0 until wordsJson.length()).mapNotNull { index ->
            val word = wordsJson.getJSONObject(index)
            val type = word.optString("type", "word")
            if (type == "audio_event") return@mapNotNull null
            val text = word.optString("text")
            NoteWord(
                text = if (isChinese) ChineseText.toTraditional(text) else text,
                startMs = offsetMs + (word.optDouble("start", 0.0) * 1000).toLong(),
                endMs = offsetMs + (word.optDouble("end", 0.0) * 1000).toLong(),
                speaker = word.nullableString("speaker_id"),
                isSpacing = type == "spacing",
            )
        }
        return Result(words)
    }
}

/** Scribe tends to write Mandarin in simplified characters; the notes should be in traditional ones. */
internal object ChineseText {
    private val transliterator: Transliterator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { Transliterator.getInstance("Hans-Hant") }.getOrNull()
        } else {
            null
        }
    }

    fun toTraditional(text: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return text
        return transliterator?.transliterate(text) ?: text
    }
}

internal object NoteAiClient {
    fun complete(config: NotesConfig, system: String, user: String): String {
        return when (config.effectiveProvider) {
            AiProvider.Claude -> claude(config.claudeKey, config.effectiveClaudeModel, system, user)
            AiProvider.OpenAi -> openAi(config.openAiKey, config.effectiveOpenAiModel, system, user)
            null -> throw NotesSetupException("請先在聽記設定填入 Claude 或 OpenAI 的 API key")
        }
    }

    private fun claude(apiKey: String, model: String, system: String, user: String): String {
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 2000)
            put("system", system)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
        }
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(NotesHttp.jsonBody(body))
            .build()
        val json = NotesHttp.execute("Claude", request)
        val content = json.optJSONArray("content") ?: JSONArray()
        return (0 until content.length())
            .map { content.getJSONObject(it) }
            .filter { it.optString("type") == "text" }
            .joinToString("") { it.optString("text") }
    }

    private fun openAi(apiKey: String, model: String, system: String, user: String): String {
        val body = JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )
            put("response_format", JSONObject().put("type", "json_object"))
        }
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(NotesHttp.jsonBody(body))
            .build()
        val json = NotesHttp.execute("OpenAI", request)
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
        return choice?.optJSONObject("message")?.optString("content").orEmpty()
    }
}

internal object TickTickApi {
    const val REDIRECT_URI = "https://localhost/tingji-ticktick"
    private const val SCOPE = "tasks:write tasks:read"

    fun authorizeUrl(clientId: String, state: String): String {
        return "https://ticktick.com/oauth/authorize".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("scope", SCOPE)
            .addQueryParameter("state", state)
            .addQueryParameter("redirect_uri", REDIRECT_URI)
            .addQueryParameter("response_type", "code")
            .build()
            .toString()
    }

    /** Returns the access token and when it expires, in epoch milliseconds (0 when unknown). */
    fun exchangeCode(clientId: String, clientSecret: String, code: String): Pair<String, Long> {
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("code", code)
            .add("grant_type", "authorization_code")
            .add("scope", SCOPE)
            .add("redirect_uri", REDIRECT_URI)
            .build()
        val request = Request.Builder()
            .url("https://ticktick.com/oauth/token")
            .header("Authorization", Credentials.basic(clientId, clientSecret))
            .post(form)
            .build()
        val json = NotesHttp.execute("TickTick", request)
        val token = json.optString("access_token")
        if (token.isBlank()) throw IOException("TickTick did not return an access token")
        val expiresIn = json.optLong("expires_in", 0L)
        val expiresAt = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000 else 0L
        return token to expiresAt
    }

    /** Updates [existing] when given and still there, otherwise creates a task in the inbox. */
    fun upsertTask(token: String, existing: TickTickTaskRef?, title: String, content: String): TickTickTaskRef {
        if (existing != null) {
            try {
                val body = JSONObject().apply {
                    put("id", existing.taskId)
                    put("projectId", existing.projectId)
                    put("title", title)
                    put("content", content)
                }
                val request = Request.Builder()
                    .url("https://api.ticktick.com/open/v1/task/${existing.taskId}")
                    .header("Authorization", "Bearer $token")
                    .post(NotesHttp.jsonBody(body))
                    .build()
                NotesHttp.execute("TickTick", request)
                return existing
            } catch (e: NotesApiException) {
                if (e.code != 404 && e.code != 400) throw e
            }
        }
        val body = JSONObject().apply {
            put("title", title)
            put("content", content)
        }
        val request = Request.Builder()
            .url("https://api.ticktick.com/open/v1/task")
            .header("Authorization", "Bearer $token")
            .post(NotesHttp.jsonBody(body))
            .build()
        val json = NotesHttp.execute("TickTick", request)
        return TickTickTaskRef(projectId = json.optString("projectId"), taskId = json.optString("id"))
    }

}
