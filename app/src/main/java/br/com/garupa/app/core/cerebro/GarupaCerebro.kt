package br.com.garupa.app.core.cerebro

import android.util.Log
import br.com.garupa.app.core.Personalidade

data class ContextoConversaGarupa(

    val falaAtual: String,

    val memoria: String,

    val recebidoEm: Long =
        System.currentTimeMillis()
)

class GarupaCerebro {

    companion object {

        /*
         * Esse marcador é criado pelo Garupa.kt quando existe
         * visão válida e atual fornecida pelos Olhos.
         */
        private const val MARCADOR_CONTEXTO_VISUAL =
            "CONTEXTO VISUAL ATUAL:"
    }

    fun iniciar(): String {

        return Personalidade.saudacao()
    }

    /*
     * =========================================================
     * COMPATIBILIDADE COM O FLUXO ANTIGO
     * =========================================================
     */

    fun receberFala(
        fala: String,
        memoria: String,
        motor: MotorInteligenciaLocal?,
        aoResponder: (
            String?
        ) -> Unit = {}
    ) {

        receberFala(
            fala =
                fala,

            memoria =
                memoria,

            motorLocal =
                motor,

            motorGemini =
                null,

            aoResponder =
                aoResponder
        )
    }

    /*
     * =========================================================
     * ENTRADA COGNITIVA - DOIS MOTORES
     * =========================================================
     */

    fun receberFala(
        fala: String,
        memoria: String,
        motorLocal: MotorInteligenciaLocal?,
        motorGemini: MotorInteligenciaGemini?,
        aoResponder: (
            String?
        ) -> Unit = {}
    ) {

        val falaLimpa =
            fala
                .replace(
                    Regex("\\s+"),
                    " "
                )
                .trim()

        if (
            falaLimpa.isBlank()
        ) {

            aoResponder(
                null
            )

            return
        }

        val contexto =
            ContextoConversaGarupa(
                falaAtual =
                    falaLimpa,

                memoria =
                    memoria
            )

        processarContexto(
            contexto =
                contexto,

            motorLocal =
                motorLocal,

            motorGemini =
                motorGemini,

            aoResponder =
                aoResponder
        )
    }

    /*
     * =========================================================
     * PROCESSAMENTO
     * =========================================================
     */

    private fun processarContexto(
        contexto: ContextoConversaGarupa,
        motorLocal: MotorInteligenciaLocal?,
        motorGemini: MotorInteligenciaGemini?,
        aoResponder: (
            String?
        ) -> Unit
    ) {

        Log.d(
            "GARUPA_CEREBRO",
            "🧠 Situação recebida"
        )

        Log.d(
            "GARUPA_CEREBRO",
            "🗣️ Fala atual: ${contexto.falaAtual}"
        )

        if (
            contexto.memoria.isNotBlank()
        ) {

            Log.d(
                "GARUPA_CEREBRO",
                "💭 Contexto disponível:\n${contexto.memoria}"
            )
        }

        val possuiVisaoAtual =
            possuiContextoVisualAtual(
                contexto.memoria
            )

        Log.d(
            "GARUPA_CEREBRO_VISAO_ESTADO",
            if (
                possuiVisaoAtual
            ) {
                "👀 Cérebro possui visão atual válida"
            } else {
                "🙈 Cérebro NÃO possui visão atual válida"
            }
        )

        val prompt =
            construirPrompt(
                contexto =
                    contexto,

                possuiVisaoAtual =
                    possuiVisaoAtual
            )

        /*
         * =====================================================
         * GEMINI PRIMEIRO
         * =====================================================
         */

        if (
            motorGemini != null
        ) {

            Log.d(
                "GARUPA_CEREBRO_ROTEADOR",
                "☁️ Tentando Gemini primeiro"
            )

            tentarGemini(
                prompt =
                    prompt,

                motorGemini =
                    motorGemini,

                motorLocal =
                    motorLocal,

                aoResponder =
                    aoResponder
            )

            return
        }

        /*
         * =====================================================
         * SEM GEMINI → GEMMA LOCAL
         * =====================================================
         */

        Log.d(
            "GARUPA_CEREBRO_ROTEADOR",
            "📱 Gemini não disponível; usando inteligência local"
        )

        tentarMotorLocal(
            prompt =
                prompt,

            motorLocal =
                motorLocal,

            aoResponder =
                aoResponder
        )
    }

    /*
     * =========================================================
     * GEMINI
     * =========================================================
     */

    private fun tentarGemini(
        prompt: String,
        motorGemini: MotorInteligenciaGemini,
        motorLocal: MotorInteligenciaLocal?,
        aoResponder: (
            String?
        ) -> Unit
    ) {

        Log.d(
            "GARUPA_CEREBRO_PROMPT",
            "☁️ Enviando contexto para Gemini"
        )

        motorGemini.gerarResposta(
            prompt =
                prompt
        ) respostaGemini@ { resposta ->

            if (
                resposta.isNullOrBlank()
            ) {

                Log.w(
                    "GARUPA_CEREBRO_ROTEADOR",
                    "⚠️ Gemini não respondeu; tentando Gemma local"
                )

                tentarMotorLocal(
                    prompt =
                        prompt,

                    motorLocal =
                        motorLocal,

                    aoResponder =
                        aoResponder
                )

                return@respostaGemini
            }

            val respostaLimpa =
                limparResposta(
                    resposta
                )

            if (
                respostaLimpa.isBlank()
            ) {

                Log.w(
                    "GARUPA_CEREBRO_ROTEADOR",
                    "⚠️ Resposta Gemini ficou vazia após limpeza; tentando local"
                )

                tentarMotorLocal(
                    prompt =
                        prompt,

                    motorLocal =
                        motorLocal,

                    aoResponder =
                        aoResponder
                )

                return@respostaGemini
            }

            Log.d(
                "GARUPA_CEREBRO_ROTEADOR",
                "✅ Resposta escolhida | motor=GEMINI"
            )

            Log.d(
                "GARUPA_CEREBRO_RESPOSTA",
                "💬 Garupa: $respostaLimpa"
            )

            aoResponder(
                respostaLimpa
            )
        }
    }

    /*
     * =========================================================
     * GEMMA LOCAL
     * =========================================================
     */

    private fun tentarMotorLocal(
        prompt: String,
        motorLocal: MotorInteligenciaLocal?,
        aoResponder: (
            String?
        ) -> Unit
    ) {

        if (
            motorLocal == null
        ) {

            Log.e(
                "GARUPA_CEREBRO_ROTEADOR",
                "❌ Nenhum motor de inteligência disponível"
            )

            aoResponder(
                null
            )

            return
        }

        if (
            !motorLocal.estaPronto()
        ) {

            Log.d(
                "GARUPA_CEREBRO_ROTEADOR",
                "⏳ Gemma local ainda não está pronto"
            )

            aoResponder(
                null
            )

            return
        }

        Log.d(
            "GARUPA_CEREBRO_PROMPT",
            "📱 Enviando contexto para Gemma local"
        )

        motorLocal.gerarResposta(
            prompt =
                prompt
        ) respostaLocal@ { resposta ->

            if (
                resposta.isNullOrBlank()
            ) {

                Log.e(
                    "GARUPA_CEREBRO_ROTEADOR",
                    "❌ Gemma local também não respondeu"
                )

                aoResponder(
                    null
                )

                return@respostaLocal
            }

            val respostaLimpa =
                limparResposta(
                    resposta
                )

            if (
                respostaLimpa.isBlank()
            ) {

                Log.e(
                    "GARUPA_CEREBRO_ROTEADOR",
                    "❌ Resposta local ficou vazia após limpeza"
                )

                aoResponder(
                    null
                )

                return@respostaLocal
            }

            Log.d(
                "GARUPA_CEREBRO_ROTEADOR",
                "✅ Resposta escolhida | motor=GEMMA_LOCAL"
            )

            Log.d(
                "GARUPA_CEREBRO_RESPOSTA",
                "💬 Garupa: $respostaLimpa"
            )

            aoResponder(
                respostaLimpa
            )
        }
    }

    /*
     * =========================================================
     * ESTADO DA VISÃO
     * =========================================================
     */

    private fun possuiContextoVisualAtual(
        contexto: String
    ): Boolean {

        return contexto.contains(
            MARCADOR_CONTEXTO_VISUAL,
            ignoreCase = false
        )
    }

    /*
     * =========================================================
     * CONTEXTO PARA OS MOTORES
     * =========================================================
     */

    private fun construirPrompt(
        contexto: ContextoConversaGarupa,
        possuiVisaoAtual: Boolean
    ): String {

        return buildString {

            /*
             * =================================================
             * IDENTIDADE
             * =================================================
             */

            appendLine(
                "Você é o Garupa."
            )

            appendLine(
                "Seu papel é ser um companheiro de estrada e trabalho do piloto."
            )

            appendLine(
                "O piloto não é seu chefe, patrão, mestre ou superior; ele é seu parceiro de trabalho."
            )

            appendLine(
                "Converse de igual para igual e não chame o piloto de chefe, patrão, mestre ou senhor."
            )

            appendLine(
                "Seu estilo é ${Personalidade.estilo}."
            )

            appendLine()

            appendLine(
                "Converse de forma natural em português do Brasil."
            )

            appendLine(
                "Não aja como atendente, chatbot ou assistente formal."
            )

            appendLine(
                "Não diga que é uma inteligência artificial."
            )

            appendLine(
                "Não use respostas engessadas."
            )

            appendLine(
                "Responda como alguém que está acompanhando o contexto junto com o piloto."
            )

            appendLine(
                "Se não souber algo, não invente."
            )

            appendLine(
                "Não invente lembranças que não estejam no contexto fornecido."
            )

            appendLine(
                "Prefira respostas curtas e naturais, próprias para uma conversa falada."
            )

            appendLine(
                "Normalmente responda em uma ou duas frases."
            )

            appendLine()

            /*
             * =================================================
             * RESPOSTA OPERACIONAL RÁPIDA
             * =================================================
             *
             * Durante o trabalho, respostas longas atrasam o piloto.
             * O Garupa deve ser extremamente objetivo quando a
             * situação exigir decisão ou leitura rápida.
             */
            appendLine(
                "Quando a situação for operacional de entrega, responda de forma ainda mais curta e direta."
            )

            appendLine(
                "Considere situação operacional: oferta de corrida, decisão de aceitar ou recusar, coleta, entrega, rota, navegação, distância, valor, R$/km ou pergunta objetiva sobre a tela de trabalho."
            )

            appendLine(
                "Nessas situações, priorize uma frase curta. Evite explicações longas, introduções e repetições."
            )

            appendLine(
                "Se for decisão de oferta e o contexto já permitir decidir, diga primeiro a decisão, por exemplo: 'Vale a pena.' ou 'Deixa passar.'"
            )

            appendLine(
                "Só explique mais se o piloto pedir o motivo ou se faltar uma informação essencial para decidir."
            )

            appendLine(
                "Em conversa normal, continue natural e não transforme toda resposta em frase telegráfica."
            )

            appendLine()

            /*
             * =================================================
             * REGRA COGNITIVA: MEMÓRIA ≠ VISÃO
             * =================================================
             */

            appendLine(
                "IMPORTANTE SOBRE MEMÓRIA E VISÃO:"
            )

            appendLine(
                "A memória de conversa descreve acontecimentos anteriores."
            )

            appendLine(
                "Uma lembrança sobre uma oferta antiga não significa que essa oferta ainda está na tela."
            )

            appendLine(
                "Nunca use memória de conversa como prova do que está visível agora."
            )

            appendLine(
                "Somente informações presentes na seção \"$MARCADOR_CONTEXTO_VISUAL\" representam aquilo que o Garupa está vendo neste momento."
            )

            if (
                possuiVisaoAtual
            ) {

                appendLine(
                    "Neste momento existe contexto visual atual válido."
                )

                appendLine(
                    "Você pode usar os dados dessa seção para responder sobre o que está na tela."
                )

            } else {

                appendLine(
                    "Neste momento NÃO existe contexto visual atual válido."
                )

                appendLine(
                    "Portanto, não afirme que está vendo na tela algo presente apenas na memória."
                )

                appendLine(
                    "Se o piloto perguntar o que você está vendo agora, diga naturalmente que você não tem uma leitura visual atual da tela."
                )

                appendLine(
                    "Não reconstrua a tela atual a partir de ofertas ou conversas anteriores."
                )
            }

            appendLine()

            /*
             * =================================================
             * CONTEXTO REAL
             * =================================================
             */

            if (
                contexto.memoria.isNotBlank()
            ) {

                appendLine(
                    "CONTEXTO DISPONÍVEL:"
                )

                appendLine(
                    contexto.memoria
                )

                appendLine()
            }

            /*
             * =================================================
             * FALA ATUAL
             * =================================================
             */

            appendLine(
                "O piloto acabou de dizer:"
            )

            appendLine(
                "\"${contexto.falaAtual}\""
            )

            appendLine()

            appendLine(
                "Responda diretamente ao piloto como Garupa."
            )
        }
    }

    /*
     * =========================================================
     * LIMPEZA DA SAÍDA
     * =========================================================
     */

    private fun limparResposta(
        resposta: String
    ): String {

        return resposta
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
            .removePrefix(
                "Garupa:"
            )
            .trim()
    }
}