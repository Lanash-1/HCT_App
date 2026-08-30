package com.codigitech.belay.data.notification

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codigitech.belay.MainActivity
import com.codigitech.belay.R
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.PushTokenRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives FCM pushes — the tray fallback for when the app isn't open to pick state up over a
 * Firestore listener (docs/TECH_STACK.md §4).
 *
 * Nothing here writes app state: the push is only an alert. The real data is read from Firestore
 * when the user opens the app, so a dropped or duplicated push can't corrupt anything.
 */
@AndroidEntryPoint
class BelayMessagingService : FirebaseMessagingService() {

  @Inject lateinit var pushTokenRepository: PushTokenRepository

  @Inject lateinit var authRepository: AuthRepository

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /** FCM rotates tokens on its own schedule; a rotated token that isn't re-registered stops receiving pushes. */
  override fun onNewToken(token: String) {
    val userId = authRepository.currentUserId() ?: return
    scope.launch { pushTokenRepository.register(userId) }
  }

  override fun onMessageReceived(message: RemoteMessage) {
    // Channel comes from the payload's own type rather than the sender-specified channel id, so
    // an unrecognised type from a newer backend is dropped instead of vanishing into a channel
    // this app never created.
    val channel = PushChannels.forType(message.data["type"]) ?: return
    val notification = message.notification ?: return
    val title = notification.title ?: return

    if (
      ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
      return
    }

    val openApp =
      android.app.PendingIntent.getActivity(
        this,
        channel.id.hashCode(),
        android.content.Intent(this, MainActivity::class.java),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
      )

    val built =
      NotificationCompat.Builder(this, channel.id)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(notification.body.orEmpty())
        .setContentIntent(openApp)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    // Keyed by channel so a second cheer replaces the first rather than stacking four
    // near-identical rows in the tray.
    NotificationManagerCompat.from(this).notify(channel.id.hashCode(), built)
  }
}
