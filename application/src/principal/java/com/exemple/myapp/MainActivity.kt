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

    private fun cleDepuisMdp(mdp: String, sel: String): SecretKey
