package com.sophonie.assistant

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors

/** Écran principal : discussion (texte ou voix) avec Sophonie, et réglages. */
class MainActivity : Activity() {

    private val executeur = Executors.newSingleThreadExecutor()
    private val principal = Handler(Looper.getMainLooper())
    private lateinit var reglages: Reglages
    private lateinit var fil: LinearLayout
    private lateinit var defilement: ScrollView
    private lateinit var blocReglages: LinearLayout
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reglages = Reglages(this)
        Rythme.creerCanaux(this)
        Rythme.reprogrammerTout(this)
        tts = TextToSpeech(this) { statut -> if (statut == TextToSpeech.SUCCESS) tts?.language = Locale.FRENCH }

        val racine = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 56, 32, 24) }
        racine.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "Sophonie"; textSize = 26f; setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(bouton("⚙️") { blocReglages.visibility = if (blocReglages.visibility == View.GONE) View.VISIBLE else View.GONE })
        })

        blocReglages = construireReglages()
        racine.addView(blocReglages)

        fil = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        defilement = ScrollView(this).apply { addView(fil) }
        racine.addView(defilement, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val saisie = EditText(this).apply { hint = "Parlez à Sophonie…"; maxLines = 4 }
        racine.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(saisie, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(bouton("🎤") { ecouter() })
            addView(bouton("➤") {
                val texte = saisie.text.toString().trim()
                if (texte.isNotEmpty()) { saisie.setText(""); discuter(texte) }
            })
        })
        setContentView(racine)

        Memoire.de(this).historique(10).forEach { (role, texte) -> bulle(if (role == "user") "Vous" else "Sophonie", texte) }
        if (!reglages.configure) {
            blocReglages.visibility = View.VISIBLE
            bulle("Sophonie", "Bienvenue ! Collez votre clé API Claude, indiquez votre prénom, touchez « Enregistrer », puis « Autorisations ».")
        } else if (fil.childCount == 0) {
            bulle("Sophonie", "Bonjour ! Je suis là. Que puis-je faire pour vous ?")
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun construireReglages() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        val cle = EditText(context).apply {
            hint = "Clé API Claude (sk-ant-…)"
            setText(reglages.cleApi)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val prenom = EditText(context).apply { hint = "Votre prénom"; setText(reglages.prenom) }
        val prioritaires = EditText(context).apply {
            hint = "Contacts prioritaires (séparés par des virgules)"; setText(reglages.contactsPrioritaires)
        }
        val tri = CheckBox(context).apply {
            text = "Analyser chaque message reçu et m'alerter si important"; isChecked = reglages.triIntelligent
        }
        val voix = CheckBox(context).apply { text = "Lire les réponses à voix haute"; isChecked = reglages.voix }
        listOf(cle, prenom, prioritaires, tri, voix).forEach { addView(it) }
        addView(rangee(
            bouton("Enregistrer") {
                reglages.cleApi = cle.text.toString(); reglages.prenom = prenom.text.toString()
                reglages.contactsPrioritaires = prioritaires.text.toString()
                reglages.triIntelligent = tri.isChecked; reglages.voix = voix.isChecked
                Toast.makeText(context, if (reglages.configure) "Enregistré" else "La clé doit commencer par sk-ant-",
                    Toast.LENGTH_SHORT).show()
                if (reglages.configure) visibility = View.GONE
            },
            bouton("Autorisations") { demanderAutorisations() },
        ))
        addView(rangee(
            bouton("Batterie") { ignorerOptimisationBatterie() },
            bouton("Alarmes") { autoriserAlarmesExactes() },
        ))
    }

    private fun discuter(texte: String) {
        if (!reglages.configure) {
            bulle("Sophonie", "Il me faut d'abord votre clé API Claude (⚙️)."); return
        }
        bulle("Vous", texte)
        val attente = bulle("Sophonie", "…")
        executeur.execute {
            val reponse = try {
                Cerveau(applicationContext).discuter(texte)
            } catch (e: Exception) {
                "Je n'arrive pas à joindre Claude (${e.message}). Vérifiez la connexion et la clé API."
            }
            principal.post {
                attente.text = reponse
                defiler()
                if (reglages.voix) tts?.speak(reponse.replace(Regex("[*#_`]"), ""), TextToSpeech.QUEUE_FLUSH, null, "rep")
            }
        }
    }

    private fun ecouter() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Je vous écoute…")
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, CODE_VOIX)
        } catch (e: Exception) {
            Toast.makeText(this, "Reconnaissance vocale indisponible sur ce téléphone", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("API Activity classique")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CODE_VOIX && resultCode == RESULT_OK) {
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { discuter(it) }
        }
    }

    private fun demanderAutorisations() {
        val permissions = mutableListOf(
            Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR, Manifest.permission.RECORD_AUDIO,
        )
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        requestPermissions(permissions.toTypedArray(), 1)
        val ecoute = ComponentName(this, EcouteNotifications::class.java).flattenToString()
        val actives = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        if (!actives.contains(ecoute)) {
            Toast.makeText(this, "Activez Sophonie dans « Accès aux notifications »", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun ignorerOptimisationBatterie() {
        try {
            @Suppress("BatteryLife")
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun autoriserAlarmesExactes() {
        if (Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        } else {
            Toast.makeText(this, "Les alarmes à l'heure exacte sont déjà autorisées", Toast.LENGTH_SHORT).show()
        }
        Rythme.reprogrammerTout(this)
    }

    private fun bouton(texte: String, action: () -> Unit) = Button(this).apply {
        text = texte; isAllCaps = false; setOnClickListener { action() }
    }

    private fun rangee(vararg boutons: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        boutons.forEach { addView(it, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) }
    }

    private fun bulle(auteur: String, texte: String): TextView {
        val vue = TextView(this).apply {
            text = texte; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
            setBackgroundColor(if (auteur == "Vous") 0xFFE3F2FD.toInt() else 0xFFF3E5F5.toInt())
        }
        val params = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            setMargins(if (auteur == "Vous") 80 else 0, 8, if (auteur == "Vous") 0 else 80, 8)
        }
        fil.addView(vue, params)
        defiler()
        return vue
    }

    private fun defiler() = defilement.post { defilement.fullScroll(ScrollView.FOCUS_DOWN) }

    companion object {
        private const val CODE_VOIX = 42
    }
}
