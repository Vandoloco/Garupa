package br.com.garupa.app.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import br.com.garupa.app.core.cerebro.GarupaCerebro
import br.com.garupa.app.core.cerebro.MotorInteligenciaGemini
import br.com.garupa.app.core.cerebro.MotorInteligenciaLocal
import br.com.garupa.app.core.memoria.Memoria
import br.com.garupa.app.core.olhos.Olhos
import br.com.garupa.app.core.ouvido.Ouvido
import br.com.garupa.app.core.voz.Voz

object Garupa {

    private const val COOLDOWN_APOS_FALA_MS =
        1000L

    private val handlerPrincipal =
        Handler(
            Looper.getMainLooper()
        )

    private val memoria =
        Memoria()

    private val olhos =
        Olhos()

    private val cerebro =
        GarupaCerebro()

    private var ouvido:
            Ouvido? =
        null

    private var voz:
            Voz? =
        null

    private var motorInteligenciaLocal:
            MotorInteligenciaLocal? =
        null

    private var motorInteligenciaGemini:
            MotorInteligenciaGemini? =
        null

    @Volatile
    private var processandoFala =
        false

    @Volatile
    private var garupaFalando =
        false

    @Volatile
    private var decisaoOperacionalFalando =
        false

    @Volatile
    private var aguardandoCooldown =
        false

    /*
     * =========================================================
     * INICIALIZAÇÃO
     * =========================================================
     */

    fun iniciar(
        contexto: Context
    ) {

        val contextoAplicacao =
            contexto.applicationContext

        Log.d(
            "GARUPA",
            "🚀 Iniciando Garupa..."
        )

        memoria.carregar()

        olhos.iniciar()

        /*
         * =====================================================
         * VOZ
         * =====================================================
         */

        if (
            voz == null
        ) {

            voz =
                Voz(
                    contextoAplicacao
                ).also {

                    it.iniciar()
                }
        }

        /*
         * =====================================================
         * OUVIDO
         * =====================================================
         */

        if (
            ouvido == null
        ) {

            ouvido =
                Ouvido(
                    contextoAplicacao
                ).also { novoOuvido ->

                    novoOuvido
                        .definirAoReconhecerFala { frase ->

                            if (
                                processandoFala ||
                                garupaFalando ||
                                decisaoOperacionalFalando ||
                                aguardandoCooldown
                            ) {

                                Log.d(
                                    "GARUPA_OUVIDO",
                                    "🔇 Entrada ignorada: Garupa ocupado"
                                )

                                return@definirAoReconhecerFala
                            }

                            receberFala(
                                frase
                            )
                        }

                    novoOuvido.iniciar()
                }
        }

        /*
         * =====================================================
         * GEMMA LOCAL
         * =====================================================
         */

        if (
            motorInteligenciaLocal ==
            null
        ) {

            motorInteligenciaLocal =
                MotorInteligenciaLocal(
                    contextoAplicacao
                )

            Log.d(
                "GARUPA_IA_LOCAL",
                "🚀 Solicitando inicialização do motor local"
            )

            motorInteligenciaLocal
                ?.inicializar { sucesso ->

                    if (
                        sucesso
                    ) {

                        Log.d(
                            "GARUPA_IA_LOCAL",
                            "🧠 Motor local disponível"
                        )

                    } else {

                        Log.e(
                            "GARUPA_IA_LOCAL",
                            "⚠️ Motor local indisponível"
                        )
                    }
                }
        }

        /*
         * =====================================================
         * GEMINI ONLINE
         * =====================================================
         */

        if (
            motorInteligenciaGemini ==
            null
        ) {

            motorInteligenciaGemini =
                MotorInteligenciaGemini()

            Log.d(
                "GARUPA_GEMINI",
                "☁️ Motor Gemini disponível para o cérebro"
            )
        }

        /*
         * =====================================================
         * CÉREBRO
         * =====================================================
         */

        val respostaInicial =
            cerebro.iniciar()

        Log.d(
            "GARUPA",
            respostaInicial
        )

        Log.d(
            "GARUPA",
            "✅ Garupa pronto para rodar!"
        )
    }

    /*
     * =========================================================
     * FALA DO PILOTO
     * =========================================================
     */

    private fun receberFala(
        frase: String
    ) {

        if (
            processandoFala ||
            garupaFalando ||
            decisaoOperacionalFalando ||
            aguardandoCooldown
        ) {

            Log.d(
                "GARUPA_OUVIDO",
                "🔇 Entrada descartada: Garupa ocupado"
            )

            return
        }

        val fraseLimpa =
            frase
                .replace(
                    Regex("\\s+"),
                    " "
                )
                .trim()

        if (
            fraseLimpa.isBlank()
        ) {

            return
        }

        /*
         * =====================================================
         * PAUSA IMEDIATA DO OUVIDO
         * =====================================================
         */

        processandoFala =
            true

        Log.d(
            "GARUPA_OUVIDO",
            "🔒 Fala aceita; pausando Ouvido durante processamento"
        )

        ouvido
            ?.pararEscuta()

        Log.d(
            "GARUPA_CEREBRO_FALA",
            "🧠 Fala recebida pelo Garupa: $fraseLimpa"
        )

        /*
         * =====================================================
         * MEMÓRIA
         * =====================================================
         */

        memoria.lembrarConversa(
            texto =
                "Piloto disse: $fraseLimpa",

            importancia =
                0.6
        )

        /*
         * =====================================================
         * CONTEXTO DA MEMÓRIA
         * =====================================================
         */

        val contextoMemoria =
            memoria.construirContexto()

        /*
         * =====================================================
         * CONTEXTO DOS OLHOS
         * =====================================================
         */

        val contextoVisual =
            olhos.construirContexto()

        if (
            contextoVisual.isNotBlank()
        ) {

            Log.d(
                "GARUPA_CEREBRO_VISAO",
                "👀 Contexto visual disponível:\n$contextoVisual"
            )
        }

        /*
         * =====================================================
         * CONTEXTO COGNITIVO FINAL
         * =====================================================
         */

        val contextoAtual =
            buildString {

                if (
                    contextoMemoria.isNotBlank()
                ) {

                    appendLine(
                        "MEMÓRIA DE CONVERSA:"
                    )

                    appendLine(
                        contextoMemoria
                    )
                }

                if (
                    contextoVisual.isNotBlank()
                ) {

                    if (
                        isNotBlank()
                    ) {

                        appendLine()
                    }

                    appendLine(
                        "CONTEXTO VISUAL ATUAL:"
                    )

                    appendLine(
                        contextoVisual
                    )
                }
            }
                .trim()

        Log.d(
            "GARUPA_CEREBRO_CONTEXTO",
            "🧠 Contexto cognitivo atual:\n$contextoAtual"
        )

        /*
         * =====================================================
         * CÉREBRO HÍBRIDO
         * =====================================================
         */

        cerebro.receberFala(
            fala =
                fraseLimpa,

            memoria =
                contextoAtual,

            motorLocal =
                motorInteligenciaLocal,

            motorGemini =
                motorInteligenciaGemini,

            aoResponder =
                resposta@ { respostaGerada ->

                    if (
                        respostaGerada.isNullOrBlank()
                    ) {

                        Log.d(
                            "GARUPA_CONVERSA",
                            "⚠️ Garupa não respondeu desta vez"
                        )

                        processandoFala =
                            false

                        if (
                            !decisaoOperacionalFalando
                        ) {

                            iniciarCooldownERetomarOuvido()
                        }

                        return@resposta
                    }

                    processandoFala =
                        false

                    garupaFalando =
                        true

                    memoria.lembrarConversa(
                        texto =
                            "Garupa respondeu: $respostaGerada",

                        importancia =
                            0.5
                    )

                    Log.d(
                        "GARUPA_CONVERSA",
                        "💬 $respostaGerada"
                    )

                    Log.d(
                        "GARUPA_OUVIDO",
                        "🔇 Resposta pronta; Ouvido permanece pausado durante TTS"
                    )

                    voz?.falar(
                        mensagem =
                            respostaGerada,

                        aoTerminar =
                            {

                                Log.d(
                                    "GARUPA_VOZ",
                                    "✅ Resposta falada por completo"
                                )

                                garupaFalando =
                                    false

                                if (
                                    !decisaoOperacionalFalando
                                ) {

                                    iniciarCooldownERetomarOuvido()
                                }
                            }
                    )
                }
        )
    }

    /*
     * =========================================================
     * DECISÕES OPERACIONAIS
     * =========================================================
     */

    fun anunciarAceitar() {

        anunciarDecisaoOperacional(
            aceitar =
                true
        )
    }

    fun anunciarDeixarPassar() {

        anunciarDecisaoOperacional(
            aceitar =
                false
        )
    }

    private fun anunciarDecisaoOperacional(
        aceitar: Boolean
    ) {

        handlerPrincipal.post {

            if (
                decisaoOperacionalFalando
            ) {

                Log.d(
                    "GARUPA_DECISAO_VOZ",
                    "⏳ Decisão ignorada: outra decisão já está sendo anunciada"
                )

                return@post
            }

            val vozAtual =
                voz

            if (
                vozAtual == null
            ) {

                Log.d(
                    "GARUPA_DECISAO_VOZ",
                    "⚠️ Voz ainda não está disponível"
                )

                return@post
            }

            decisaoOperacionalFalando =
                true

            aguardandoCooldown =
                false

            Log.d(
                "GARUPA_DECISAO_VOZ",
                if (
                    aceitar
                ) {
                    "🔒 Pausando Ouvido para anunciar: ACEITAR"
                } else {
                    "🔒 Pausando Ouvido para anunciar: DEIXAR PASSAR"
                }
            )

            /*
             * pararEscuta() libera a rota de comunicação.
             */
            ouvido
                ?.pararEscuta()

            /*
             * Dá tempo para MODE_NORMAL/rota normal
             * estabilizarem antes do TTS.
             */
            handlerPrincipal.postDelayed(
                {

                    /*
                     * IMPORTANTE:
                     *
                     * Tipagem explícita como () -> Unit.
                     *
                     * Sem isto o Kotlin pode inferir () -> Any
                     * porque Log.d retorna Int.
                     */
                    val aoTerminarDecisao:
                                () -> Unit =
                        {

                            Log.d(
                                "GARUPA_DECISAO_VOZ",
                                "✅ Decisão operacional falada por completo"
                            )

                            decisaoOperacionalFalando =
                                false

                            if (
                                processandoFala ||
                                garupaFalando
                            ) {

                                Log.d(
                                    "GARUPA_DECISAO_VOZ",
                                    "⏳ Ouvido continua pausado: cérebro/voz ainda ocupados"
                                )

                            } else {

                                iniciarCooldownERetomarOuvido()
                            }

                            Unit
                        }

                    if (
                        aceitar
                    ) {

                        vozAtual.anunciarAceitar(
                            aoTerminar =
                                aoTerminarDecisao
                        )

                    } else {

                        vozAtual.anunciarDeixarPassar(
                            aoTerminar =
                                aoTerminarDecisao
                        )
                    }

                },
                150L
            )
        }
    }

    /*
     * =========================================================
     * COOLDOWN
     * =========================================================
     */

    private fun iniciarCooldownERetomarOuvido() {

        if (
            aguardandoCooldown
        ) {

            return
        }

        if (
            processandoFala ||
            garupaFalando ||
            decisaoOperacionalFalando
        ) {

            Log.d(
                "GARUPA_OUVIDO",
                "⏳ Cooldown adiado: Garupa continua ocupado"
            )

            return
        }

        aguardandoCooldown =
            true

        Log.d(
            "GARUPA_OUVIDO",
            "⏳ Cooldown de áudio por ${COOLDOWN_APOS_FALA_MS}ms"
        )

        handlerPrincipal.postDelayed(
            {

                if (
                    processandoFala ||
                    garupaFalando ||
                    decisaoOperacionalFalando
                ) {

                    aguardandoCooldown =
                        false

                    Log.d(
                        "GARUPA_OUVIDO",
                        "⚠️ Retomada cancelada: Garupa continua ocupado"
                    )

                    return@postDelayed
                }

                aguardandoCooldown =
                    false

                Log.d(
                    "GARUPA_OUVIDO",
                    "👂 Cooldown concluído; retomando Ouvido"
                )

                ouvido
                    ?.comecarEscutaContinua()

            },
            COOLDOWN_APOS_FALA_MS
        )
    }

    /*
     * =========================================================
     * CONTROLE DO OUVIDO
     * =========================================================
     */

    fun iniciarEscuta() {

        if (
            processandoFala ||
            garupaFalando ||
            decisaoOperacionalFalando ||
            aguardandoCooldown
        ) {

            Log.d(
                "GARUPA_OUVIDO",
                "⏳ Escuta solicitada, mas Garupa está ocupado"
            )

            return
        }

        Log.d(
            "GARUPA_OUVIDO",
            "🎤 Ativando escuta contínua"
        )

        ouvido
            ?.comecarEscutaContinua()
    }

    fun pararEscuta() {

        aguardandoCooldown =
            false

        ouvido
            ?.pararEscuta()
    }

    /*
     * =========================================================
     * ACESSO CONTROLADO
     * =========================================================
     */

    fun obterVoz():
            Voz? {

        return voz
    }

    fun obterMemoria():
            Memoria {

        return memoria
    }

    fun obterOlhos():
            Olhos {

        return olhos
    }

    fun obterMotorInteligenciaLocal():
            MotorInteligenciaLocal? {

        return motorInteligenciaLocal
    }

    fun obterMotorInteligenciaGemini():
            MotorInteligenciaGemini? {

        return motorInteligenciaGemini
    }
}