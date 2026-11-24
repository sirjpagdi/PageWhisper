package com.pagewhisper.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pagewhisper.app.MainActivity
import com.pagewhisper.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps playback alive with the screen off (a foreground service of type mediaPlayback) and shows the
 * notification and lock-screen controls. The real work happens in [Playback].
 */
class PlaybackService : Service() {
    companion object {
        const val ACTION_PLAY = "com.pagewhisper.app.PLAY"
        const val ACTION_PAUSE = "com.pagewhisper.app.PAUSE"
        const val ACTION_NEXT = "com.pagewhisper.app.NEXT"
        const val ACTION_PREV = "com.pagewhisper.app.PREV"
        const val ACTION_STOP = "com.pagewhisper.app.STOP"
        private const val CHANNEL = "playback"
        private const val NOTIF_ID = 1
    }

    private lateinit var session: MediaSessionCompat
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var idleJob: Job? = null

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) Playback.pause() // headphones unplugged
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW))
        session = MediaSessionCompat(this, "PageWhisper").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = Playback.resumeOrPlay()
                override fun onPause() = Playback.pause()
                override fun onSkipToNext() = Playback.skip(1)
                override fun onSkipToPrevious() = Playback.skip(-1)
                override fun onStop() = Playback.stop()
            })
            isActive = true
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(Playback.ui.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch { Playback.ui.collect { update(it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> Playback.resumeOrPlay()
            ACTION_PAUSE -> Playback.pause()
            ACTION_NEXT -> Playback.skip(1)
            ACTION_PREV -> Playback.skip(-1)
            ACTION_STOP -> Playback.stop()
        }
        return START_NOT_STICKY
    }

    private fun update(u: PlayerUi) {
        val state = when {
            u.playing && !u.paused -> PlaybackStateCompat.STATE_PLAYING
            u.playing && u.paused -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP,
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, u.title.ifEmpty { "PageWhisper" })
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, u.subtitle.ifEmpty { "PageWhisper" })
                .build(),
        )
        val busy = u.playing || Playback.advancing
        if (busy) {
            idleJob?.cancel()
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(u))
        } else {
            // not playing: leave after a short pause (the next chapter may be about to start)
            idleJob?.cancel()
            idleJob = scope.launch {
                delay(4000)
                if (!Playback.ui.value.playing && !Playback.advancing) {
                    ServiceCompat.stopForeground(this@PlaybackService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun buildNotification(u: PlayerUi): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(a: String, icon: Int, label: String) = NotificationCompat.Action(
            icon, label, PendingIntent.getService(this, a.hashCode(), Intent(this, PlaybackService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE),
        )
        val playing = u.playing && !u.paused
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pw)
            .setContentTitle(u.title.ifEmpty { "PageWhisper" })
            .setContentText(u.subtitle.ifEmpty { u.status })
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(action(ACTION_PREV, android.R.drawable.ic_media_previous, "Previous"))
            .addAction(if (playing) action(ACTION_PAUSE, android.R.drawable.ic_media_pause, "Pause") else action(ACTION_PLAY, android.R.drawable.ic_media_play, "Play"))
            .addAction(action(ACTION_NEXT, android.R.drawable.ic_media_next, "Next"))
            .addAction(action(ACTION_STOP, android.R.drawable.ic_menu_close_clear_cancel, "Stop"))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    override fun onDestroy() {
        idleJob?.cancel()
        runCatching { unregisterReceiver(noisy) }
        session.isActive = false
        session.release()
        scope.cancel()
        super.onDestroy()
    }
}
