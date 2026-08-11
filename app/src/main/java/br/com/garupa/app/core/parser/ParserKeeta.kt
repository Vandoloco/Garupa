package br.com.garupa.app.core.parser

import android.util.Log
import br.com.garupa.app.core.leitura.LinhaOcr

data class ResultadoKeeta(
    val valor: Double?,
    val distancia: Double?,
    val coletaVisivel: Boolean,
    val entregaVisivel: Boolean,
    val nomeColeta: String?,
    val enderecoColeta: String?,
    val enderecoEntrega: String?
)

class ParserKeeta {

    fun analisar(
        linhas: List<LinhaOcr>
    ): ResultadoKeeta {

        val textoCompleto =
            linhas.joinToString("\n") {
                it.texto
            }

        val regexValor =
            Regex(
                """R\$\s*(\d+[.,]\d{2})"""
            )

        val regexDistancia =
            Regex(
                """(\d+[.,]\d+)\s*km""",
                RegexOption.IGNORE_CASE
            )

        val valor =
            regexValor
                .find(textoCompleto)
                ?.groupValues
                ?.get(1)
                ?.replace(",", ".")
                ?.toDoubleOrNull()

        val distancia =
            regexDistancia
                .find(textoCompleto)
                ?.groupValues
                ?.get(1)
                ?.replace(",", ".")
                ?.toDoubleOrNull()

        val linhaColeta =
            linhas.firstOrNull { linha ->

                linha.texto.contains(
                    "Coleta",
                    ignoreCase = true
                )
            }

        val linhaEntrega =
            linhas.firstOrNull { linha ->

                Regex(
                    """Entrega\s*(até|ate)""",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(
                    linha.texto
                )
            }

        val coletaVisivel =
            linhaColeta != null

        val entregaVisivel =
            linhaEntrega != null

        var nomeColeta: String? = null
        var enderecoColeta: String? = null
        var enderecoEntrega: String? = null

        /*
         * PONTO B
         */
        if (linhaColeta != null) {

            val limiteColeta =
                linhaEntrega?.y
                    ?: linhas
                        .firstOrNull { linha ->

                            linha.y > linhaColeta.y &&
                                    ehBotaoFinal(
                                        linha.texto
                                    )
                        }
                        ?.y
                    ?: Int.MAX_VALUE

            val linhasColeta =
                linhas
                    .filter { linha ->

                        linha.y > linhaColeta.y &&
                                linha.y < limiteColeta
                    }
                    .filter { linha ->

                        linha.texto.isNotBlank()
                    }
                    .sortedBy { linha ->

                        linha.y
                    }

            if (linhasColeta.isNotEmpty()) {

                nomeColeta =
                    linhasColeta
                        .first()
                        .texto
                        .trim()

                val partesEndereco =
                    linhasColeta
                        .drop(1)
                        .map {
                            it.texto.trim()
                        }

                if (partesEndereco.isNotEmpty()) {

                    enderecoColeta =
                        juntarLinhasComSobreposicao(
                            partesEndereco
                        )
                }
            }
        }

        /*
         * PONTO C
         */
        if (linhaEntrega != null) {

            val limiteEntrega =
                linhas
                    .firstOrNull { linha ->

                        linha.y > linhaEntrega.y &&
                                ehBotaoFinal(
                                    linha.texto
                                )
                    }
                    ?.y
                    ?: Int.MAX_VALUE

            val partesEntrega =
                linhas
                    .filter { linha ->

                        linha.y > linhaEntrega.y &&
                                linha.y < limiteEntrega
                    }
                    .filter { linha ->

                        linha.texto.isNotBlank()
                    }
                    .filterNot { linha ->

                        linha.texto.contains(
                            "km total",
                            ignoreCase = true
                        )
                    }
                    .filterNot { linha ->

                        linha.texto.contains(
                            "Coleta",
                            ignoreCase = true
                        )
                    }
                    .sortedBy { linha ->

                        linha.y
                    }
                    .map {
                        it.texto.trim()
                    }

            if (partesEntrega.isNotEmpty()) {

                enderecoEntrega =
                    juntarLinhasComSobreposicao(
                        partesEntrega
                    )
            }
        }

        val resultado =
            ResultadoKeeta(
                valor = valor,
                distancia = distancia,
                coletaVisivel = coletaVisivel,
                entregaVisivel = entregaVisivel,
                nomeColeta = nomeColeta,
                enderecoColeta = enderecoColeta,
                enderecoEntrega = enderecoEntrega
            )

        Log.d(
            "GARUPA_KEETA",
            "📦 Keeta | " +
                    "Valor: ${resultado.valor} | " +
                    "Distância: ${resultado.distancia} | " +
                    "Coleta: ${resultado.coletaVisivel} | " +
                    "Entrega: ${resultado.entregaVisivel}"
        )

        Log.d(
            "GARUPA_KEETA_B",
            "📍 Ponto B | " +
                    "Local: ${resultado.nomeColeta} | " +
                    "Endereço: ${resultado.enderecoColeta}"
        )

        Log.d(
            "GARUPA_KEETA_C",
            "🏠 Ponto C | " +
                    "Endereço: ${resultado.enderecoEntrega}"
        )

        return resultado
    }

    private fun juntarLinhasComSobreposicao(
        linhas: List<String>
    ): String {

        if (linhas.isEmpty()) {
            return ""
        }

        var resultado =
            linhas.first().trim()

        for (i in 1 until linhas.size) {

            resultado =
                juntarDuasLinhas(
                    resultado,
                    linhas[i].trim()
                )
        }

        return resultado
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    private fun juntarDuasLinhas(
        atual: String,
        proxima: String
    ): String {

        val palavrasAtual =
            atual.split(
                Regex("\\s+")
            )

        val palavrasProxima =
            proxima.split(
                Regex("\\s+")
            )

        /*
         * CASO 1
         *
         * Final da primeira linha é igual
         * ao começo da próxima.
         */
        val maximo =
            minOf(
                palavrasAtual.size,
                palavrasProxima.size
            )

        for (quantidade in maximo downTo 1) {

            val fimAtual =
                palavrasAtual
                    .takeLast(quantidade)
                    .map {
                        normalizarPalavra(it)
                    }

            val inicioProxima =
                palavrasProxima
                    .take(quantidade)
                    .map {
                        normalizarPalavra(it)
                    }

            if (
                fimAtual == inicioProxima
            ) {

                val restante =
                    palavrasProxima
                        .drop(quantidade)
                        .joinToString(" ")

                return if (
                    restante.isBlank()
                ) {

                    atual

                } else {

                    "$atual $restante"
                }
            }
        }

        /*
         * CASO 2
         *
         * O começo da próxima linha
         * já existe no MEIO/FIM da atual.
         *
         * Exemplo real:
         *
         * Rua Bartolomeo Veneto, 126, Rua
         * Bartolomeo Veneto, 126, São Paulo,
         *
         * Detectamos:
         *
         * Bartolomeo Veneto 126
         *
         * repetido nas duas linhas.
         */
        for (
        quantidade in
        palavrasProxima.size downTo 2
        ) {

            val prefixoProxima =
                palavrasProxima
                    .take(quantidade)
                    .map {
                        normalizarPalavra(it)
                    }

            if (
                prefixoProxima.any {
                    it.isBlank()
                }
            ) {
                continue
            }

            val limite =
                palavrasAtual.size -
                        quantidade

            for (
            inicio in
            limite downTo 0
            ) {

                val trechoAtual =
                    palavrasAtual
                        .drop(inicio)
                        .take(quantidade)
                        .map {
                            normalizarPalavra(it)
                        }

                if (
                    trechoAtual ==
                    prefixoProxima
                ) {

                    val antesDaRepeticao =
                        palavrasAtual
                            .take(inicio)
                            .joinToString(" ")

                    return if (
                        antesDaRepeticao.isBlank()
                    ) {

                        proxima

                    } else {

                        "$antesDaRepeticao $proxima"
                    }
                }
            }
        }

        return "$atual $proxima"
    }

    private fun normalizarPalavra(
        palavra: String
    ): String {

        return palavra
            .lowercase()
            .replace(
                Regex("[^a-z0-9à-ú]"),
                ""
            )
    }

    private fun ehBotaoFinal(
        texto: String
    ): Boolean {

        return texto.contains(
            "Aceitar",
            ignoreCase = true
        ) ||
                texto.contains(
                    "Pegar",
                    ignoreCase = true
                ) ||
                texto.contains(
                    "Recusar",
                    ignoreCase = true
                )
    }
}