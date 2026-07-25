package com.etudamada.etudiamad

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// ========== COULEURS ==========
object Couleur {
    fun palierTexte(n: Int) = when (n) {
        0 -> "[0] Inconnu"
        1 -> "[1] Débutant"
        2 -> "[2] Moyen"
        3 -> "[3a] Expert-Débutant"
        4 -> "[3b] Expert-Intermédiaire"
        5 -> "[3c] Expert-Avancé"
        6 -> "[3d] Expert-GÉNIE"
        else -> "[?]"
    }
    fun titre(t: String) = "=== $t ==="
    fun succes(t: String) = "✅ $t"
    fun alerte(t: String) = "⚠️ $t"
    fun info(t: String) = "ℹ️ $t"
}

// ========== MODÈLES ==========
data class MessageSauvegarde(val ia: String, val texte: String, val estReponse: Boolean)
data class Competence(val id: String, val nom: String, var palier: Int = 0, var derniereNote: Int? = null)
data class Bilan(val date: String, val note: Int, val commentaire: String)
data class Utilisateur(
    val mail: String,
    val nom: String,
    val mdpChiffre: String,
    val sel: String,
    val competences: MutableMap<String, Competence> = mutableMapOf(),
    val historique: MutableList<MessageSauvegarde> = mutableListOf(),
    val bilans: MutableList<Bilan> = mutableListOf()
)

// ========== SÉCURITÉ ==========
object Securite {
    private const val SEL_GLOBAL = "EtudIAMad_Sel_2026"

    private fun cleDepuisMdp(mdp: String, sel: String): SecretKey {
        val spec: KeySpec = PBEKeySpec(mdp.toCharArray(), (SEL_GLOBAL + sel).toByteArray(), 65536, 256)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val raw = skf.generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    fun chiffrerTexte(texte: String, mdp: String, sel: String): String {
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, cleDepuisMdp(mdp, sel), GCMParameterSpec(128, iv))
        }
        val data = c.doFinal(texte.toByteArray(StandardCharsets.UTF_8))
        return (iv + data).joinToString(":") { "%02x".format(it) }
    }

    fun dechiffrerTexte(texte: String, mdp: String, sel: String): String? {
        return try {
            val t = texte.split(":").mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
            if (t.size < 12) return null
            val iv = t.copyOfRange(0, 12)
            val d = t.copyOfRange(12, t.size)
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, cleDepuisMdp(mdp, sel), GCMParameterSpec(128, iv))
            }
            String(c.doFinal(d))
        } catch (e: Exception) {
            null
        }
    }

    fun genererSel() = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        .joinToString(":") { "%02x".format(it) }

    fun escapeJson(s: String) = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

// ========== RÉSEAU ==========
object Reseau {
    fun gemini(k: String, corps: String): String {
        return try {
            val u = URL("https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=$k")
            val con = u.openConnection() as HttpURLConnection
            con.connectTimeout = 15000
            con.readTimeout = 15000
            con.requestMethod = "POST"
            con.setRequestProperty("Content-Type", "application/json")
            con.doOutput = true
            val json = """{"contents":[{"parts":[{"text":"${Securite.escapeJson(corps)}"}]}]}"""
            con.outputStream.writer(StandardCharsets.UTF_8).write(json)
            val r = con.inputStream.reader().readText()
            con.disconnect()
            r
        } catch (e: java.net.SocketTimeoutException) {
            "⚠️ Délai dépassé"
        } catch (e: java.net.UnknownHostException) {
            "⚠️ Pas de réseau"
        } catch (e: Exception) {
            "❌ Erreur: ${e.message}"
        }
    }
}

// ========== CONFIG ==========
object ConfigPalier {
    fun pieges(p: Int) = when (p) {
        1 -> "PIÈGES: ; oublié, mauvaise casse, espace"
        2 -> "PIÈGES: guillemets, ordre, indentation"
        3 -> "PIÈGES: structure, accolades"
        4 -> "PIÈGES: = vs ==, portée"
        5 -> "PIÈGES SÉCU: injection"
        6 -> "PIÈGES: TOUS"
        else -> ""
    }
    fun mode(p: Int) = if (p == 0) "Découverte" else "RÉPARATION"
}

// ========== DÉPÔT ==========
class Depot(private val mdpMaitre: String, private val selApp: String, private val dossier: File) {
    private var cleApi: String? = null
    private val utilisateurs = mutableMapOf<String, Utilisateur>()
    var connecte: Utilisateur? = null
    var iaCourante = "gemini"
    private val fichierProfil = File(dossier, "etudiamad_sauvegarde.dat")

    init {
        chargerDepuisFichier()
        if (utilisateurs.isEmpty()) {
            val selU = Securite.genererSel()
            val mdpChiffre = Securite.chiffrerTexte("AlexPro2026!", mdpMaitre, selU)
            utilisateurs["alexforele@gmail.com"] = Utilisateur(
                "alexforele@gmail.com", "Alex Forele", mdpChiffre, selU
            )
            sauvegarderTout()
        }
    }

    fun connexion(mail: String, mdp: String): Boolean {
        val u = utilisateurs[mail] ?: return false
        val verif = Securite.dechiffrerTexte(u.mdpChiffre, mdpMaitre, u.sel) ?: return false
        if (verif != mdp) return false
        connecte = u
        return true
    }

    fun sauverCle(c: String) {
        cleApi = c
        sauvegarderTout()
    }

    fun ajouterCompetence(nom: String): String {
        val u = connecte ?: return Couleur.alerte("Pas connecté")
        if (u.competences.containsKey(nom)) return Couleur.info("Déjà présente")
        u.competences[nom] = Competence(nom, nom)
        sauvegarderTout()
        return Couleur.succes("$nom ajouté")
    }

    fun setPalier(nom: String, p: Int): String {
        val c = connecte?.competences?.get(nom) ?: return Couleur.alerte("Inconnu")
        if (p !in 0..6) return Couleur.alerte("0 à 6")
        c.palier = p
        sauvegarderTout()
        return Couleur.succes("$nom → ${Couleur.palierTexte(p)}")
    }

    fun lister() = connecte?.competences?.values?.joinToString("\n") {
        "- ${it.nom} : ${Couleur.palierTexte(it.palier)}"
    } ?: Couleur.alerte("Pas connecté")

    fun stats(): String {
        val u = connecte ?: return Couleur.alerte("Pas connecté")
        return """
${Couleur.titre("STATISTIQUES")}
Compte : ${u.nom}
Compétences : ${u.competences.size}
Messages : ${u.historique.size}
        """.trimIndent()
    }

    fun envoyerPrompt(txt: String) = cleApi?.let { Reseau.gemini(it, txt) } ?: "⚠️ Clé API manquante"

    fun generer(competence: String): String {
        val p = connecte?.competences?.get(competence)?.palier ?: 0
        val prompt = "Compétence:$competence Palier:$p ${ConfigPalier.mode(p)} | Pièges: ${ConfigPalier.pieges(p)}"
        return envoyerPrompt(prompt)
    }

    private fun sauvegarderTout() {
        val txt = utilisateurs.mapValues { u ->
            "\( {u.value.nom}| \){u.value.mail}|\( {u.value.mdpChiffre}| \){u.value.sel}"
        }.entries.joinToString("\n") { "\( {it.key}= \){it.value}" }
        fichierProfil.writeText(Securite.chiffrerTexte(txt, mdpMaitre, selApp))
    }

    private fun chargerDepuisFichier() {
        if (!fichierProfil.exists()) return
        val dech = Securite.dechiffrerTexte(fichierProfil.readText(), mdpMaitre, selApp) ?: return
        dech.lines().forEach { ligne ->
            val parts = ligne.split("=", limit = 2)
            if (parts.size != 2) return@forEach
            val infos = parts[1].split("|")
            if (infos.size >= 4) {
                utilisateurs[parts[0]] = Utilisateur(infos[1], infos[0], infos[2], infos[3])
            }
        }
    }
}

// ========== ACTIVITÉ PRINCIPALE ==========
class MainActivity : AppCompatActivity() {

    private lateinit var terminalView: TextView
    private lateinit var depot: Depot

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        terminalView = findViewById(R.id.terminal)

        terminalView.append("${Couleur.titre("ETUDIAMAD — ANDROID")}\n")
        terminalView.append("${Couleur.info("Compte test : alexforele@gmail.com / AlexPro2026!")}\n\n")

        val selApp = Securite.genererSel()
        val mdpMaitre = "EtudIAMad_Android_2026"
        depot = Depot(mdpMaitre, selApp, filesDir)

        demarrerConnexion()
    }

    private fun demarrerConnexion() {
        terminalView.append("${Couleur.info("--- CONNEXION ---")}\n")
        if (depot.connexion("alexforele@gmail.com", "AlexPro2026!")) {
            terminalView.append("${Couleur.succes("Connecté avec succès")}\n")
            afficherMenuPrincipal()
        } else {
            terminalView.append("${Couleur.alerte("Échec de la connexion")}\n")
        }
    }

    private fun afficherMenuPrincipal() {
        terminalView.append("\n${Couleur.titre("MENU PRINCIPAL")}\n")
        terminalView.append("1 — Changer IA\n")
        terminalView.append("2 — Entrer clé API Gemini\n")
        terminalView.append("3 — Ajouter compétence\n")
        terminalView.append("4 — Générer exercice\n")
        terminalView.append("5 — Corriger code\n")
        terminalView.append("6 — Exporter profil\n")
        terminalView.append("7 — Importer profil\n")
        terminalView.append("8 — Statistiques\n")
        terminalView.append("9 — Lister compétences\n")
        terminalView.append("0 — Quitter\n")
    }
}
