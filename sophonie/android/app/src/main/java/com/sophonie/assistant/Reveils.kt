package com.sophonie.assistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Rappels quotidiens à heure fixe, déclenchés par le téléphone lui-même (même sans réseau) :
 * prière du matin, prière du soir, coucher. À l'heure dite, la notification s'affiche puis
 * Sophonie va chercher sur le serveur le verset et la prière préparés pour ce moment.
 */
object Reveils {
    fun planifierTous(context: Context) {
        Reglages(context).reveils.forEachIndexed { i, (heure, titre) -> planifier(context, i, heure, titre) }
    }

    fun planifier(context: Context, index: Int, heure: String, titre: String) {
        val (h, m) = heure.split(":").map { it.toInt() }
        val prochain = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(context, ReveilRecepteur::class.java)
            .putExtra("index", index).putExtra("heure", heure).putExtra("titre", titre)
        val pi = PendingIntent.getBroadcast(context, 500 + index, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarmes = context.getSystemService(AlarmManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 31 && !alarmes.canScheduleExactAlarms()) {
            alarmes.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, prochain.timeInMillis, pi)
        } else {
            alarmes.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, prochain.timeInMillis, pi)
        }
    }
}

class ReveilRecepteur : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val index = intent.getIntExtra("index", 0)
        val heure = intent.getStringExtra("heure") ?: return
        val titre = intent.getStringExtra("titre") ?: "Sophonie"
        Notifications.afficher(context, 500 + index, titre, "Prenez un instant. Sophonie vous accompagne.", true)
        AlertesWorker.maintenant(context)
        Reveils.planifier(context, index, heure, titre) // demain, même heure
    }
}

class DemarrageRecepteur : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Reveils.planifierTous(context)
            AlertesWorker.planifier(context)
        }
    }
}
