package app.overfit.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSLibraryDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationStatus
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.darwin.dispatch_async
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import platform.UIKit.UIApplicationState
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import kotlin.time.Clock

/**
 * Rest end on iOS: a local notification scheduled for the end time. iOS delivers it while the app
 * is in the background or the screen is locked; while the app is open it is not shown (no
 * delegate presents it), the app vibrates itself. With vibration on, the default sound is used,
 * because iOS only vibrates for notifications that have a sound (silent mode: vibration only).
 *
 * Ticks (see docs/DECISIONS.md, "Ticken vor Pausenende"): no app code runs in the background, so
 * a second notification at end − 3 s carries the tick sound (a WAV written once to
 * Library/Sounds, where notification sounds may live). In the foreground iOS does not present it,
 * the app plays the same sound itself via AVAudioPlayer ([appPlaysTicks], [playTicks]).
 */
@OptIn(ExperimentalForeignApi::class)
class IosRestAlerts : RestAlerts {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()
    private var player: AVAudioPlayer? = null
    /** Counts playbacks, so a stale "done" callback does not stop a newer one. */
    private var playback = 0

    override fun requestPermission() {
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }
    }

    override fun schedule(alert: RestAlert) {
        val now = Clock.System.now().toEpochMilliseconds()
        val seconds = (alert.endsAt - now) / 1000.0
        center.removeDeliveredNotificationsWithIdentifiers(listOf(ID, ID_TICKS))
        if (seconds <= 0) {
            center.removePendingNotificationRequestsWithIdentifiers(listOf(ID, ID_TICKS))
            return
        }
        val content = UNMutableNotificationContent().apply {
            setTitle(alert.overTitle)
            setBody(alert.body)
            setThreadIdentifier(ID)
            if (alert.vibrate) setSound(UNNotificationSound.defaultSound)
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
        // Same identifier: replaces a pending request (±15 s).
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(ID, content, trigger), null)

        val ticksAt = RestTicks.startAt(alert, now)
        val sound = if (ticksAt != null) tickSoundName() else null
        if (ticksAt == null || sound == null) {
            center.removePendingNotificationRequestsWithIdentifiers(listOf(ID_TICKS))
            return
        }
        val ticks = UNMutableNotificationContent().apply {
            setTitle(alert.title)
            setBody(alert.ticksBody)
            setThreadIdentifier(ID)
            setSound(UNNotificationSound.soundNamed(sound))
        }
        // A time interval must be > 0; at exactly 3 s left the ticks start right away.
        val tickSeconds = ((ticksAt - now) / 1000.0).coerceAtLeast(0.1)
        val tickTrigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(tickSeconds, repeats = false)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(ID_TICKS, ticks, tickTrigger), null)
    }

    override fun cancel() {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(ID, ID_TICKS))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(ID, ID_TICKS))
        stopTicks()
    }

    override suspend fun notificationsAllowed(): Boolean = authorization().let {
        it == UNAuthorizationStatusAuthorized || it == UNAuthorizationStatusProvisional || it == UNAuthorizationStatusEphemeral
    }

    private suspend fun authorization(): UNAuthorizationStatus = suspendCoroutine { cont ->
        center.getNotificationSettingsWithCompletionHandler { settings ->
            cont.resume(settings?.authorizationStatus ?: UNAuthorizationStatusNotDetermined)
        }
    }

    /** Never asked: the system dialog. Denied before: the app's page in the Settings app. */
    override fun allowNotifications() {
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus ?: UNAuthorizationStatusNotDetermined
            dispatch_async(dispatch_get_main_queue()) {
                if (status == UNAuthorizationStatusNotDetermined) {
                    requestPermission()
                } else {
                    NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
                        UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null)
                    }
                }
            }
        }
    }

    override fun openNotificationSettings() {
        dispatch_async(dispatch_get_main_queue()) {
            NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
                UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null)
            }
        }
    }

    override fun appPlaysTicks(): Boolean =
        UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive

    /**
     * Category "playback" so the ticks are heard in silent mode too (media volume), mixed with
     * other audio and ducking it; the session is released afterwards so music gets loud again.
     */
    override fun playTicks() {
        stopTicks()
        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            AVAudioSessionCategoryPlayback,
            withOptions = AVAudioSessionCategoryOptionMixWithOthers or AVAudioSessionCategoryOptionDuckOthers,
            error = null,
        )
        session.setActive(true, error = null)
        val p = AVAudioPlayer(data = wavData(), error = null)
        player = p
        p.play()
        val current = ++playback
        val delayNs = (RestTicks.durationMs + 150) * 1_000_000
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delayNs), dispatch_get_main_queue()) {
            if (current == playback) stopTicks()
        }
    }

    private fun stopTicks() {
        val p = player ?: return
        player = null
        p.stop()
        AVAudioSession.sharedInstance().setActive(
            false,
            withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
            error = null,
        )
    }

    private fun wavData(): NSData {
        val bytes = RestTicks.wav()
        return bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
    }

    /** Writes the tick sound to Library/Sounds once; null if that fails (then no tick notification). */
    private fun tickSoundName(): String? {
        val library = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, true).firstOrNull() as? String
            ?: return null
        val dir = "$library/Sounds"
        val path = "$dir/$TICK_SOUND"
        val files = NSFileManager.defaultManager
        if (files.fileExistsAtPath(path)) return TICK_SOUND
        files.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        return if (wavData().writeToFile(path, atomically = true)) TICK_SOUND else null
    }

    companion object {
        const val ID = "app.overfit.rest"
        const val ID_TICKS = "app.overfit.rest.ticks"
        /** Versioned: iOS caches notification sounds by name. */
        const val TICK_SOUND = "overfit_rest_ticks_v1.wav"
    }
}
