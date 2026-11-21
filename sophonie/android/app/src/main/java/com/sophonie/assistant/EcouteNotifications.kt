package com.sophonie.assistant

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.Executors

/**
 * Lit les notifications entrantes de WhatsApp, WhatsApp Business, SMS et mails, puis les transmet
 * au serveur Sophonie qui décide s'il faut vous alerter. Rien n'est envoyé en votre nom.
 * L'accès s'active dans : Paramètres > Notifications > Accès aux notifications > Sophonie.
 */
class EcouteNotifications : NotificationListenerService() {

    private val executeur = Executors.newSingleThreadExecutor()
    private val dejaVus = object : LinkedHashMap<String, Boolean>(200, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 200
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = SOURCES[sbn.packageName] ?: return
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras
        val expediteur = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val texte = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (texte.isBlank() || texte.matches(Regex("^\\d+ nouveaux? messages?.*"))) return

        // WhatsApp republie souvent la même notification : on évite les doublons.
        val cle = "$source|$expediteur|$texte"
        synchronized(dejaVus) {
            if (dejaVus.containsKey(cle)) return
            dejaVus[cle] = true
        }

        val reglages = Reglages(applicationContext)
        if (!reglages.configure) return
        executeur.execute {
            try {
                Api(reglages).envoyerEvenement(source, expediteur, texte)
            } catch (e: Exception) {
                Log.w("Sophonie", "Transmission impossible : ${e.message}")
            }
        }
    }

    companion object {
        val SOURCES = mapOf(
            "com.whatsapp" to "whatsapp",
            "com.whatsapp.w4b" to "whatsapp_business",
            "com.google.android.apps.messaging" to "sms",
            "com.samsung.android.messaging" to "sms",
            "com.android.mms" to "sms",
            "com.google.android.gm" to "mail",
            "com.microsoft.office.outlook" to "mail",
            "com.samsung.android.email.provider" to "mail",
        )
    }
}
