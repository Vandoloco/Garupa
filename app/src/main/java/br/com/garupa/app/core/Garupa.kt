package br.com.garupa.app.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import br.com.garupa.app.core.cerebro.GarupaCerebro
import br.com.garupa.app.core.cerebro.MotorInteligenciaLocal
import br.com.garupa.app.core.memoria.Memoria
import br.com.garupa.app.core.olhos.Olhos
import br.com.garupa.app.core.ouvido.Ouvido
import br.com.garupa.app.core.voz.Voz

object Garupa {

    /*
     * Pequeno intervalo depois que o TTS termina.
     *
     * Evita capturar:
     * - final da própria voz do Garupa;
     * - reverberação do intercom;
     * - transientes produzidos pela troca da rota de áudio.
     */
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

    /*
     * =========================================================
     * ESTADO DA CONVERSA
     * =========================================================
     */

    @Volatile
    private var processandoFala =
        false

    @Volatile
    private var garupaFalando =
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

                            /*
                             * Se alguma resposta residual do
                             * SpeechRecognizer aparecer enquanto
                             * o Garupa estiver ocupado, ignoramos.
                             */
                            if (
                                processandoFala ||
                                garupaFalando ||
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
         * INTELIGÊNCIA LOCAL
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
                            "🧠 Motor de inteligência local disponível para o cérebro"
                        )

                    } else {

                        Log.e(
                            "GARUPA_IA_LOCAL",
                            "⚠️ Garupa continuará funcionando sem IA local"
                        )
                    }
                }
        }

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
     * RECEBIMENTO DA FALA DO PILOTO
     * =========================================================
     */

    private fun receberFala(
        frase: String
    ) {

        if (
            processandoFala ||
            garupaFalando ||
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
         * TRAVA IMEDIATA
         * =====================================================
         *
         * Assim que conseguimos uma frase válida:
         *
         * OUVIDO FECHA.
         *
         * Não esperamos:
         * - Gemma começar;
         * - Gemma terminar;
         * - TTS começar.
         *
         * Portanto, durante o tempo de inferência,
         * vento/motor/conversas não entram como uma
         * segunda pergunta.
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

        val contextoAtual =
            memoria.construirContexto()

        Log.d(
            "GARUPA_CEREBRO_CONTEXTO",
            "🧠 Contexto atual:\n$contextoAtual"
        )

        /*
         * =====================================================
         * CÉREBRO / GEMMA
         * =====================================================
         */

        cerebro.receberFala(
            fala =
                fraseLimpa,

            memoria =
                contextoAtual,

            motor =
                motorInteligenciaLocal,

            aoResponder =
                resposta@ { respostaGerada ->

                    /*
                     * Se a IA não responder, precisamos
                     * obrigatoriamente devolver o Ouvido.
                     */
                    if (
                        respostaGerada.isNullOrBlank()
                    ) {

                        Log.d(
                            "GARUPA_CONVERSA",
                            "⚠️ Garupa não respondeu desta vez"
                        )

                        processandoFala =
                            false

                        iniciarCooldownERetomarOuvido()

                        return@resposta
                    }

                    /*
                     * Gemma terminou de pensar.
                     *
                     * Continuamos com o Ouvido fechado porque
                     * agora o Garupa vai falar a resposta.
                     */
                    processandoFala =
                        false

                    garupaFalando =
                        true

                    /*
                     * =================================================
                     * MEMÓRIA DA RESPOSTA
                     * =================================================
                     */

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
                        "🔇 Gemma terminou; Ouvido permanece pausado durante TTS"
                    )

                    /*
                     * =================================================
                     * TTS
                     * =================================================
                     */

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

                                /*
                                 * Só depois do término REAL do TTS
                                 * começamos o período de proteção.
                                 */
                                iniciarCooldownERetomarOuvido()
                            }
                    )
                }
        )
    }

    /*
     * =========================================================
     * COOLDOWN + RETOMADA
     * =========================================================
     */

    private fun iniciarCooldownERetomarOuvido() {

        /*
         * Evita agendar duas retomadas simultaneamente.
         */
        if (
            aguardandoCooldown
        ) {

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

                /*
                 * Se outra operação tiver começado nesse
                 * intervalo, não devemos abrir o microfone.
                 */
                if (
                    processandoFala ||
                    garupaFalando
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

    fun obterMotorInteligenciaLocal():
            MotorInteligenciaLocal? {

        return motorInteligenciaLocal
    }
}