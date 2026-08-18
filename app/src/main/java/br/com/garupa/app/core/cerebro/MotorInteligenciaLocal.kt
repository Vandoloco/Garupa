package br.com.garupa.app.core.cerebro

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MotorInteligenciaLocal(
    contexto: Context
) {

    private val contextoAplicacao =
        contexto.applicationContext

    private val escopo =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO
        )

    private var engine:
            Engine? =
        null

    @Volatile
    private var inicializando =
        false

    @Volatile
    private var pronto =
        false

    @Volatile
    private var gerandoResposta =
        false

    @Volatile
    private var backendAtual =
        "NENHUM"

    private fun arquivoModelo():
            File {

        return File(
            contextoAplicacao.filesDir,
            "models/garupa_modelo.litertlm"
        )
    }

    /*
     * =========================================================
     * INICIALIZAÇÃO
     * =========================================================
     *
     * GPU primeiro.
     * CPU somente como fallback.
     */
    fun inicializar(
        aoConcluir: (
            sucesso: Boolean
        ) -> Unit = {}
    ) {

        if (
            pronto
        ) {

            Log.d(
                "GARUPA_IA_LOCAL",
                "✅ Motor já pronto | backend=$backendAtual"
            )

            aoConcluir(
                true
            )

            return
        }

        if (
            inicializando
        ) {

            Log.d(
                "GARUPA_IA_LOCAL",
                "⏳ Motor já está inicializando"
            )

            return
        }

        val modelo =
            arquivoModelo()

        Log.d(
            "GARUPA_IA_LOCAL",
            "📦 Modelo: ${modelo.absolutePath}"
        )

        if (
            !modelo.exists() ||
            modelo.length() <= 0L
        ) {

            Log.e(
                "GARUPA_IA_LOCAL",
                "❌ Modelo não encontrado ou vazio"
            )

            aoConcluir(
                false
            )

            return
        }

        inicializando =
            true

        escopo.launch {

            val inicioTotal =
                SystemClock.elapsedRealtime()

            /*
             * GPU
             */
            val gpuFuncionou =
                tentarInicializarEngine(
                    modelo =
                        modelo,

                    backend =
                        Backend.GPU(),

                    nomeBackend =
                        "GPU"
                )

            if (
                gpuFuncionou
            ) {

                pronto =
                    true

                inicializando =
                    false

                val tempoTotal =
                    SystemClock.elapsedRealtime() -
                            inicioTotal

                Log.d(
                    "GARUPA_IA_LOCAL",
                    "✅ Gemma inicializado | " +
                            "backend=GPU | " +
                            "tempoTotal=${tempoTotal}ms"
                )

                withContext(
                    Dispatchers.Main
                ) {

                    aoConcluir(
                        true
                    )
                }

                return@launch
            }

            /*
             * CPU fallback
             */
            Log.w(
                "GARUPA_IA_LOCAL",
                "⚠️ GPU falhou; tentando CPU"
            )

            val cpuFuncionou =
                tentarInicializarEngine(
                    modelo =
                        modelo,

                    backend =
                        Backend.CPU(),

                    nomeBackend =
                        "CPU"
                )

            pronto =
                cpuFuncionou

            inicializando =
                false

            val tempoTotal =
                SystemClock.elapsedRealtime() -
                        inicioTotal

            if (
                cpuFuncionou
            ) {

                Log.d(
                    "GARUPA_IA_LOCAL",
                    "✅ Gemma inicializado | " +
                            "backend=CPU | " +
                            "tempoTotal=${tempoTotal}ms"
                )

            } else {

                Log.e(
                    "GARUPA_IA_LOCAL",
                    "❌ Gemma não inicializou"
                )
            }

            withContext(
                Dispatchers.Main
            ) {

                aoConcluir(
                    cpuFuncionou
                )
            }
        }
    }

    private suspend fun tentarInicializarEngine(
        modelo: File,
        backend: Backend,
        nomeBackend: String
    ): Boolean {

        val inicio =
            SystemClock.elapsedRealtime()

        var novoEngine:
                Engine? =
            null

        try {

            Log.d(
                "GARUPA_IA_LOCAL",
                "🧠 Inicializando | backend=$nomeBackend"
            )

            val configuracao =
                EngineConfig(
                    modelPath =
                        modelo.absolutePath,

                    backend =
                        backend,

                    maxNumTokens =
                        2048,

                    cacheDir =
                        contextoAplicacao
                            .cacheDir
                            .absolutePath
                )

            novoEngine =
                Engine(
                    configuracao
                )

            novoEngine.initialize()

            try {

                engine?.close()

            } catch (_: Throwable) {
            }

            engine =
                novoEngine

            novoEngine =
                null

            backendAtual =
                nomeBackend

            val tempo =
                SystemClock.elapsedRealtime() -
                        inicio

            Log.d(
                "GARUPA_IA_LOCAL",
                "✅ Backend $nomeBackend pronto | " +
                        "tempo=${tempo}ms"
            )

            return true

        } catch (
            erro: Throwable
        ) {

            try {

                novoEngine?.close()

            } catch (_: Throwable) {
            }

            val tempo =
                SystemClock.elapsedRealtime() -
                        inicio

            Log.e(
                "GARUPA_IA_LOCAL",
                "❌ Falha $nomeBackend | " +
                        "tempo=${tempo}ms | " +
                        "${erro.javaClass.simpleName}: ${erro.message}"
            )

            return false
        }
    }

    /*
     * =========================================================
     * INFERÊNCIA
     * =========================================================
     *
     * Agora medimos:
     *
     * - tempo até PRIMEIRO pedaço;
     * - tempo TOTAL;
     * - quantidade de pedaços recebidos.
     */
    fun gerarResposta(
        prompt: String,
        aoResponder: (
            resposta: String?
        ) -> Unit
    ) {

        val promptLimpo =
            prompt.trim()

        if (
            promptLimpo.isBlank()
        ) {

            aoResponder(
                null
            )

            return
        }

        val motor =
            engine

        if (
            !pronto ||
            motor == null
        ) {

            Log.d(
                "GARUPA_IA_LOCAL",
                "⚠️ Motor ainda não pronto"
            )

            aoResponder(
                null
            )

            return
        }

        if (
            gerandoResposta
        ) {

            Log.d(
                "GARUPA_IA_LOCAL",
                "⏳ Gemma já está processando outra fala"
            )

            aoResponder(
                null
            )

            return
        }

        gerandoResposta =
            true

        val inicio =
            SystemClock.elapsedRealtime()

        Log.d(
            "GARUPA_IA_LOCAL",
            "💭 Inferência iniciada | " +
                    "backend=$backendAtual | " +
                    "promptChars=${promptLimpo.length}"
        )

        escopo.launch {

            try {

                var primeiroPedacoRecebido =
                    false

                var quantidadePedacos =
                    0

                val resposta =
                    motor
                        .createConversation()
                        .use { conversa ->

                            val acumulado =
                                StringBuilder()

                            conversa
                                .sendMessageAsync(
                                    promptLimpo
                                )
                                .collect { parte ->

                                    quantidadePedacos++

                                    val textoParte =
                                        parte.toString()

                                    if (
                                        !primeiroPedacoRecebido
                                    ) {

                                        primeiroPedacoRecebido =
                                            true

                                        val tempoPrimeiro =
                                            SystemClock.elapsedRealtime() -
                                                    inicio

                                        Log.d(
                                            "GARUPA_IA_LOCAL",
                                            "⚡ PRIMEIRO TOKEN/PEDAÇO | " +
                                                    "backend=$backendAtual | " +
                                                    "tempo=${tempoPrimeiro}ms | " +
                                                    "texto=$textoParte"
                                        )
                                    }

                                    acumulado.append(
                                        textoParte
                                    )
                                }

                            acumulado
                                .toString()
                                .trim()
                        }

                gerandoResposta =
                    false

                val tempoTotal =
                    SystemClock.elapsedRealtime() -
                            inicio

                if (
                    resposta.isBlank()
                ) {

                    Log.d(
                        "GARUPA_IA_LOCAL",
                        "⚠️ Resposta vazia | " +
                                "tempoTotal=${tempoTotal}ms"
                    )

                } else {

                    Log.d(
                        "GARUPA_IA_LOCAL",
                        "🤖 RESPOSTA COMPLETA | " +
                                "backend=$backendAtual | " +
                                "tempoTotal=${tempoTotal}ms | " +
                                "pedacos=$quantidadePedacos | " +
                                "texto=$resposta"
                    )
                }

                withContext(
                    Dispatchers.Main
                ) {

                    aoResponder(
                        resposta.takeIf {
                            it.isNotBlank()
                        }
                    )
                }

            } catch (
                erro: Throwable
            ) {

                gerandoResposta =
                    false

                val tempo =
                    SystemClock.elapsedRealtime() -
                            inicio

                Log.e(
                    "GARUPA_IA_LOCAL",
                    "❌ Erro de inferência | " +
                            "backend=$backendAtual | " +
                            "tempo=${tempo}ms",
                    erro
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
    }

    fun estaPronto():
            Boolean {

        return pronto
    }

    fun estaInicializando():
            Boolean {

        return inicializando
    }

    fun estaGerandoResposta():
            Boolean {

        return gerandoResposta
    }

    fun obterBackendAtual():
            String {

        return backendAtual
    }

    fun encerrar() {

        pronto =
            false

        inicializando =
            false

        gerandoResposta =
            false

        backendAtual =
            "NENHUM"

        try {

            engine?.close()

        } catch (
            erro: Throwable
        ) {

            Log.e(
                "GARUPA_IA_LOCAL",
                "⚠️ Erro ao encerrar motor",
                erro
            )
        }

        engine =
            null

        escopo.cancel()

        Log.d(
            "GARUPA_IA_LOCAL",
            "🧠 Motor encerrado"
        )
    }
}