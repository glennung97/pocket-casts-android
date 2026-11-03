package au.com.shiftyjelly.pocketcasts.repositories.notes

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import au.com.shiftyjelly.pocketcasts.models.entity.BaseEpisode
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import java.util.UUID
import timber.log.Timber
import au.com.shiftyjelly.pocketcasts.images.R as IR

/** Entry point for the "save to notes" button and the notification that follows it. */
object SaveToNotes {
    const val NOTES_ACTIVITY_CLASS = "au.com.shiftyjelly.pocketcasts.settings.notes.NotesActivity"
    const val CLIP_SHEET_ACTIVITY_CLASS = "au.com.shiftyjelly.pocketcasts.settings.notes.NoteClipSheetActivity"
    const val EXTRA_CLIP_ID = "tingji_clip_id"

    private const val CHANNEL_ID = "tingji_notes"

    /** Saves the playing episode's current position. Returns the new clip's id, or null when nothing is playing. */
    suspend fun captureCurrent(context: Context, playbackManager: PlaybackManager): String? {
        val episode = playbackManager.getCurrentEpisode() ?: return null
        val positionMs = playbackManager.getCurrentTimeMs(episode).toLong()
        return capture(context, episode, positionMs)
    }

    /** Plays the clip's episode from the start of its selection. */
    suspend fun playClip(playbackManager: PlaybackManager, clip: NoteClip) {
        val positionMs = (if (clip.status == NoteClipStatus.Ready) clip.selectionStartMs else clip.bookmarkMs).toInt()
        if (playbackManager.getCurrentEpisode()?.uuid != clip.episodeUuid) {
            playbackManager.playNowSuspend(episodeUuid = clip.episodeUuid)
        }
        playbackManager.seekToTimeMsSuspend(positionMs)
        playbackManager.playIfNotPlaying()
    }

    /** The pop-up that shows one clip over whatever screen is open. */
    fun clipSheetIntent(context: Context, clipId: String): Intent {
        return Intent().setClassName(context.packageName, CLIP_SHEET_ACTIVITY_CLASS)
            .putExtra(EXTRA_CLIP_ID, clipId)
    }

    /** Saves the moment at [positionMs] of [episode] and starts transcribing it in the background. */
    fun capture(context: Context, episode: BaseEpisode, positionMs: Long): String {
        val clip = NoteClip(
            id = UUID.randomUUID().toString(),
            episodeUuid = episode.uuid,
            podcastUuid = episode.podcastOrSubstituteUuid,
            podcastTitle = if (episode is PodcastEpisode) "" else "我的檔案",
            episodeTitle = episode.title,
            bookmarkMs = positionMs,
            createdAt = System.currentTimeMillis(),
            status = NoteClipStatus.Processing,
        )
        NoteClipStore.get(context).save(clip)
        showProcessing(context, clip)
        enqueue(context, clip.id, NoteClipMode.Initial)
        return clip.id
    }

    fun enqueue(context: Context, clipId: String, mode: NoteClipMode) {
        val request = OneTimeWorkRequestBuilder<NoteClipWorker>()
            .setInputData(workDataOf(NoteClipWorker.KEY_CLIP_ID to clipId, NoteClipWorker.KEY_MODE to mode.name))
            .addTag(NoteClipWorker.TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("tingji_clip_$clipId", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun showProcessing(context: Context, clip: NoteClip) {
        val builder = baseBuilder(context, clip)
            .setContentTitle("已記下 ${NoteTranscript.formatTime(clip.bookmarkMs)}")
            .setContentText("轉錄中，完成後會在這裡顯示")
            .setProgress(0, 0, true)
            .addAction(0, "復原", actionIntent(context, clip.id, NotesActionReceiver.ACTION_DELETE))
        notify(context, clip, builder)
    }

    fun showReady(context: Context, clip: NoteClip) {
        val excerpt = clip.selectedSentences.joinToString("") { it.text }.take(220)
        val extended = buildList {
            if (clip.autoExtendedBefore > 0) add("往前補 ${clip.autoExtendedBefore * 30} 秒")
            if (clip.autoExtendedAfter > 0) add("往後補 ${clip.autoExtendedAfter * 30} 秒")
        }
        val footer = buildString {
            if (extended.isNotEmpty()) append("AI 已").append(extended.joinToString("、")).append(" · ")
            append("不處理會放進待整理")
        }
        val noteLine = clip.note.takeIf { it.isNotBlank() }?.let { "\n筆記：$it" }.orEmpty()
        val title = "${NoteTranscript.formatTime(clip.bookmarkMs)} ${clip.title.ifBlank { clip.episodeTitle }}"
        val remoteInput = RemoteInput.Builder(NotesActionReceiver.KEY_NOTE_TEXT).setLabel("加一句筆記").build()
        val noteAction = NotificationCompat.Action.Builder(
            0,
            if (clip.note.isBlank()) "加筆記" else "改筆記",
            actionIntent(context, clip.id, NotesActionReceiver.ACTION_ADD_NOTE, mutable = true),
        ).addRemoteInput(remoteInput).build()
        val builder = baseBuilder(context, clip)
            .setContentTitle(title)
            .setContentText(excerpt)
            .setStyle(NotificationCompat.BigTextStyle().bigText("「$excerpt」$noteLine\n$footer"))
            .addAction(noteAction)
            .addAction(0, "往前補 30 秒", actionIntent(context, clip.id, NotesActionReceiver.ACTION_EXTEND_BEFORE))
            .addAction(0, "刪除", actionIntent(context, clip.id, NotesActionReceiver.ACTION_DELETE))
        notify(context, clip, builder)
    }

    fun showFailed(context: Context, clip: NoteClip) {
        val builder = baseBuilder(context, clip)
            .setContentTitle("${NoteTranscript.formatTime(clip.bookmarkMs)} 沒有存成功")
            .setContentText(clip.error ?: "發生錯誤")
            .setStyle(NotificationCompat.BigTextStyle().bigText(clip.error ?: "發生錯誤"))
            .addAction(0, "重試", actionIntent(context, clip.id, NotesActionReceiver.ACTION_RETRY))
            .addAction(0, "刪除", actionIntent(context, clip.id, NotesActionReceiver.ACTION_DELETE))
        notify(context, clip, builder)
    }

    fun cancel(context: Context, clipId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(clipId))
    }

    fun openNotesIntent(context: Context, clipId: String?): Intent {
        return Intent().setClassName(context.packageName, NOTES_ACTIVITY_CLASS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (clipId != null) putExtra(EXTRA_CLIP_ID, clipId) }
    }

    private fun baseBuilder(context: Context, clip: NoteClip): NotificationCompat.Builder {
        ensureChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId(clip.id),
            openNotesIntent(context, clip.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(IR.drawable.notification)
            .setSubText("存到筆記")
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, clip: NoteClip, builder: NotificationCompat.Builder) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(notificationId(clip.id), builder.build())
        } catch (e: SecurityException) {
            Timber.w(e, "Could not show the notes notification")
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "存到筆記", NotificationManager.IMPORTANCE_LOW).apply {
            description = "存到筆記的轉錄進度與結果"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun actionIntent(context: Context, clipId: String, action: String, mutable: Boolean = false): PendingIntent {
        val intent = Intent(context, NotesActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_CLIP_ID, clipId)
        val mutability = if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(
            context,
            (clipId + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutability,
        )
    }

    private fun notificationId(clipId: String) = clipId.hashCode()
}

class NotesActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val clipId = intent.getStringExtra(SaveToNotes.EXTRA_CLIP_ID) ?: return
        val store = NoteClipStore.get(context)
        when (intent.action) {
            ACTION_DELETE -> {
                store.delete(clipId)
                SaveToNotes.cancel(context, clipId)
            }

            ACTION_EXTEND_BEFORE -> {
                val clip = store.update(clipId) { it.copy(status = NoteClipStatus.Processing) } ?: return
                SaveToNotes.showProcessing(context, clip)
                SaveToNotes.enqueue(context, clipId, NoteClipMode.ExtendBefore)
            }

            ACTION_RETRY -> {
                val clip = store.update(clipId) { it.copy(status = NoteClipStatus.Processing, error = null) } ?: return
                SaveToNotes.showProcessing(context, clip)
                val mode = if (clip.words.isEmpty()) NoteClipMode.Initial else NoteClipMode.Reanalyze
                SaveToNotes.enqueue(context, clipId, mode)
            }

            ACTION_ADD_NOTE -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_NOTE_TEXT)?.toString()?.trim()
                val clip = if (text.isNullOrEmpty()) store.get(clipId) else store.update(clipId) { it.copy(note = text) }
                if (clip != null) SaveToNotes.showReady(context, clip)
            }
        }
    }

    companion object {
        const val ACTION_DELETE = "au.com.shiftyjelly.pocketcasts.notes.DELETE"
        const val ACTION_EXTEND_BEFORE = "au.com.shiftyjelly.pocketcasts.notes.EXTEND_BEFORE"
        const val ACTION_RETRY = "au.com.shiftyjelly.pocketcasts.notes.RETRY"
        const val ACTION_ADD_NOTE = "au.com.shiftyjelly.pocketcasts.notes.ADD_NOTE"
        const val KEY_NOTE_TEXT = "note_text"
    }
}
