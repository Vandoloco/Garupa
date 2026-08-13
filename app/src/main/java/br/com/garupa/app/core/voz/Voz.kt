package br.com.garupa.app.core.voz

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class Voz(
    private val contexto: Context
) {

    private var tts: TextToSpeech? =
        null

    private var pronta =
        false

    fun iniciar() {

        if (tts != null) {
            return
        }

        tts =
            TextToSpeech(
                contexto.applicationContext
            ) { status ->

                if (
                    status ==
                    TextToSpeech.SUCCESS
                ) {

                    val resultadoIdioma =
                        tts?.setLanguage(
                            Locale(
                                "pt",
                                "BR"
                            )
                        )

                    pronta =
                        resultadoIdioma !=
                                TextToSpeech.LANG_MISSING_DATA &&
                                resultadoIdioma !=
                                TextToSpeech.LANG_NOT_SUPPORTED

                    if (pronta) {

                        Log.d(
                            "GARUPA_VOZ",
                            "🔊 Voz pronta"
                        )

                        falar(
                            "Garupa pronto para rodar."
                        )

                    } else {

                        Log.e(
                            "GARUPA_VOZ",
                            "❌ Português do Brasil indisponível no TTS"
                        )
                    }

                } else {

                    Log.e(
                        "GARUPA_VOZ",
                        "❌ Falha ao iniciar TextToSpeech"
                    )
                }
            }
    }

    fun anunciarAceitar() {

        falarDecisao(
            mensagem =
                "Aceitar."
        )
    }

    fun anunciarDeixarPassar() {

        falarDecisao(
            mensagem =
                "Deixa passar."
        )
    }

    private fun falarDecisao(
        mensagem: String
    ) {

        if (!pronta) {

            Log.d(
                "GARUPA_VOZ",
                "⚠️ Voz ainda não está pronta"
            )

            return
        }

        Log.d(
            "GARUPA_VOZ",
            "🔊 $mensagem"
        )

        /*
         * QUEUE_FLUSH é proposital.
         *
         * A decisão é mais importante que qualquer
         * mensagem que ainda esteja aguardando.
         */
        tts?.speak(
            mensagem,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "GARUPA_DECISAO"
        )
    }

    fun falar(
        mensagem: String
    ) {

        if (!pronta) {
            return
        }

        tts?.speak(
            mensagem,
            TextToSpeech.QUEUE_ADD,
            null,
            "GARUPA_${System.currentTimeMillis()}"
        )
    }

    fun encerrar() {

        pronta =
            false

        tts?.stop()
        tts?.shutdown()

        tts =
            null
    }
}