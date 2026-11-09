package com.paymentannouncer.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

class PaymentNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(applicationContext) { status ->
            ready.set(status == TextToSpeech.SUCCESS)
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = listOf(
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        ).filterNotNull().joinToString(" ").trim()
        val raw = "$title $body".trim()
        if (raw.isBlank()) return
        val direction = detectDirection(raw) ?: return
        val amount = detectAmount(raw) ?: return
        val app = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() } catch (_: Exception) { sbn.packageName }
        val fingerprint = "${sbn.packageName}|${sbn.key}|${sbn.postTime}|$direction|$amount|${raw.hashCode()}"
        scope.launch {
            val id = PaymentDatabase.get(applicationContext).dao().insert(
                Payment(amount = amount, direction = direction, appName = app, detail = raw.take(240), createdAt = sbn.postTime, fingerprint = fingerprint)
            )
            if (id > 0) announce(direction, amount, app)
        }
    }

    private fun detectDirection(s: String): String? {
        val x = s.lowercase()
        val received = listOf("received", "credited", "credit", "deposited", "got ₹", "got rs", "you've got", "you have received", "money added", "payment from")
        val sent = listOf("sent", "debited", "debit", "paid", "payment to", "transferred", "withdrawn")
        return when {
            received.any { x.contains(it) } -> "RECEIVED"
            sent.any { x.contains(it) } -> "SENT"
            else -> null
        }
    }

    private fun detectAmount(s: String): Double? {
        val patterns = listOf(
            Regex("(?:₹|rs\\.?\\s*|inr\\s*)([0-9,]+(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE),
            Regex("([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:rupees|inr|rs\\.?)", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val value = p.find(s)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
            if (value != null && value > 0) return value
        }
        return null
    }

    private fun announce(direction: String, amount: Double, app: String) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (!prefs.getBoolean("voice_enabled", true)) return
        if (direction == "RECEIVED" && !prefs.getBoolean("incoming", true)) return
        if (direction == "SENT" && !prefs.getBoolean("outgoing", true)) return
        val lang = prefs.getString("voice_language", "hi") ?: "hi"
        val locale = when (lang) { "en" -> Locale.US; "hi" -> Locale("hi", "IN"); else -> Locale("hi", "IN") }
        if (ready.get()) {
            tts?.language = locale
            val phrase = if (direction == "RECEIVED") "Payment received rupees $amount from $app" else "Payment sent rupees $amount to $app"
            tts?.speak(phrase, TextToSpeech.QUEUE_ADD, null, "payment-${System.currentTimeMillis()}")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
