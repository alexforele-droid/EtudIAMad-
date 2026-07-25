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

// ========== COULEURS (version Android-friendly) ==========
object Couleur {
    const val RESET = ""
    const val ROUGE = ""
    const val VERT = ""
    const val JAUNE = ""
    const val BLEU = ""
    const val CYAN = ""
    const val MAGENTA = ""
    const val BLANC = ""
    const val GRIS = ""
    const val GRAS = ""

    fun palierTexte(n: Int) = when (n) {
        0 -> "[0] Inconnu"
        1 -> "[1] Débutant — casse / espace"
        2 -> "[2] Moyen — guillemets / ordre"
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
    fun modeReparation() = "🔧 RÉPARATION — Code à corriger"
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
            "⚠️ Délai dépassé — vérifie la connexion"
        } catch (e: java.net.UnknownHostException) {
            "⚠️ Pas de réseau"
        } catch (e: Exception) {
            "❌ Erreur: ${e.message}"
        }
    }
}

// ========== CONFIG PIÈGES ==========
object ConfigPalier {
    fun pieges(p: Int) = when (p) {
        1 -> "PIÈGES: ; oublié, mauvaise casse, espace, orthographe"
        2 -> "PIÈGES: guillemets mélangés, ordre, indentation, variable inutile"
        3 -> "PIÈGES: structure, accolades, condition inversée"
        4 -> "PIÈGES: = vs ==, portée, boucle infinie"
        5 -> "PIÈGES SÉCU: injection, données en clair"
        6 -> "PIÈGES: TOUS combinés"
        else -> ""
    }

    fun mode(p: Int) = if (p == 0) "Découverte" else "RÉPARATION: code cassé avec pièges cachés"
}

// ========== DÉPÔT ==========
class Depot(private val mdpMaitre: String, private val selApp: String, private val dossier: File) {
    private var cleApi: String? = null
    private val utilisateurs = mutableMapOf<String, Utilisateur>()
    var connecte: Utilisateur? = null
    var iaCourante = "gemini"
    val listeIA = listOf("gemini", "gpt", "claude", "grok", "deepseek")
    private val fichierProfil = File(dossier, "etudiamad_sauvegarde.dat")

    init {
        chargerDepuisFichier()
        if (utilisateurs.isEmpty()) {
            val selU = Securite.genererSel()
            val mdpChiffre = Securite.chiffrerTexte("AlexPro2026!", mdpMaitre, selU)
            utilisateurs["alexforele@gmail.com"] = Utilisateur(
                "alexforele@gmail.com",
                "Alex Forele",
                mdpChiffre,
                selU
            )
            sauvegarderTout()
        }
    }

    fun inscrire(mail: String, nom: String, mdp: String): String {
        if (utilisateurs.containsKey(mail)) return Couleur.alerte("Email déjà pris")
        val selU = Securite.genererSel()
        val mdpChiffre = Securite.chiffrerTexte(mdp, mdpMaitre, selU)
        utilisateurs[mail] = Utilisateur(mail, nom, mdpChiffre, selU)
        sauvegarderTout()
        return Couleur.succes("Inscription OK pour $nom")
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

    fun verrouPalier(nom: String, note: Int): String {
        val c = connecte?.competences?.get(nom) ?: return Couleur.alerte("Inconnu")
        c.derniereNote = note
        sauvegarderTout()
        return if (note >= 14) setPalier(nom, c.palier + 1)
        else Couleur.info("Note $note/20 — 14 minimum pour monter")
    }

    fun lister() = connecte?.competences?.values?.joinToString("\n") {
        "- ${it.nom} : ${Couleur.palierTexte(it.palier)} | Note: ${it.derniereNote ?: "—"}"
    } ?: Couleur.alerte("Pas connecté")

    fun stats(): String {
        val u = connecte ?: return Couleur.alerte("Pas connecté")
        val nbNotes = u.bilans.size
        val moy = if (nbNotes > 0) u.bilans.sumOf { it.note } / nbNotes else 0
        return """
${Couleur.titre("STATISTIQUES")}
Compte : ${u.nom}
Compétences : ${u.competences.size}
Corrections/Bilans : $nbNotes | Moyenne : $moy/20
Messages échangés : ${u.historique.size}
        """.trimIndent()
    }

    fun ajouterMessage(txt: String, estReponse: Boolean = false) {
        connecte?.historique?.add(MessageSauvegarde(iaCourante, txt, estReponse))
        sauvegarderTout()
    }

    fun ajouterBilan(n: Int, c: String) {
        connecte?.bilans?.add(Bilan(LocalDate.now().toString(), n, c))
        sauvegarderTout()
    }

    fun exporter(): String {
        val u = connecte ?: return Couleur.alerte("Pas connecté")
        fun enc(s: String) = s.replace("|", "&#124;")
        val txt = buildString {
            appendLine("nom:${enc(u.nom)}")
            appendLine("mail:${enc(u.mail)}")
            appendLine("sel:${u.sel}")
            appendLine("---COMP---")
            append(u.competences.values.joinToString("|") {
                "\( {enc(it.nom)}: \){it.palier}:${it.derniereNote ?: ""}"
            })
            appendLine("\n---HISTO---")
            append(u.historique.joinToString("|") {
                "\( {it.ia}: \){enc(it.texte)}:${it.estReponse}"
            })
            appendLine("\n---BILANS---")
            append(u.bilans.joinToString("|") {
                "\( {it.date}: \){it.note}:${enc(it.commentaire)}"
            })
        }
        val nomF = "etudiamad_export_${u.mail.take(10).replace(Regex("[^A-Za-z0-9]"), "_")}.etud"
        File(dossier, nomF).writeText(Securite.chiffrerTexte(txt, mdpMaitre, selApp))
        return Couleur.succes("Exporté → $nomF")
    }

    fun importer(nomF: String): String {
        val f = File(dossier, nomF)
        if (!f.exists()) return Couleur.alerte("Fichier introuvable")
        val brut = Securite.dechiffrerTexte(f.readText(), mdpMaitre, selApp)
            ?: return Couleur.alerte("Fichier corrompu")
        val lignes = brut.lines().associateBy(
            { it.substringBefore(":") },
            { it.substringAfter(":") }
        )
        val nom = lignes["nom"] ?: return Couleur.alerte("Format invalide")
        val mail = lignes["mail"] ?: return Couleur.alerte("Format invalide")
        val selU = lignes["sel"] ?: Securite.genererSel()
        val mdpFictif = Securite.chiffrerTexte("ImportTemp", mdpMaitre, selU)

        utilisateurs[mail] = Utilisateur(mail, nom, mdpFictif, selU).apply {
            lignes["---COMP---"]?.split("|")?.forEach {
                val p = it.split(":")
                if (p.size >= 3) {
                    competences[p[0]] = Competence(p[0], p[0], p[1].toIntOrNull() ?: 0, p[2].toIntOrNull())
                }
            }
            lignes["---HISTO---"]?.split("|")?.forEach {
                val p = it.split(":", limit = 3)
                if (p.size == 3) historique.add(MessageSauvegarde(p[0], p[1], p[2].toBoolean()))
            }
            lignes["---BILANS---"]?.split("|")?.forEach {
                val p = it.split(":", limit = 3)
                if (p.size == 3) bilans.add(Bilan(p[0], p[1].toIntOrNull() ?: 0, p[2]))
            }
        }
        sauvegarderTout()
        return Couleur.succes("Importé — $nom")
    }

    fun envoyerPrompt(txt: String) = cleApi?.let { Reseau.gemini(it, txt) } ?: "⚠️ Clé API manquante"

    fun generer(competence: String): String {
        val p = connecte?.competences?.get(competence)?.palier ?: 0
        val prompt = "Compétence:$competence Palier:$p ${ConfigPalier.mode(p)} | Pièges: ${ConfigPalier.pieges(p)} — Code avec pièges CACHÉS."
        return envoyerPrompt(prompt)
    }

    fun corriger(competence: String, code: String): String {
        val p = connecte?.competences?.get(competence)?.palier ?: 0
        val prompt = "Corrige $competence palier $p. Note/20. Pièges: ${ConfigPalier.pieges(p)}. Code :\n$code"
        return envoyerPrompt(prompt)
    }

    private fun sauvegarderTout() {
        val txt = utilisateurs.mapValues { u ->
            """\( {u.value.nom}| \){u.value.mail}|\( {u.value.mdpChiffre}| \){u.value.sel}
comp:\( {u.value.competences.values.joinToString(";") { " \){it.nom},\( {it.palier}, \){it.derniereNote}" }}
hist:\( {u.value.historique.joinToString(";") { " \){it.ia}:\( {it.texte}: \){it.estReponse}" }}
bilan:\( {u.value.bilans.joinToString(";") { " \){it.date}:\( {it.note}: \){it.commentaire}" }}"""
        }.entries.joinToString("\n") { "\( {it.key}= \){it.value}" }

        fichierProfil.writeText(Securite.chiffrerTexte(txt, mdpMaitre, selApp))
    }

    private fun chargerDepuisFichier() {
        if (!fichierProfil.exists()) return
        val dech = Securite.dechiffrerTexte(fichierProfil.readText(), mdpMaitre, selApp) ?: return
        dech.lines().forEach { ligne ->
            val parts = ligne.split("=", limit = 2)
            if (parts.size != 2) return@forEach
            val mail = parts[0]
            val data = parts[1].split("\n")
            val infos = data[0].split("|")
            if (infos.size < 4) return@forEach

            val u = Utilisateur(infos[1], infos[0], infos[2], infos[3]) // attention à l'ordre

            data.find { it.startsWith("comp:") }?.removePrefix("comp:")?.split(";")?.forEach {
                val p = it.split(",")
                if (p.size >= 3) {
                    u.competences[p[0]] = Competence(p[0], p[0], p[1].toIntOrNull() ?: 0, p[2].toIntOrNull())
                }
            }
            data.find { it.startsWith("hist:") }?.removePrefix("hist:")?.split(";")?.forEach {
                val p = it.split(":", limit = 3)
                if (p.size == 3) u.historique.add(MessageSauvegarde(p[0], p[1], p[2].toBoolean()))
            }
            data.find { it.startsWith("bilan:") }?.removePrefix("bilan:")?.split(";")?.forEach {
                val p = it.split(":", limit = 3)
                if (p.size == 3) u.bilans.add(Bilan(p[0], p[1].toIntOrNull() ?: 0, p[2]))
            }
            utilisateurs[mail] = u
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
        terminalView.append("${Couleur.info("Compte test : alexforele@gmail.com / AlexPro2026!")}\n")
        terminalView.append("${Couleur.info("Commandes disponibles bientôt")}\n\n")

        val selApp = Securite.genererSel()
        val mdpMaitre = "EtudIAMad_Android_2026"
        depot = Depot(mdpMaitre, selApp, filesDir)

        demarrerConnexion()
    }

    private fun demarrerConnexion() {
        terminalView.append("${Couleur.info("--- CONNEXION ---")}\n")
        terminalView.append("Email : alexforele@gmail.com\n")
        terminalView.append("Mdp   : AlexPro2026!\n")

        if (depot.connexion("alexforele@gmail.com", "AlexPro2026!")) {
            terminalView.append("${Couleur.succes("Connecté avec succès")}\n")
            afficherMenuPrincipal()
        } else {
            terminalView.append("${Couleur.alerte("Échec de la connexion")}\n")
        }
    }

    private fun afficherMenuPrincipal() {
        terminalView.append("\n${Couleur.titre("MENU PRINCIPAL")}\n")
        terminalView.append("1 — Changer IA (actuelle: ${depot.iaCourante})\n")
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
