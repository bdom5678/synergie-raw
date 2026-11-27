package com.sophonie.assistant

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Tool
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

/** Un outil que Claude peut appeler : nom, description, paramètres et exécution sur le téléphone. */
class Outil(
    val nom: String,
    val description: String,
    val parametres: Map<String, Map<String, Any>>,
    val obligatoires: List<String>,
    val action: (JSONObject) -> String,
) {
    fun versApi(): Tool = Tool.builder()
        .name(nom)
        .description(description)
        .inputSchema(Tool.InputSchema.builder()
            .properties(Tool.InputSchema.Properties.builder().apply {
                parametres.forEach { (cle, schema) -> putAdditionalProperty(cle, JsonValue.from(schema)) }
            }.build())
            .required(obligatoires)
            .build())
        .build()
}

private fun texte(description: String) = mapOf("type" to "string", "description" to description)
private fun entier(description: String) = mapOf("type" to "integer", "description" to description)
private fun choix(description: String, vararg valeurs: String) =
    mapOf("type" to "string", "enum" to valeurs.toList(), "description" to description)

class Outils(private val context: Context) {
    private val memoire = Memoire.de(context)

    private fun autorise(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun refus(quoi: String) = "Accès refusé : autorisez « $quoi » dans l'application Sophonie (bouton Autorisations)."

    /** avecActions = false pour les tâches automatiques (tri, briefings) : lecture et rappels seulement. */
    fun liste(avecActions: Boolean): List<Outil> {
        val lecture = listOf(
            Outil("messages_recus",
                "Lit les messages reçus sur le téléphone via les notifications : WhatsApp, WhatsApp Business, SMS, mails, appels manqués.",
                mapOf("depuis_heures" to entier("Fenêtre de temps en heures (ex. 24)."),
                    "source" to choix("Filtre facultatif.", "", "whatsapp", "whatsapp_business", "sms", "mail", "appel", "autre"),
                    "recherche" to texte("Mot ou nom à chercher, facultatif.")),
                listOf("depuis_heures")) { p ->
                val depuis = System.currentTimeMillis() - p.optLong("depuis_heures", 24) * 3_600_000
                memoire.messages(depuis, p.optString("source"), p.optString("recherche")).toString()
            },
            Outil("sms", "Lit les SMS reçus et envoyés (boîte du téléphone).",
                mapOf("depuis_heures" to entier("Fenêtre en heures."), "recherche" to texte("Mot ou numéro, facultatif.")),
                listOf("depuis_heures")) { p -> lireSms(p.optLong("depuis_heures", 48), p.optString("recherche")) },
            Outil("appels_recents", "Liste les appels récents (entrants, sortants, manqués).",
                mapOf("depuis_heures" to entier("Fenêtre en heures.")), listOf("depuis_heures")) { p ->
                appels(p.optLong("depuis_heures", 48))
            },
            Outil("chercher_contact", "Cherche un contact du téléphone (numéros, e-mails, anniversaire).",
                mapOf("recherche" to texte("Nom, prénom ou numéro.")), listOf("recherche")) { p ->
                contacts(p.getString("recherche"))
            },
            Outil("anniversaires", "Liste les anniversaires des contacts pour un jour donné.",
                mapOf("mois_jour" to texte("Format MM-JJ, ex. 11-21.")), listOf("mois_jour")) { p ->
                anniversaires(p.getString("mois_jour"))
            },
            Outil("agenda", "Lit les événements de l'agenda du téléphone (Google Agenda et autres comptes synchronisés).",
                mapOf("a_partir_de_jours" to entier("0 = aujourd'hui, 1 = demain..."),
                    "nombre_de_jours" to entier("Nombre de jours couverts.")),
                listOf("a_partir_de_jours", "nombre_de_jours")) { p ->
                agenda(p.optInt("a_partir_de_jours", 0), p.optInt("nombre_de_jours", 1))
            },
            Outil("lire_gouts", "Lit ce que Sophonie sait des goûts, habitudes, rejets et objectifs de l'utilisateur.",
                mapOf("categorie" to texte("Catégorie facultative (cuisine, musique, foi, travail, relations, sport...).")),
                emptyList()) { p -> memoire.gouts(p.optString("categorie")).toString() },
            Outil("apprendre_gout", "Mémorise durablement un goût, une habitude, un rejet ou un objectif.",
                mapOf("categorie" to texte("Catégorie courte."), "description" to texte("Ce qui a été appris, en une phrase."),
                    "sentiment" to choix("Nature de l'information.", "aime", "n_aime_pas", "habitude", "objectif")),
                listOf("categorie", "description", "sentiment")) { p ->
                memoire.apprendreGout(p.getString("categorie"), p.getString("description"), p.getString("sentiment"))
                "Mémorisé."
            },
            Outil("creer_rappel", "Programme un rappel : le téléphone sonnera et affichera une alerte à l'heure dite.",
                mapOf("texte" to texte("Ce qu'il faut rappeler."),
                    "echeance" to texte("Date et heure locales, format AAAA-MM-JJTHH:MM, ex. 2026-11-21T15:30."),
                    "recurrence" to choix("Répétition.", "", "quotidien", "hebdomadaire")),
                listOf("texte", "echeance")) { p ->
                val quand = Temps.analyser(p.getString("echeance")) ?: return@Outil "Échéance illisible."
                if (quand < System.currentTimeMillis()) return@Outil "Cette heure est déjà passée."
                val id = memoire.ajouterRappel(p.getString("texte"), quand, p.optString("recurrence"))
                Rythme.programmerRappel(context, id, quand)
                "Rappel n°$id programmé pour ${Temps.lisible(quand)}."
            },
            Outil("lister_rappels", "Liste les rappels à venir.", emptyMap(), emptyList()) {
                JSONArray(memoire.rappelsActifs()).toString()
            },
            Outil("annuler_rappel", "Annule un rappel.", mapOf("rappel_id" to entier("Numéro du rappel.")),
                listOf("rappel_id")) { p ->
                memoire.terminerRappel(p.getLong("rappel_id")); "Rappel annulé."
            },
        )
        if (!avecActions) return lecture
        return lecture + listOf(
            Outil("creer_evenement", "Ajoute un événement dans l'agenda principal du téléphone.",
                mapOf("titre" to texte("Titre."), "debut" to texte("AAAA-MM-JJTHH:MM, heure locale."),
                    "fin" to texte("AAAA-MM-JJTHH:MM, heure locale."), "lieu" to texte("Lieu, facultatif.")),
                listOf("titre", "debut", "fin")) { p -> creerEvenement(p) },
            Outil("preparer_message",
                "Ouvre WhatsApp, WhatsApp Business ou les SMS avec le message déjà écrit. L'utilisateur n'a plus qu'à appuyer sur Envoyer.",
                mapOf("canal" to choix("Application.", "whatsapp", "whatsapp_business", "sms"),
                    "numero" to texte("Numéro au format international, ex. +237600000000."),
                    "texte" to texte("Texte du message.")),
                listOf("canal", "numero", "texte")) { p -> preparerMessage(p) },
            Outil("appeler", "Ouvre le composeur avec le numéro : l'utilisateur appuie pour lancer l'appel.",
                mapOf("numero" to texte("Numéro à appeler.")), listOf("numero")) { p ->
                ouvrir(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(p.getString("numero")))))
                "Composeur ouvert."
            },
            Outil("itineraire", "Ouvre la navigation vers une destination.",
                mapOf("destination" to texte("Adresse ou lieu.")), listOf("destination")) { p ->
                ouvrir(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(p.getString("destination")))))
                "Navigation ouverte."
            },
        )
    }

    fun executer(outils: List<Outil>, nom: String, entree: JSONObject): String = try {
        outils.firstOrNull { it.nom == nom }?.action?.invoke(entree) ?: "Outil inconnu : $nom"
    } catch (e: Exception) {
        "Erreur : ${e.message}"
    }

    // ------------------------------------------------------------------ implémentations
    private fun lireSms(heures: Long, recherche: String): String {
        if (!autorise(Manifest.permission.READ_SMS)) return refus("SMS")
        val depuis = System.currentTimeMillis() - heures * 3_600_000
        val sortie = JSONArray()
        context.contentResolver.query(Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
            "${Telephony.Sms.DATE} >= ?", arrayOf(depuis.toString()), "${Telephony.Sms.DATE} DESC")?.use { c ->
            while (c.moveToNext() && sortie.length() < 40) {
                val corps = c.getString(1) ?: ""
                val adresse = c.getString(0) ?: ""
                if (recherche.isNotBlank() && !corps.contains(recherche, true) && !adresse.contains(recherche)) continue
                sortie.put(JSONObject().put("numero", adresse).put("texte", corps)
                    .put("date", Temps.lisible(c.getLong(2)))
                    .put("sens", if (c.getInt(3) == Telephony.Sms.MESSAGE_TYPE_SENT) "envoyé" else "reçu"))
            }
        }
        return sortie.toString()
    }

    private fun appels(heures: Long): String {
        if (!autorise(Manifest.permission.READ_CALL_LOG)) return refus("Journal d'appels")
        val depuis = System.currentTimeMillis() - heures * 3_600_000
        val sortie = JSONArray()
        context.contentResolver.query(CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE, CallLog.Calls.DURATION),
            "${CallLog.Calls.DATE} >= ?", arrayOf(depuis.toString()), "${CallLog.Calls.DATE} DESC")?.use { c ->
            while (c.moveToNext() && sortie.length() < 40) {
                val type = when (c.getInt(3)) {
                    CallLog.Calls.MISSED_TYPE -> "manqué"; CallLog.Calls.OUTGOING_TYPE -> "sortant"
                    CallLog.Calls.REJECTED_TYPE -> "rejeté"; else -> "entrant"
                }
                sortie.put(JSONObject().put("nom", c.getString(0) ?: "").put("numero", c.getString(1) ?: "")
                    .put("date", Temps.lisible(c.getLong(2))).put("type", type).put("duree_s", c.getLong(4)))
            }
        }
        return sortie.toString()
    }

    private fun contacts(recherche: String): String {
        if (!autorise(Manifest.permission.READ_CONTACTS)) return refus("Contacts")
        val parNom = linkedMapOf<String, JSONObject>()
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
            arrayOf("%$recherche%", "%$recherche%"), null)?.use { c ->
            while (c.moveToNext() && parNom.size < 10) {
                val nom = c.getString(0) ?: continue
                parNom.getOrPut(nom) { JSONObject().put("nom", nom).put("numeros", JSONArray()) }
                    .getJSONArray("numeros").put(c.getString(1) ?: "")
            }
        }
        return if (parNom.isEmpty()) "Aucun contact trouvé." else JSONArray(parNom.values).toString()
    }

    private fun anniversaires(moisJour: String): String {
        if (!autorise(Manifest.permission.READ_CONTACTS)) return refus("Contacts")
        val sortie = JSONArray()
        context.contentResolver.query(ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE),
            "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
            arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()), null)?.use { c ->
            while (c.moveToNext()) {
                val date = c.getString(1) ?: continue
                if (date.endsWith(moisJour)) sortie.put(JSONObject().put("nom", c.getString(0)).put("date", date))
            }
        }
        return sortie.toString()
    }

    private fun agenda(aPartirDe: Int, jours: Int): String {
        if (!autorise(Manifest.permission.READ_CALENDAR)) return refus("Agenda")
        val debut = java.time.LocalDate.now().plusDays(aPartirDe.toLong())
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val fin = debut + jours.coerceIn(1, 31) * 86_400_000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .let { ContentUrisCompat.ajouter(it, debut, fin) }.build()
        val sortie = JSONArray()
        context.contentResolver.query(uri,
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME),
            null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) sortie.put(JSONObject().put("titre", c.getString(0) ?: "(sans titre)")
                .put("debut", Temps.lisible(c.getLong(1))).put("fin", Temps.lisible(c.getLong(2)))
                .put("lieu", c.getString(3) ?: "").put("journee_entiere", c.getInt(4) == 1)
                .put("agenda", c.getString(5) ?: ""))
        }
        return if (sortie.length() == 0) "Aucun événement." else sortie.toString()
    }

    private fun creerEvenement(p: JSONObject): String {
        if (!autorise(Manifest.permission.WRITE_CALENDAR)) return refus("Agenda")
        val debut = Temps.analyser(p.getString("debut")) ?: return "Date de début illisible."
        val fin = Temps.analyser(p.getString("fin")) ?: return "Date de fin illisible."
        var calendrier: Long? = null
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY),
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null)?.use { c ->
            while (c.moveToNext()) {
                if (calendrier == null || c.getInt(1) == 1) calendrier = c.getLong(0)
            }
        }
        val id = calendrier ?: return "Aucun agenda modifiable sur le téléphone."
        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, id)
            put(CalendarContract.Events.TITLE, p.getString("titre"))
            put(CalendarContract.Events.DTSTART, debut)
            put(CalendarContract.Events.DTEND, fin)
            put(CalendarContract.Events.EVENT_LOCATION, p.optString("lieu"))
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }) ?: return "L'agenda a refusé l'ajout."
        return "Événement ajouté : ${p.getString("titre")}, ${Temps.lisible(debut)}."
    }

    private fun preparerMessage(p: JSONObject): String {
        val numero = p.getString("numero").filter { it.isDigit() || it == '+' }
        val texteMessage = p.getString("texte")
        val intent = when (p.getString("canal")) {
            "sms" -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$numero")).putExtra("sms_body", texteMessage)
            else -> Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://api.whatsapp.com/send?phone=${numero.trimStart('+')}&text=${Uri.encode(texteMessage)}"))
                .setPackage(if (p.getString("canal") == "whatsapp_business") "com.whatsapp.w4b" else "com.whatsapp")
        }
        return if (ouvrir(intent)) "Message prêt dans l'application : il reste à appuyer sur Envoyer."
        else "Application introuvable sur le téléphone."
    }

    private fun ouvrir(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) {
        false
    }
}

/** Ajoute la plage [debut, fin] à l'URI des instances d'agenda. */
private object ContentUrisCompat {
    fun ajouter(b: Uri.Builder, debut: Long, fin: Long): Uri.Builder =
        b.appendPath(debut.toString()).appendPath(fin.toString())
}
