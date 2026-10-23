package com.tx24.spicyplayer.playback

import android.service.notification.NotificationListenerService
import com.tx24.spicyplayer.widget.NowPlayingHolder

/**
 * Exists for its grant: `MediaSessionManager` lets a notification listener read other apps'
 * sessions. While access is on, the system keeps it bound and the app's process alive, so the
 * home screen widget starts following the music here.
 */
class SessionAccessService : NotificationListenerService() {
    override fun onListenerConnected() = NowPlayingHolder.start(this)
}
