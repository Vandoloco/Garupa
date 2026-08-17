package br.com.garupa.app.core.oferta

import android.util.Log
import br.com.garupa.app.core.leitura.LinhaOcr

data class ResultadoDetectorOferta(
    val pareceOferta: Boolean,
    val pontuacao: Int,
    val temValor: Boolean,
    val temDistancia: Boolean,
    val temEndereco: Boolean,
    val temAcaoPedido: Boolean,
    val temPalavraEntrega: Boolean
)

class DetectorOferta {

    fun analisar(
        linhas: List<LinhaOcr>
    ): ResultadoDetectorOferta {

        val textoCompleto =
            linhas
                .joinToString("\n") {
                    it.texto
                }

        /*
         * =====================================================
         * 1. VALOR EM R$
         *
         * Nesta primeira versão, valor monetário é obrigatório.
         *
         * Isso ajuda a excluir Maps/Waze,
         * que podem ter km/endereço,
         * mas normalmente não mostram valor de oferta.
         * =====================================================
         */
        val regexValor =
            Regex(
                """R\$\s*\d+[.,]\d{2}""",
                RegexOption.IGNORE_CASE
            )

        val temValor =
            regexValor.containsMatchIn(
                textoCompleto
            )

        /*
         * =====================================================
         * 2. DISTÂNCIA
         * =====================================================
         */
        val regexDistancia =
            Regex(
                """\d+[.,]?\d*\s*km""",
                RegexOption.IGNORE_CASE
            )

        val temDistancia =
            regexDistancia.containsMatchIn(
                textoCompleto
            )

        /*
         * =====================================================
         * 3. ENDEREÇO
         *
         * Procuramos sinais comuns de via.
         * =====================================================
         */
        val regexEndereco =
            Regex(
                """\b(rua|r\.|avenida|av\.?|alameda|estrada|rodovia|travessa|praça|praca)\b""",
                RegexOption.IGNORE_CASE
            )

        val temEndereco =
            regexEndereco.containsMatchIn(
                textoCompleto
            )

        /*
         * =====================================================
         * 4. AÇÃO DE PEDIDO
         *
         * São sinais muito fortes de oferta.
         * =====================================================
         */
        val regexAcao =
            Regex(
                """\b(aceitar|recusar|pegar|rejeitar)\b""",
                RegexOption.IGNORE_CASE
            )

        val temAcaoPedido =
            regexAcao.containsMatchIn(
                textoCompleto
            )

        /*
         * =====================================================
         * 5. VOCABULÁRIO DE DELIVERY
         * =====================================================
         */
        val regexEntrega =
            Regex(
                """\b(coleta|entrega|pedido|pedidos|delivery)\b""",
                RegexOption.IGNORE_CASE
            )

        val temPalavraEntrega =
            regexEntrega.containsMatchIn(
                textoCompleto
            )

        /*
         * =====================================================
         * PONTUAÇÃO
         *
         * R$ é obrigatório.
         *
         * Os outros sinais aumentam a confiança.
         * =====================================================
         */

        var pontuacao =
            0

        if (temValor) {
            pontuacao += 4
        }

        if (temDistancia) {
            pontuacao += 2
        }

        if (temEndereco) {
            pontuacao += 2
        }

        if (temAcaoPedido) {
            pontuacao += 3
        }

        if (temPalavraEntrega) {
            pontuacao += 2
        }

        /*
         * Para ser considerada oferta:
         *
         * - precisa obrigatoriamente ter R$
         * - e alcançar pelo menos 6 pontos
         *
         * Exemplos:
         *
         * R$ + km = 6
         *
         * R$ + endereço = 6
         *
         * R$ + Aceitar = 7
         *
         * R$ sozinho = 4 → NÃO passa
         */
        val pareceOferta =
            temValor &&
                    pontuacao >= 6

        val resultado =
            ResultadoDetectorOferta(
                pareceOferta =
                    pareceOferta,

                pontuacao =
                    pontuacao,

                temValor =
                    temValor,

                temDistancia =
                    temDistancia,

                temEndereco =
                    temEndereco,

                temAcaoPedido =
                    temAcaoPedido,

                temPalavraEntrega =
                    temPalavraEntrega
            )

        Log.d(
            "GARUPA_DETECTOR_OFERTA",
            "🧠 Oferta=${resultado.pareceOferta} | " +
                    "pontos=${resultado.pontuacao} | " +
                    "R$=${resultado.temValor} | " +
                    "km=${resultado.temDistancia} | " +
                    "endereco=${resultado.temEndereco} | " +
                    "acao=${resultado.temAcaoPedido} | " +
                    "delivery=${resultado.temPalavraEntrega}"
        )

        return resultado
    }
}