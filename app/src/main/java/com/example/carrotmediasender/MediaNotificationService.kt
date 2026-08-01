package com.example.carrotmediasender

import android.app.Notification
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

class MediaNotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastTitle: String = ""
    private var lastArtist: String = ""
    private var activeController: MediaController? = null
    private var pollingJob: kotlinx.coroutines.Job? = null
    private var currentArtBase64: String = ""

    override fun onListenerConnected() {
        Log.d("MediaSender", "NotificationListenerConnected")
        try {
            val notifications = activeNotifications
            if (notifications != null) {
                for (sbn in notifications) {
                    checkNotification(sbn)
                }
            }
        } catch (e: Exception) {
            Log.e("MediaSender", "Error getting active notifications: ${e.message}")
        }
        startPolling()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        checkNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Option to clear when removed
    }

    override fun onListenerDisconnected() {
        pollingJob?.cancel()
    }
    
    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                try {
                    var title = ""
                    var artist = ""
                    var pos: Long = 0
                    var dur: Long = 0
                    var isPlaying = false
                    
                    activeController?.let { controller ->
                        val pbState = controller.playbackState
                        val meta = controller.metadata
                        if (pbState != null && meta != null) {
                            pos = pbState.position
                            dur = meta.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)
                            isPlaying = pbState.state == android.media.session.PlaybackState.STATE_PLAYING
                            title = meta.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: ""
                            artist = meta.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                        }
                    }
                    sendUdpPacket(title, artist, currentArtBase64, pos, dur, isPlaying)
                } catch (e: Exception) { }
            }
        }
    }
    
    private fun checkNotification(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notification = sbn.notification
        
        // Android creates media notifications with specific extras
        val extras = notification.extras
        val mediaSession = extras.getParcelable<android.media.session.MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        
        if (mediaSession != null) {
            try {
                activeController = MediaController(this, mediaSession)
            } catch (e: Exception) {}
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            
            // Extract album art from MediaController if available, otherwise from extras
            var artBitmap: Bitmap? = null
            try {
                val controller = android.media.session.MediaController(this, mediaSession)
                val metadata = controller.metadata
                if (metadata != null) {
                    artBitmap = metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                    if (artBitmap == null) {
                        artBitmap = metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaSender", "Error getting MediaController metadata: ${e.message}")
            }
            
            // Fallback to Notification Extras
            if (artBitmap == null) {
                val pic = extras.get(Notification.EXTRA_PICTURE)
                if (pic is Bitmap) artBitmap = pic
                else if (pic is android.graphics.drawable.Icon) {
                    artBitmap = pic.loadDrawable(this)?.let { d ->
                        val b = Bitmap.createBitmap(d.intrinsicWidth, d.intrinsicHeight, Bitmap.Config.ARGB_8888)
                        val c = android.graphics.Canvas(b)
                        d.setBounds(0, 0, c.width, c.height)
                        d.draw(c)
                        b
                    }
                }
            }
            
            if (artBitmap == null) {
                val li = extras.get(Notification.EXTRA_LARGE_ICON)
                if (li is Bitmap) artBitmap = li
                else if (li is android.graphics.drawable.Icon) {
                    artBitmap = li.loadDrawable(this)?.let { d ->
                        val b = Bitmap.createBitmap(d.intrinsicWidth, d.intrinsicHeight, Bitmap.Config.ARGB_8888)
                        val c = android.graphics.Canvas(b)
                        d.setBounds(0, 0, c.width, c.height)
                        d.draw(c)
                        b
                    }
                }
            }
            
            var artBase64 = ""
            if (artBitmap != null) {
                try {
                    // Resize to a small thumbnail to fit in UDP packet
                    val scaled = Bitmap.createScaledBitmap(artBitmap, 256, 256, true)
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 60, baos)
                    val bytes = baos.toByteArray()
                    artBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    currentArtBase64 = artBase64
                } catch (e: Exception) {
                    Log.e("MediaSender", "Error compressing album art: ${e.message}")
                }
            }
            
            if (title.isNotEmpty() && (title != lastTitle || artist != lastArtist)) {
                lastTitle = title
                lastArtist = artist
                MediaState.update(title, artist)
                Log.d("MediaSender", "New Media: $title - $artist")
                sendUdpPacket(title, artist, artBase64)
            }
        }
    }

    private fun sendUdpPacket(title: String, artist: String, artBase64: String, pos: Long = 0, dur: Long = 0, isPlaying: Boolean = false) {
        scope.launch {
            try {
                val targetIp = "255.255.255.255"
                val port = 5005
                
                val json = JSONObject()
                json.put("media_title", title)
                json.put("media_artist", artist)
                json.put("media_position", pos)
                json.put("media_duration", dur)
                json.put("media_is_playing", isPlaying)
                if (artBase64.isNotEmpty()) {
                    json.put("media_art", artBase64)
                }
                json.put("theme_mode", MediaState.themeMode.value)
                
                val data = json.toString().toByteArray(Charsets.UTF_8)
                
                val socket = DatagramSocket()
                socket.broadcast = true
                val address = InetAddress.getByName(targetIp)
                val packet = DatagramPacket(data, data.size, address, port)
                
                socket.send(packet)
                socket.close()
                Log.d("MediaSender", "UDP broadcasted to $targetIp:$port")
            } catch (e: Exception) {
                Log.e("MediaSender", "UDP Error: ${e.message}")
            }
        }
    }
}
