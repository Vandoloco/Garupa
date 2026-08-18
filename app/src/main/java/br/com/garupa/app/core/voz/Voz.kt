package br.com.garupa.app.core.voz

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class Voz(
    private val contexto: Context
) {

    private var tts: TextToSpeech? =
        null

    @Volatile
    private var pronta =
        false

    /*
     * Guarda callbacks associados a cada fala.
     *
     * Quando o Android informa que aquela fala terminou,
     * executamos o callback correspondente.
     */
    private val callbacksFim =
        ConcurrentHashMap<String, () -> Unit>()

    fun iniciar() {

        if (
            tts != null
        ) {
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

                    configurarListener()

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

                    if (
                        pronta
                    ) {

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

    /*
     * =========================================================
     * CALLBACK REAL DO TTS
     * =========================================================
     */

    private fun configurarListener() {

        tts?.setOnUtteranceProgressListener(

            object :
                UtteranceProgressListener() {

                override fun onStart(
                    utteranceId: String?
                ) {

                    if (
                        utteranceId != null
                    ) {

                        Log.d(
                            "GARUPA_VOZ",
                            "▶️ Fala iniciada | id=$utteranceId"
                        )
                    }
                }

                override fun onDone(
                    utteranceId: String?
                ) {

                    if (
                        utteranceId == null
                    ) {
                        return
                    }

                    Log.d(
                        "GARUPA_VOZ",
                        "✅ Fala concluída | id=$utteranceId"
                    )

                    executarCallbackFim(
                        utteranceId
                    )
                }

                @Deprecated(
                    "Método legado exigido pela interface Android"
                )
                override fun onError(
                    utteranceId: String?
                ) {

                    if (
                        utteranceId == null
                    ) {
                        return
                    }

                    Log.e(
                        "GARUPA_VOZ",
                        "❌ Erro durante fala | id=$utteranceId"
                    )

                    /*
                     * Mesmo se o TTS falhar,
                     * devolvemos o Ouvido para não deixar
                     * o Garupa permanentemente surdo.
                     */
                    executarCallbackFim(
                        utteranceId
                    )
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int
                ) {

                    if (
                        utteranceId == null
                    ) {
                        return
                    }

                    Log.e(
                        "GARUPA_VOZ",
                        "❌ Erro durante fala | " +
                                "id=$utteranceId | " +
                                "codigo=$errorCode"
                    )

                    executarCallbackFim(
                        utteranceId
                    )
                }
            }
        )
    }

    private fun executarCallbackFim(
        utteranceId: String
    ) {

        callbacksFim
            .remove(
                utteranceId
            )
            ?.invoke()
    }

    /*
     * =========================================================
     * DECISÕES
     * =========================================================
     */

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

        if (
            !pronta
        ) {

            Log.d(
                "GARUPA_VOZ",
                "⚠️ Voz ainda não está pronta"
            )

            return
        }

        val id =
            "GARUPA_DECISAO_${System.currentTimeMillis()}"

        Log.d(
            "GARUPA_VOZ",
            "🔊 $mensagem"
        )

        /*
         * Decisão continua tendo prioridade
         * sobre qualquer fala na fila.
         */
        tts?.speak(
            mensagem,
            TextToSpeech.QUEUE_FLUSH,
            null,
            id
        )
    }

    /*
     * =========================================================
     * CONVERSA NORMAL
     * =========================================================
     */

    fun falar(
        mensagem: String,
        aoTerminar: (() -> Unit)? = null
    ) {

        val mensagemLimpa =
            mensagem
                .trim()

        if (
            mensagemLimpa.isBlank()
        ) {

            aoTerminar?.invoke()

            return
        }

        if (
            !pronta
        ) {

            Log.d(
                "GARUPA_VOZ",
                "⚠️ Voz ainda não está pronta"
            )

            aoTerminar?.invoke()

            return
        }

        val id =
            "GARUPA_${System.currentTimeMillis()}"

        if (
            aoTerminar != null
        ) {

            callbacksFim[
                id
            ] =
                aoTerminar
        }

        Log.d(
            "GARUPA_VOZ",
            "🔊 Falando: $mensagemLimpa"
        )

        val resultado =
            tts?.speak(
                mensagemLimpa,
                TextToSpeech.QUEUE_ADD,
                null,
                id
            )

        if (
            resultado ==
            TextToSpeech.ERROR
        ) {

            Log.e(
                "GARUPA_VOZ",
                "❌ TTS recusou a fala"
            )

            executarCallbackFim(
                id
            )
        }
    }

    /*
     * =========================================================
     * CONTROLE
     * =========================================================
     */

    fun pararFala() {

        try {

            tts?.stop()

        } catch (_: Exception) {
        }
    }

    fun encerrar() {

        pronta =
            false

        callbacksFim.clear()

        tts?.stop()
        tts?.shutdown()

        tts =
            null
    }
}