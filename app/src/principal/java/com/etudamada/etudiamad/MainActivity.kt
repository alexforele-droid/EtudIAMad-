package com.etudamada.etudiamad

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // On met un texte en dur, pas de layout XML
        val textView = TextView(this)
        textView.text = "EtudIAMad fonctionne !"
        setContentView(textView)
    }
}
