package br.com.garupa.app.core.cerebro

import android.os.SystemClock
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MotorInteligenciaGemini {

    companion object {
        private const val TAG = "GARUPA_GEMINI"

        // Modelo que vamos testar agora.
        private const val MODELO = "gemini-3.5-flash-lite"

        private const val MAX_TENTATIVAS = 2
        private const val ESPERA_RETRY_MS = 2500L
    }

    private val escopo =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    private val modelo by lazy {

        Log.d(
            TAG,
            "☁️ Criando modelo Gemini | modelo=$MODELO"
        )

        Firebase
            .ai(
                backend = GenerativeBackend.googleAI()
            )
            .generativeModel(
                modelName = MODELO
            )
    }

    init {
        Log.d(
            TAG,
            "☁️ Motor Gemini criado | modelo=$MODELO"
        )
    }

    fun gerarResposta(
        prompt: String,
        aoResponder: (String?) -> Unit
    ) {

        val promptLimpo =
            prompt.trim()

        if (promptLimpo.isBlank()) {

            Log.e(
                TAG,
                "❌ Prompt vazio"
            )

            aoResponder(null)
            return
        }

        escopo.launch {

            val inicioTotal =
                SystemClock.elapsedRealtime()

            Log.d(
                TAG,
                "☁️ Enviando prompt para Gemini | modelo=$MODELO | chars=${promptLimpo.length}"
            )

            var ultimaFalha: Throwable? =
                null

            for (
            tentativa in 1..MAX_TENTATIVAS
            ) {

                try {

                    Log.d(
                        TAG,
                        "🌐 Gemini | tentativa $tentativa/$MAX_TENTATIVAS | modelo=$MODELO"
                    )

                    val inicioTentativa =
                        SystemClock.elapsedRealtime()

                    val resposta =
                        modelo
                            .generateContent(
                                promptLimpo
                            )
                            .text
                            ?.trim()

                    val tempoTentativa =
                        SystemClock.elapsedRealtime() -
                                inicioTentativa

                    if (
                        resposta.isNullOrBlank()
                    ) {

                        throw IllegalStateException(
                            "Gemini retornou resposta vazia"
                        )
                    }

                    val tempoTotal =
                        SystemClock.elapsedRealtime() -
                                inicioTotal

                    Log.d(
                        TAG,
                        "⚡ Gemini respondeu na tentativa $tentativa | tempo=${tempoTentativa}ms"
                    )

                    Log.d(
                        TAG,
                        "☁️ Gemini respondeu | modelo=$MODELO | tempoTotal=${tempoTotal}ms | chars=${resposta.length}"
                    )

                    withContext(
                        Dispatchers.Main
                    ) {

                        aoResponder(
                            resposta
                        )
                    }

                    return@launch

                } catch (
                    cancelamento: CancellationException
                ) {

                    throw cancelamento

                } catch (
                    erro: Throwable
                ) {

                    ultimaFalha =
                        erro

                    val tempo =
                        SystemClock.elapsedRealtime() -
                                inicioTotal

                    Log.e(
                        TAG,
                        "⚠️ Gemini falhou | tentativa=$tentativa/$MAX_TENTATIVAS | modelo=$MODELO | tempo=${tempo}ms | ${erro.javaClass.simpleName}: ${erro.message}"
                    )

                    if (
                        tentativa <
                        MAX_TENTATIVAS
                    ) {

                        Log.d(
                            TAG,
                            "⏳ Aguardando ${ESPERA_RETRY_MS}ms antes da próxima tentativa"
                        )

                        delay(
                            ESPERA_RETRY_MS
                        )
                    }
                }
            }

            val tempoTotal =
                SystemClock.elapsedRealtime() -
                        inicioTotal

            Log.e(
                TAG,
                "❌ GEMINI ESGOTOU AS TENTATIVAS | modelo=$MODELO | tempoTotal=${tempoTotal}ms | erro=${ultimaFalha?.message}"
            )

            withContext(
                Dispatchers.Main
            ) {

                aoResponder(
                    null
                )
            }
        }
    }

    fun encerrar() {

        Log.d(
            TAG,
            "🛑 Encerrando Motor Gemini"
        )

        escopo.cancel()
    }
}