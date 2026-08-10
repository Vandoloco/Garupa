package br.com.garupa.app.core.voz

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class Voz(private val contexto: Context) {

    private var tts: TextToSpeech? = null

    fun iniciar() {

        tts = TextToSpeech(contexto) { status ->

            if (status == TextToSpeech.SUCCESS) {

                tts?.language = Locale("pt", "BR")

                falar("Garupa pronto para rodar.")
            }
        }
    }

    fun falar(mensagem: String) {

        tts?.speak(
            mensagem,
            TextToSpeech.QUEUE_ADD,
            null,
            "GARUPA_${System.currentTimeMillis()}"
        )
    }
}