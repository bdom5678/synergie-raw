package com.sophonie.assistant

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/** Écran principal : discussion avec Sophonie et réglages. */
class MainActivity : Activity() {

    private val executeur = Executors.newSingleThreadExecutor()
    private val principal = Handler(Looper.getMainLooper())
    private lateinit var reglages: Reglages
    private lateinit var fil: LinearLayout
    private lateinit var defilement: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reglages = Reglages(this)
        Notifications.creerCanaux(this)
        AlertesWorker.planifier(this)
        Reveils.planifierTous(this)

        val racine = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 24)
        }
        racine.addView(TextView(this).apply {
            text = "Sophonie"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
        })

        val serveur = champ("Adresse du serveur (https://…)", reglages.serveur)
        val jeton = champ("Jeton secret", reglages.jeton).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val reglagesBloc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        reglagesBloc.addView(serveur)
        reglagesBloc.addView(jeton)
        reglagesBloc.addView(rangee(
            bouton("Enregistrer") {
                reglages.serveur = serveur.text.toString()
                reglages.jeton = jeton.text.toString()
                Toast.makeText(this, "Réglages enregistrés", Toast.LENGTH_SHORT).show()
            },
            bouton("Autorisations") { demanderAutorisations() },
            bouton("Contacts") { synchroniserContacts() },
        ))
        racine.addView(reglagesBloc)

        fil = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        defilement = ScrollView(this).apply { addView(fil) }
        racine.addView(defilement, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val saisie = champ("Parlez à Sophonie…", "")
        val envoyer = bouton("Envoyer") {
            val texte = saisie.text.toString().trim()
            if (texte.isNotEmpty()) {
                saisie.setText("")
                discuter(texte)
            }
        }
        racine.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(saisie, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(envoyer)
        })
        setContentView(racine)

        if (reglages.configure) reglagesBloc.visibility = android.view.View.GONE
        bulle("Sophonie", if (reglages.configure) "Bonjour ! Comment puis-je vous aider ?"
        else "Bienvenue. Indiquez l'adresse de votre serveur et votre jeton, puis touchez « Autorisations ».")
    }

    private fun discuter(texte: String) {
        bulle("Vous", texte)
        val attente = bulle("Sophonie", "…")
        executeur.execute {
            val reponse = try {
                Api(reglages).discuter(texte)
            } catch (e: Exception) {
                "Je n'arrive pas à joindre le serveur (${e.message})."
            }
            principal.post { attente.text = reponse; defiler() }
        }
    }

    private fun synchroniserContacts() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 2)
            return
        }
        executeur.execute {
            val message = try {
                "${Api(reglages).envoyerContacts(lireContacts(this))} contacts transmis à Sophonie."
            } catch (e: Exception) {
                "Échec de la synchronisation : ${e.message}"
            }
            principal.post { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
        }
    }

    private fun demanderAutorisations() {
        val permissions = mutableListOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG)
        if (android.os.Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        requestPermissions(permissions.toTypedArray(), 1)
        val ecoute = ComponentName(this, EcouteNotifications::class.java).flattenToString()
        val actives = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        if (!actives.contains(ecoute)) {
            Toast.makeText(this, "Activez Sophonie dans « Accès aux notifications »", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun champ(indice: String, valeur: String) = EditText(this).apply {
        hint = indice
        setText(valeur)
    }

    private fun bouton(texte: String, action: () -> Unit) = Button(this).apply {
        text = texte
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun rangee(vararg boutons: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        boutons.forEach { addView(it, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) }
    }

    private fun bulle(auteur: String, texte: String): TextView {
        val vue = TextView(this).apply {
            text = texte
            textSize = 16f
            setPadding(24, 16, 24, 16)
            gravity = if (auteur == "Vous") Gravity.END else Gravity.START
            setBackgroundColor(if (auteur == "Vous") 0xFFE3F2FD.toInt() else 0xFFF3E5F5.toInt())
        }
        fil.addView(vue, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(0, 8, 0, 8) })
        defiler()
        return vue
    }

    private fun defiler() = defilement.post { defilement.fullScroll(ScrollView.FOCUS_DOWN) }
}
