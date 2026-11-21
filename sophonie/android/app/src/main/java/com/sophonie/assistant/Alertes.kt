package com.sophonie.assistant

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Notifications {
    const val CANAL_NORMAL = "sophonie_normal"
    const val CANAL_URGENT = "sophonie_urgent"

    fun creerCanaux(context: Context) {
        val gestionnaire = context.getSystemService(NotificationManager::class.java)
        gestionnaire.createNotificationChannel(
            NotificationChannel(CANAL_NORMAL, "Sophonie", NotificationManager.IMPORTANCE_DEFAULT))
        gestionnaire.createNotificationChannel(
            NotificationChannel(CANAL_URGENT, "Sophonie – important", NotificationManager.IMPORTANCE_HIGH))
    }

    fun afficher(context: Context, id: Int, titre: String, corps: String, urgent: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        val ouvrir = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, if (urgent) CANAL_URGENT else CANAL_NORMAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titre)
            .setContentText(corps)
            .setStyle(NotificationCompat.BigTextStyle().bigText(corps))
            .setPriority(if (urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(ouvrir)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}

/** Toutes les 15 minutes : récupère les alertes du serveur et transmet les appels manqués. */
class AlertesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reglages = Reglages(applicationContext)
        if (!reglages.configure) return Result.success()
        val api = Api(reglages)
        return try {
            transmettreAppelsManques(applicationContext, reglages, api)
            val alertes = api.alertes()
            for (i in 0 until alertes.length()) {
                val a = alertes.getJSONObject(i)
                Notifications.afficher(applicationContext, 10_000 + a.getInt("id"), a.getString("titre"),
                    a.getString("corps"), a.optString("priorite") == "haute")
                api.marquerVue(a.getInt("id"))
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun planifier(context: Context) {
            val contraintes = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "sophonie-alertes", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AlertesWorker>(15, TimeUnit.MINUTES).setConstraints(contraintes).build())
        }

        /** Récupération rapide (à l'heure d'une prière : le serveur a ~2 min pour préparer le verset du jour). */
        fun maintenant(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<AlertesWorker>().setInitialDelay(2, TimeUnit.MINUTES).build())
        }
    }
}

fun transmettreAppelsManques(context: Context, reglages: Reglages, api: Api) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
        != PackageManager.PERMISSION_GRANTED) return
    val depuis = reglages.dernierAppelVu
    var plusRecent = depuis
    context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.DATE),
        "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} > ?",
        arrayOf(CallLog.Calls.MISSED_TYPE.toString(), depuis.toString()),
        "${CallLog.Calls.DATE} ASC",
    )?.use { c ->
        while (c.moveToNext()) {
            val nom = c.getString(0) ?: ""
            val numero = c.getString(1) ?: ""
            val date = c.getLong(2)
            api.envoyerEvenement("appel", if (nom.isNotBlank()) "$nom ($numero)" else numero, "Appel manqué")
            if (date > plusRecent) plusRecent = date
        }
    }
    reglages.dernierAppelVu = plusRecent
}

/** Lit les contacts du téléphone (nom, numéros, e-mails, anniversaire) pour les envoyer à Sophonie. */
fun lireContacts(context: Context): JSONArray {
    val parNom = linkedMapOf<String, JSONObject>()
    fun fiche(nom: String) = parNom.getOrPut(nom) {
        JSONObject().put("nom", nom).put("telephones", JSONArray()).put("emails", JSONArray())
    }
    val r = context.contentResolver
    r.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
        null, null, null)?.use { c ->
        while (c.moveToNext()) {
            val nom = c.getString(0) ?: continue
            fiche(nom).getJSONArray("telephones").put(c.getString(1) ?: "")
        }
    }
    r.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Email.ADDRESS),
        null, null, null)?.use { c ->
        while (c.moveToNext()) {
            val nom = c.getString(0) ?: continue
            fiche(nom).getJSONArray("emails").put(c.getString(1) ?: "")
        }
    }
    r.query(ContactsContract.Data.CONTENT_URI,
        arrayOf(ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE),
        "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
        arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()),
        null)?.use { c ->
        while (c.moveToNext()) {
            val nom = c.getString(0) ?: continue
            fiche(nom).put("anniversaire", c.getString(1) ?: "")
        }
    }
    return JSONArray(parNom.values)
}
