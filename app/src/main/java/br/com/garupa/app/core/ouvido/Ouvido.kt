package br.com.garupa.app.core.ouvido

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class Ouvido(
    contexto: Context
) {

    private val contextoAplicacao =
        contexto.applicationContext

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private var reconhecedor:
            SpeechRecognizer? =
        null

    private var escutando =
        false

    private var deveContinuarEscutando =
        false

    private var aoReconhecerFala:
            ((String) -> Unit)? =
        null

    fun definirAoReconhecerFala(
        callback: (String) -> Unit
    ) {

        aoReconhecerFala =
            callback
    }

    fun iniciar() {

        if (
            reconhecedor != null
        ) {
            return
        }

        if (
            !SpeechRecognizer.isRecognitionAvailable(
                contextoAplicacao
            )
        ) {

            Log.e(
                "GARUPA_OUVIDO",
                "❌ Reconhecimento de voz indisponível neste aparelho"
            )

            return
        }

        reconhecedor =
            SpeechRecognizer.createSpeechRecognizer(
                contextoAplicacao
            )

        reconhecedor?.setRecognitionListener(
            criarListener()
        )

        Log.d(
            "GARUPA_OUVIDO",
            "🎤 Ouvido inicializado"
        )
    }

    fun comecarEscutaContinua() {

        if (
            reconhecedor == null
        ) {

            iniciar()
        }

        if (
            reconhecedor == null
        ) {

            return
        }

        deveContinuarEscutando =
            true

        iniciarNovaEscuta()
    }

    fun pararEscuta() {

        deveContinuarEscutando =
            false

        escutando =
            false

        handler.removeCallbacksAndMessages(
            null
        )

        try {

            reconhecedor?.stopListening()

        } catch (_: Exception) {
        }

        Log.d(
            "GARUPA_OUVIDO",
            "🔇 Escuta pausada"
        )
    }

    private fun iniciarNovaEscuta() {

        if (
            !deveContinuarEscutando ||
            escutando
        ) {

            return
        }

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "pt-BR"
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "pt-BR"
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )
            }

        try {

            escutando =
                true

            reconhecedor?.startListening(
                intent
            )

            Log.d(
                "GARUPA_OUVIDO",
                "👂 Escutando..."
            )

        } catch (erro: Exception) {

            escutando =
                false

            Log.e(
                "GARUPA_OUVIDO",
                "❌ Erro ao iniciar escuta",
                erro
            )

            agendarNovaEscuta(
                1500L
            )
        }
    }

    private fun criarListener():
            RecognitionListener {

        return object :
            RecognitionListener {

            override fun onReadyForSpeech(
                params: Bundle?
            ) {

                Log.d(
                    "GARUPA_OUVIDO",
                    "🎙️ Pode falar"
                )
            }

            override fun onBeginningOfSpeech() {

                Log.d(
                    "GARUPA_OUVIDO",
                    "🗣️ Fala detectada"
                )
            }

            override fun onRmsChanged(
                rmsdB: Float
            ) {
                // Não logamos para evitar poluir o Logcat.
            }

            override fun onBufferReceived(
                buffer: ByteArray?
            ) {
            }

            override fun onEndOfSpeech() {

                Log.d(
                    "GARUPA_OUVIDO",
                    "🤫 Fim da fala detectado"
                )
            }

            override fun onError(
                error: Int
            ) {

                escutando =
                    false

                /*
                 * Erros 6 e 7 são comuns quando ninguém fala
                 * ou quando nenhuma frase é reconhecida.
                 *
                 * Não são falhas graves na escuta contínua.
                 */
                if (
                    error !=
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                    error !=
                    SpeechRecognizer.ERROR_NO_MATCH
                ) {

                    Log.d(
                        "GARUPA_OUVIDO",
                        "⚠️ Reconhecimento retornou código=$error"
                    )
                }

                /*
                 * Falta de permissão não deve gerar loop.
                 */
                if (
                    error ==
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                ) {

                    deveContinuarEscutando =
                        false

                    Log.e(
                        "GARUPA_OUVIDO",
                        "❌ Sem permissão para usar o microfone"
                    )

                    return
                }

                agendarNovaEscuta(
                    700L
                )
            }

            override fun onResults(
                results: Bundle?
            ) {

                escutando =
                    false

                val textos =
                    results
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        .orEmpty()

                val frase =
                    textos
                        .firstOrNull()
                        ?.trim()

                if (
                    !frase.isNullOrBlank()
                ) {

                    Log.d(
                        "GARUPA_OUVIDO",
                        "🧠 Você disse: $frase"
                    )

                    aoReconhecerFala?.invoke(
                        frase
                    )
                }

                agendarNovaEscuta(
                    500L
                )
            }

            override fun onPartialResults(
                partialResults: Bundle?
            ) {

                val parcial =
                    partialResults
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        ?.firstOrNull()
                        ?.trim()

                if (
                    !parcial.isNullOrBlank()
                ) {

                    Log.v(
                        "GARUPA_OUVIDO_PARCIAL",
                        "… $parcial"
                    )
                }
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?
            ) {
            }
        }
    }

    private fun agendarNovaEscuta(
        atrasoMs: Long
    ) {

        if (
            !deveContinuarEscutando
        ) {
            return
        }

        handler.postDelayed(
            {
                iniciarNovaEscuta()
            },
            atrasoMs
        )
    }

    fun encerrar() {

        deveContinuarEscutando =
            false

        escutando =
            false

        handler.removeCallbacksAndMessages(
            null
        )

        try {

            reconhecedor?.cancel()

        } catch (_: Exception) {
        }

        reconhecedor?.destroy()

        reconhecedor =
            null

        Log.d(
            "GARUPA_OUVIDO",
            "🎤 Ouvido encerrado"
        )
    }
}