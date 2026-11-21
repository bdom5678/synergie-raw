package com.sophonie.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Réglages enregistrés sur le téléphone (adresse du serveur, jeton, heures). */
class Reglages(context: Context) {
    private val prefs = context.getSharedPreferences("sophonie", Context.MODE_PRIVATE)

    var serveur: String
        get() = prefs.getString("serveur", "") ?: ""
        set(v) = prefs.edit().putString("serveur", v.trim().trimEnd('/')).apply()

    var jeton: String
        get() = prefs.getString("jeton", "") ?: ""
        set(v) = prefs.edit().putString("jeton", v.trim()).apply()

    /** Heures locales des rappels sur l'appareil (prières, lever, coucher), format HH:MM. */
    var reveils: List<Pair<String, String>>
        get() = (prefs.getString("reveils", null) ?: DEFAUT).split(";").mapNotNull {
            val morceaux = it.split("=", limit = 2)
            if (morceaux.size == 2) morceaux[0] to morceaux[1] else null
        }
        set(v) = prefs.edit().putString("reveils", v.joinToString(";") { "${it.first}=${it.second}" }).apply()

    var dernierAppelVu: Long
        get() = prefs.getLong("dernier_appel", System.currentTimeMillis())
        set(v) = prefs.edit().putLong("dernier_appel", v).apply()

    val configure: Boolean get() = serveur.isNotBlank() && jeton.isNotBlank()

    companion object {
        const val DEFAUT = "07:00=🙏 Prière du matin;23:00=🙏 Prière du soir;23:30=🌙 Il est l'heure de dormir"
    }
}

/** Appels HTTP vers le serveur Sophonie. À appeler hors du thread principal. */
class Api(private val reglages: Reglages) {

    fun discuter(texte: String): String =
        JSONObject(requete("POST", "/chat", JSONObject().put("texte", texte).toString()))
            .optString("reponse", "")

    fun envoyerEvenement(source: String, expediteur: String, texte: String) {
        val corps = JSONObject().put("source", source).put("expediteur", expediteur).put("texte", texte)
        requete("POST", "/evenements", corps.toString())
    }

    fun alertes(): JSONArray = JSONArray(requete("GET", "/alertes", null))

    fun marquerVue(id: Int) {
        requete("POST", "/alertes/$id/vue", "{}")
    }

    fun envoyerContacts(contacts: JSONArray): Int =
        JSONObject(requete("POST", "/contacts", JSONObject().put("contacts", contacts).toString()))
            .optInt("enregistres", 0)

    private fun requete(methode: String, chemin: String, corps: String?): String {
        check(reglages.configure) { "Serveur ou jeton non configuré." }
        val connexion = URL(reglages.serveur + chemin).openConnection() as HttpURLConnection
        try {
            connexion.requestMethod = methode
            connexion.connectTimeout = 15_000
            connexion.readTimeout = 180_000 // Sophonie peut réfléchir et consulter plusieurs outils
            connexion.setRequestProperty("Authorization", "Bearer ${reglages.jeton}")
            connexion.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (corps != null) {
                connexion.doOutput = true
                connexion.outputStream.use { it.write(corps.toByteArray(Charsets.UTF_8)) }
            }
            val code = connexion.responseCode
            val flux = if (code in 200..299) connexion.inputStream else connexion.errorStream
            val texte = flux?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("Erreur serveur $code : $texte")
            return texte
        } finally {
            connexion.disconnect()
        }
    }
}
