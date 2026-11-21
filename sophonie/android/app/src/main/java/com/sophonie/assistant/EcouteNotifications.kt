package com.sophonie.assistant

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.Executors

/**
 * Lit les notifications entrantes (WhatsApp, WhatsApp Business, SMS, mails, appels manqués), les garde en
 * mémoire sur le téléphone et demande à Sophonie s'il faut vous alerter.
 * À activer dans : Paramètres > Notifications > Accès aux notifications > Sophonie.
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
        if (texte.isBlank() || Regex("^\\d+ (nouveaux? )?messages?.*", RegexOption.IGNORE_CASE).matches(texte)) return
        val sourceFinale = if (source == "telephone") {
            if (notification.category == Notification.CATEGORY_MISSED_CALL || texte.contains("manqué", true)) "appel" else return
        } else source

        val cle = "$sourceFinale|$expediteur|$texte"
        synchronized(dejaVus) {
            if (dejaVus.containsKey(cle)) return
            dejaVus[cle] = true
        }

        executeur.execute { traiter(sourceFinale, expediteur, texte) }
    }

    private fun traiter(source: String, expediteur: String, texte: String) {
        val memoire = Memoire.de(applicationContext)
        val id = memoire.ajouterMessage(source, expediteur, texte)
        val reglages = Reglages(applicationContext)
        if (!reglages.configure || !reglages.triIntelligent) return
        try {
            val d = Cerveau(applicationContext).trier(source, expediteur, texte) ?: return
            memoire.marquerImportance(id, d.importance)
            if (d.alerter) {
                Rythme.notifier(applicationContext, (20_000 + id % 10_000).toInt(),
                    "Sophonie · ${d.titre}", d.corps, d.importance == "urgente")
            }
            if (d.rappel.isNotBlank()) {
                val quand = Temps.analyser(d.echeance)
                if (quand != null && quand > System.currentTimeMillis()) {
                    val rid = memoire.ajouterRappel(d.rappel, quand, "")
                    Rythme.programmerRappel(applicationContext, rid, quand)
                }
            }
            if (d.gout.isNotBlank()) memoire.apprendreGout("observations", d.gout, "habitude")
        } catch (e: Exception) {
            Log.w("Sophonie", "Tri impossible : ${e.message}")
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
            "com.google.android.dialer" to "telephone",
            "com.samsung.android.dialer" to "telephone",
            "com.android.server.telecom" to "telephone",
        )
    }
}
