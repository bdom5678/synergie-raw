package com.sophonie.assistant

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.Calendar

/** Les moments fixes de la journée. */
enum class Moment(val heure: Int, val minute: Int, val titre: String, val consigne: String?) {
    REVEIL(7, 0, "☀️ Bonjour", """C'est le réveil. Un seul message court, lisible en une minute :
1) 🙏 un verset biblique du jour (référence + texte) et une prière de 2-3 phrases pour bien commencer ;
2) 📅 l'agenda du jour, avec conflits et préparatifs ;
3) 💬 les messages et appels manqués importants depuis hier soir (WhatsApp, SMS, mails), 5 maximum ;
4) 🎂 les anniversaires du jour et ⏰ les rappels du jour ;
5) une phrase d'encouragement adaptée à ce que vous savez de lui."""),
    MIDI(12, 45, "🕛 Point de midi", """C'est la mi-journée. Message très court : l'agenda de l'après-midi (et l'heure de départ si un lieu est indiqué), les 3 messages les plus urgents de la matinée avec une idée de réponse, et un rappel bien-être d'une phrase."""),
    SOIR(23, 0, "🙏 Prière du soir", """C'est le soir, il se couche vers 23h30. Message court :
1) 🙏 un verset biblique apaisant (référence + texte) et une prière d'action de grâce de 2-3 phrases ;
2) 📋 les messages importants restés sans réponse, à traiter demain (3 maximum) ;
3) 📅 l'agenda de demain, le premier rendez-vous mis en avant, et ce qu'il faut préparer ce soir."""),
    DORMIR(23, 30, "🌙 Il est l'heure de dormir", null),
}

object Rythme {
    const val CANAL_NORMAL = "sophonie_normal"
    const val CANAL_URGENT = "sophonie_urgent"

    fun creerCanaux(context: Context) {
        val g = context.getSystemService(NotificationManager::class.java)
        g.createNotificationChannel(NotificationChannel(CANAL_NORMAL, "Sophonie", NotificationManager.IMPORTANCE_DEFAULT))
        g.createNotificationChannel(NotificationChannel(CANAL_URGENT, "Sophonie – important", NotificationManager.IMPORTANCE_HIGH))
    }

    fun notifier(context: Context, id: Int, titre: String, corps: String, urgent: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val ouvrir = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, if (urgent) CANAL_URGENT else CANAL_NORMAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titre)
            .setContentText(corps)
            .setStyle(NotificationCompat.BigTextStyle().bigText(corps))
            .setPriority(if (urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(ouvrir)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    private fun alarme(context: Context, quand: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quand, pi)
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quand, pi)
    }

    fun programmerMoments(context: Context) = Moment.entries.forEach { programmerMoment(context, it) }

    fun programmerMoment(context: Context, m: Moment) {
        val prochain = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, m.heure); set(Calendar.MINUTE, m.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val pi = PendingIntent.getBroadcast(context, 100 + m.ordinal,
            Intent(context, MomentRecepteur::class.java).putExtra("moment", m.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarme(context, prochain, pi)
    }

    fun programmerRappel(context: Context, id: Long, quand: Long) {
        val pi = PendingIntent.getBroadcast(context, 10_000 + id.toInt(),
            Intent(context, RappelRecepteur::class.java).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarme(context, quand, pi)
    }

    fun reprogrammerTout(context: Context) {
        programmerMoments(context)
        Memoire.de(context).rappelsActifs().forEach { r ->
            programmerRappel(context, r.getLong("id"), maxOf(r.getLong("echeance"), System.currentTimeMillis() + 5_000))
        }
    }
}

/** À l'heure d'un moment : on reprogramme demain, puis Sophonie prépare le message (réseau requis). */
class MomentRecepteur : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val m = Moment.valueOf(intent.getStringExtra("moment") ?: return)
        Rythme.programmerMoment(context, m)
        if (m.consigne == null) {
            Rythme.notifier(context, 100 + m.ordinal, m.titre, "Posez le téléphone, Sophonie veille sur demain.", false)
            return
        }
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<MomentWorker>()
            .setInputData(workDataOf("moment" to m.name))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build())
    }
}

class MomentWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val m = Moment.valueOf(inputData.getString("moment") ?: return Result.failure())
        if (!Reglages(applicationContext).configure) return Result.success()
        return try {
            val texte = Cerveau(applicationContext).moment(m.consigne ?: return Result.success())
            Memoire.de(applicationContext).ajouterEchange("assistant", "${m.titre}\n$texte")
            Rythme.notifier(applicationContext, 100 + m.ordinal, m.titre, texte, m != Moment.MIDI)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else {
                Rythme.notifier(applicationContext, 100 + m.ordinal, m.titre,
                    "Je n'ai pas pu préparer ce moment (${e.message}).", false)
                Result.failure()
            }
        }
    }
}

class RappelRecepteur : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1)
        val memoire = Memoire.de(context)
        val r = memoire.rappel(id) ?: return
        if (r.getInt("fait") == 1) return
        Rythme.notifier(context, 10_000 + id.toInt(), "⏰ Rappel", r.getString("texte"), true)
        val pas = when (r.getString("recurrence")) { "quotidien" -> 86_400_000L; "hebdomadaire" -> 7 * 86_400_000L; else -> 0L }
        if (pas == 0L) memoire.terminerRappel(id) else {
            var suivante = r.getLong("echeance")
            while (suivante <= System.currentTimeMillis()) suivante += pas
            memoire.reporterRappel(id, suivante)
            Rythme.programmerRappel(context, id, suivante)
        }
    }
}

class DemarrageRecepteur : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Rythme.reprogrammerTout(context)
    }
}
