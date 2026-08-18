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

    fun iniciar(): String {

        return Personalidade.saudacao()
    }

    /*
     * =========================================================
     * ENTRADA COGNITIVA
     * =========================================================
     *
     * O cérebro recebe:
     *
     * - fala atual;
     * - memória disponível;
     * - motor de raciocínio local.
     *
     * O Gemma NÃO é o Garupa.
     *
     * Ele é somente uma ferramenta de raciocínio
     * utilizada pelo cérebro.
     */
    fun receberFala(
        fala: String,
        memoria: String,
        motor: MotorInteligenciaLocal?,
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

            motor =
                motor,

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
        motor: MotorInteligenciaLocal?,
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
                "💭 Memória disponível:\n${contexto.memoria}"
            )
        }

        /*
         * Se o motor ainda estiver carregando,
         * o restante do Garupa continua funcionando.
         */
        if (
            motor == null ||
            !motor.estaPronto()
        ) {

            Log.d(
                "GARUPA_CEREBRO",
                "⏳ Inteligência local ainda não disponível"
            )

            aoResponder(
                null
            )

            return
        }

        val prompt =
            construirPrompt(
                contexto
            )

        Log.d(
            "GARUPA_CEREBRO_PROMPT",
            "🧠 Enviando contexto para inteligência local"
        )

        motor.gerarResposta(
            prompt =
                prompt
        ) { resposta ->

            if (
                resposta.isNullOrBlank()
            ) {

                Log.d(
                    "GARUPA_CEREBRO",
                    "⚠️ Nenhuma resposta gerada"
                )

                aoResponder(
                    null
                )

                return@gerarResposta
            }

            val respostaLimpa =
                limparResposta(
                    resposta
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
     * CONTEXTO PARA A INTELIGÊNCIA
     * =========================================================
     *
     * Não colocamos respostas prontas.
     *
     * O prompt descreve QUEM é o Garupa e fornece
     * o contexto real disponível naquele momento.
     */
    private fun construirPrompt(
        contexto: ContextoConversaGarupa
    ): String {

        return buildString {

            appendLine(
                "Você é o Garupa."
            )

            appendLine(
                "Seu papel é ser um companheiro de estrada e trabalho do piloto."
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

            if (
                contexto.memoria.isNotBlank()
            ) {

                appendLine(
                    "Memória e contexto disponíveis:"
                )

                appendLine(
                    contexto.memoria
                )

                appendLine()
            }

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