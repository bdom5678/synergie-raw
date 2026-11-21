package com.sophonie.assistant

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/** Réglages de Sophonie, stockés uniquement sur le téléphone. */
class Reglages(context: Context) {
    private val prefs = context.getSharedPreferences("sophonie", Context.MODE_PRIVATE)

    var cleApi: String
        get() = prefs.getString("cle_api", "") ?: ""
        set(v) = prefs.edit().putString("cle_api", v.trim()).apply()

    var prenom: String
        get() = prefs.getString("prenom", "") ?: ""
        set(v) = prefs.edit().putString("prenom", v.trim()).apply()

    var contactsPrioritaires: String
        get() = prefs.getString("prioritaires", "") ?: ""
        set(v) = prefs.edit().putString("prioritaires", v.trim()).apply()

    var triIntelligent: Boolean
        get() = prefs.getBoolean("tri", true)
        set(v) = prefs.edit().putBoolean("tri", v).apply()

    var voix: Boolean
        get() = prefs.getBoolean("voix", false)
        set(v) = prefs.edit().putBoolean("voix", v).apply()

    var modele: String
        get() = prefs.getString("modele", "claude-opus-5-5") ?: "claude-opus-5-5"
        set(v) = prefs.edit().putString("modele", v.trim()).apply()

    val configure: Boolean get() = cleApi.startsWith("sk-ant-")
}

/** Mémoire durable de Sophonie (SQLite sur le téléphone). */
class Memoire private constructor(context: Context) :
    SQLiteOpenHelper(context, "sophonie.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, source TEXT, expediteur TEXT,
            texte TEXT, recu_le INTEGER, importance TEXT DEFAULT '')""")
        db.execSQL("""CREATE TABLE gouts (id INTEGER PRIMARY KEY AUTOINCREMENT, categorie TEXT, description TEXT,
            sentiment TEXT, appris_le INTEGER, UNIQUE(categorie, description))""")
        db.execSQL("""CREATE TABLE rappels (id INTEGER PRIMARY KEY AUTOINCREMENT, texte TEXT, echeance INTEGER,
            recurrence TEXT DEFAULT '', fait INTEGER DEFAULT 0)""")
        db.execSQL("CREATE TABLE conversation (id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT, texte TEXT, le INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, ancienne: Int, nouvelle: Int) = Unit

    // --- messages reçus (notifications) ---
    fun ajouterMessage(source: String, expediteur: String, texte: String): Long =
        writableDatabase.insert("messages", null, ContentValues().apply {
            put("source", source); put("expediteur", expediteur); put("texte", texte)
            put("recu_le", System.currentTimeMillis())
        })

    fun marquerImportance(id: Long, importance: String) {
        writableDatabase.update("messages", ContentValues().apply { put("importance", importance) },
            "id = ?", arrayOf(id.toString()))
    }

    fun messages(depuisMs: Long, source: String?, recherche: String?, limite: Int = 40): JSONArray {
        val conditions = mutableListOf("recu_le >= ?")
        val args = mutableListOf(depuisMs.toString())
        if (!source.isNullOrBlank()) { conditions += "source = ?"; args += source }
        if (!recherche.isNullOrBlank()) {
            conditions += "(texte LIKE ? OR expediteur LIKE ?)"; args += "%$recherche%"; args += "%$recherche%"
        }
        val sortie = JSONArray()
        readableDatabase.query("messages", null, conditions.joinToString(" AND "), args.toTypedArray(),
            null, null, "recu_le DESC", limite.toString()).use { c ->
            while (c.moveToNext()) sortie.put(JSONObject()
                .put("source", c.getString(c.getColumnIndexOrThrow("source")))
                .put("expediteur", c.getString(c.getColumnIndexOrThrow("expediteur")))
                .put("texte", c.getString(c.getColumnIndexOrThrow("texte")))
                .put("recu_le", Temps.lisible(c.getLong(c.getColumnIndexOrThrow("recu_le"))))
                .put("importance", c.getString(c.getColumnIndexOrThrow("importance"))))
        }
        return sortie
    }

    // --- goûts, habitudes, objectifs ---
    fun apprendreGout(categorie: String, description: String, sentiment: String) {
        writableDatabase.insertWithOnConflict("gouts", null, ContentValues().apply {
            put("categorie", categorie.lowercase().trim()); put("description", description.trim())
            put("sentiment", sentiment); put("appris_le", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun oublierGout(id: Long) = writableDatabase.delete("gouts", "id = ?", arrayOf(id.toString()))

    fun gouts(categorie: String?): JSONArray {
        val sortie = JSONArray()
        val (selection, args) = if (categorie.isNullOrBlank()) null to null
        else "categorie = ?" to arrayOf(categorie.lowercase().trim())
        readableDatabase.query("gouts", null, selection, args, null, null, "categorie, appris_le DESC").use { c ->
            while (c.moveToNext()) sortie.put(JSONObject()
                .put("id", c.getLong(0)).put("categorie", c.getString(1))
                .put("description", c.getString(2)).put("sentiment", c.getString(3)))
        }
        return sortie
    }

    // --- rappels ---
    fun ajouterRappel(texte: String, echeanceMs: Long, recurrence: String): Long =
        writableDatabase.insert("rappels", null, ContentValues().apply {
            put("texte", texte); put("echeance", echeanceMs); put("recurrence", recurrence)
        })

    fun rappel(id: Long): JSONObject? = readableDatabase.query("rappels", null, "id = ?", arrayOf(id.toString()),
        null, null, null).use { c ->
        if (!c.moveToFirst()) null else JSONObject().put("id", c.getLong(0)).put("texte", c.getString(1))
            .put("echeance", c.getLong(2)).put("recurrence", c.getString(3)).put("fait", c.getInt(4))
    }

    fun rappelsActifs(): List<JSONObject> {
        val sortie = mutableListOf<JSONObject>()
        readableDatabase.query("rappels", null, "fait = 0", null, null, null, "echeance").use { c ->
            while (c.moveToNext()) sortie += JSONObject().put("id", c.getLong(0)).put("texte", c.getString(1))
                .put("echeance", c.getLong(2)).put("quand", Temps.lisible(c.getLong(2)))
                .put("recurrence", c.getString(3))
        }
        return sortie
    }

    fun reporterRappel(id: Long, echeanceMs: Long) {
        writableDatabase.update("rappels", ContentValues().apply { put("echeance", echeanceMs) },
            "id = ?", arrayOf(id.toString()))
    }

    fun terminerRappel(id: Long) {
        writableDatabase.update("rappels", ContentValues().apply { put("fait", 1) }, "id = ?", arrayOf(id.toString()))
    }

    // --- conversation (texte uniquement) ---
    fun ajouterEchange(role: String, texte: String) {
        writableDatabase.insert("conversation", null, ContentValues().apply {
            put("role", role); put("texte", texte); put("le", System.currentTimeMillis())
        })
    }

    /** Derniers échanges, dans l'ordre, en commençant par un message de l'utilisateur. */
    fun historique(limite: Int = 20): List<Pair<String, String>> {
        val lignes = mutableListOf<Pair<String, String>>()
        readableDatabase.query("conversation", arrayOf("role", "texte"), null, null, null, null,
            "id DESC", limite.toString()).use { c -> while (c.moveToNext()) lignes += c.getString(0) to c.getString(1) }
        lignes.reverse()
        while (lignes.isNotEmpty() && lignes.first().first != "user") lignes.removeAt(0)
        return lignes
    }

    companion object {
        @Volatile private var instance: Memoire? = null
        fun de(context: Context): Memoire = instance ?: synchronized(this) {
            instance ?: Memoire(context.applicationContext).also { instance = it }
        }
    }
}

object Temps {
    private val format = java.text.SimpleDateFormat("EEEE dd/MM/yyyy HH:mm", java.util.Locale.FRENCH)
    fun lisible(ms: Long): String = format.format(java.util.Date(ms))

    /** Accepte "2026-11-21T15:30", "2026-11-21T15:30:00+01:00" ou "2026-11-21 15:30" (heure locale). */
    fun analyser(iso: String): Long? = try {
        val t = iso.trim().replace(' ', 'T')
        if (Regex(".*([+-]\\d{2}:\\d{2}|Z)$").matches(t)) java.time.OffsetDateTime.parse(t).toInstant().toEpochMilli()
        else java.time.LocalDateTime.parse(if (t.length == 16) "$t:00" else t)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }
}
