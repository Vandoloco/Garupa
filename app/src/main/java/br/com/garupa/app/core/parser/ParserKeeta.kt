package br.com.garupa.app.core.parser

import android.util.Log
import br.com.garupa.app.core.leitura.LinhaOcr

enum class TipoParadaKeeta {
    COLETA,
    ENTREGA
}

data class ParadaKeeta(
    val tipo: TipoParadaKeeta,
    val nome: String? = null,
    val endereco: String
)

data class ResultadoKeeta(

    /*
     * CAMPOS ANTIGOS
     *
     * Mantidos para não quebrar o
     * LeitorTela atual.
     */
    val valor: Double?,
    val distancia: Double?,
    val coletaVisivel: Boolean,
    val entregaVisivel: Boolean,

    val nomeColeta: String?,
    val enderecoColeta: String?,
    val enderecoEntrega: String?,

    /*
     * NOVOS CAMPOS
     *
     * Permitem ofertas com múltiplas
     * coletas e/ou entregas.
     */
    val quantidadePedidos: Int? = null,

    val paradas: List<ParadaKeeta> =
        emptyList()
)

class ParserKeeta {

    fun analisar(
        linhas: List<LinhaOcr>
    ): ResultadoKeeta {

        val linhasOrdenadas =
            linhas.sortedBy {
                it.y
            }

        val textoCompleto =
            linhasOrdenadas.joinToString(
                "\n"
            ) {
                it.texto
            }

        /*
         * =====================================================
         * VALOR
         * =====================================================
         */

        val regexValor =
            Regex(
                """R\$\s*(\d+[.,]\d{2})"""
            )

        val valor =
            regexValor
                .find(textoCompleto)
                ?.groupValues
                ?.get(1)
                ?.replace(
                    ",",
                    "."
                )
                ?.toDoubleOrNull()

        /*
         * =====================================================
         * DISTÂNCIA
         * =====================================================
         */

        val regexDistancia =
            Regex(
                """(\d+[.,]\d+)\s*km""",
                RegexOption.IGNORE_CASE
            )

        val distancia =
            regexDistancia
                .find(textoCompleto)
                ?.groupValues
                ?.get(1)
                ?.replace(
                    ",",
                    "."
                )
                ?.toDoubleOrNull()

        /*
         * =====================================================
         * IDENTIFICAÇÃO DE PEDIDO AGRUPADO
         *
         * Exemplo do popup:
         *
         * "2 pedidos para coletar"
         * =====================================================
         */

        val regexPedidosAgrupados =
            Regex(
                """(\d+)\s+pedidos?\s+para\s+coletar""",
                RegexOption.IGNORE_CASE
            )

        val linhaPedidosAgrupados =
            linhasOrdenadas
                .firstOrNull { linha ->

                    regexPedidosAgrupados
                        .containsMatchIn(
                            linha.texto
                        )
                }

        val quantidadePedidos =
            linhaPedidosAgrupados
                ?.let { linha ->

                    regexPedidosAgrupados
                        .find(
                            linha.texto
                        )
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                }

        /*
         * Se encontramos:
         *
         * "2 pedidos para coletar"
         *
         * tratamos como layout agrupado.
         */
        if (
            linhaPedidosAgrupados != null
        ) {

            return analisarOfertaAgrupada(
                linhas =
                    linhasOrdenadas,

                valor =
                    valor,

                distancia =
                    distancia,

                linhaPedidos =
                    linhaPedidosAgrupados,

                quantidadePedidos =
                    quantidadePedidos
            )
        }

        /*
         * Caso contrário usamos o parser
         * tradicional de oferta simples.
         */
        return analisarOfertaSimples(
            linhas =
                linhasOrdenadas,

            valor =
                valor,

            distancia =
                distancia
        )
    }

    /*
     * =========================================================
     * OFERTA SIMPLES
     *
     * Estrutura conhecida:
     *
     * Coleta
     * Restaurante
     * Endereço B
     *
     * Entrega até ...
     * Endereço C
     * =========================================================
     */

    private fun analisarOfertaSimples(
        linhas: List<LinhaOcr>,
        valor: Double?,
        distancia: Double?
    ): ResultadoKeeta {

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

        var nomeColeta: String? =
            null

        var enderecoColeta: String? =
            null

        var enderecoEntrega: String? =
            null

        /*
         * =====================================================
         * PONTO B
         * =====================================================
         */

        if (
            linhaColeta != null
        ) {

            val limiteColeta =
                linhaEntrega?.y
                    ?: linhas
                        .firstOrNull { linha ->

                            linha.y >
                                    linhaColeta.y &&
                                    ehBotaoFinal(
                                        linha.texto
                                    )
                        }
                        ?.y
                    ?: Int.MAX_VALUE

            val linhasColeta =
                linhas
                    .filter { linha ->

                        linha.y >
                                linhaColeta.y &&
                                linha.y <
                                limiteColeta
                    }
                    .filter { linha ->

                        linha.texto
                            .isNotBlank()
                    }
                    .filterNot { linha ->

                        ehRuidoInterface(
                            linha.texto
                        )
                    }
                    .sortedBy { linha ->

                        linha.y
                    }

            if (
                linhasColeta.isNotEmpty()
            ) {

                /*
                 * Primeira linha abaixo de
                 * "Coleta" normalmente é o
                 * nome do estabelecimento.
                 */
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

                if (
                    partesEndereco.isNotEmpty()
                ) {

                    enderecoColeta =
                        juntarLinhasComSobreposicao(
                            partesEndereco
                        )
                }
            }
        }

        /*
         * =====================================================
         * PONTO C
         * =====================================================
         */

        if (
            linhaEntrega != null
        ) {

            val limiteEntrega =
                linhas
                    .firstOrNull { linha ->

                        linha.y >
                                linhaEntrega.y &&
                                ehBotaoFinal(
                                    linha.texto
                                )
                    }
                    ?.y
                    ?: Int.MAX_VALUE

            val partesEntrega =
                linhas
                    .filter { linha ->

                        linha.y >
                                linhaEntrega.y &&
                                linha.y <
                                limiteEntrega
                    }
                    .filter { linha ->

                        linha.texto
                            .isNotBlank()
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
                    .filterNot { linha ->

                        ehRuidoInterface(
                            linha.texto
                        )
                    }
                    .sortedBy { linha ->

                        linha.y
                    }
                    .map {
                        it.texto.trim()
                    }

            if (
                partesEntrega.isNotEmpty()
            ) {

                enderecoEntrega =
                    juntarLinhasComSobreposicao(
                        partesEntrega
                    )
            }
        }

        /*
         * Lista nova de paradas.
         *
         * Pedido simples:
         *
         * B = uma coleta
         * C = uma entrega
         */
        val paradas =
            mutableListOf<ParadaKeeta>()

        if (
            !enderecoColeta.isNullOrBlank()
        ) {

            paradas.add(
                ParadaKeeta(
                    tipo =
                        TipoParadaKeeta.COLETA,

                    nome =
                        nomeColeta,

                    endereco =
                        enderecoColeta
                )
            )
        }

        if (
            !enderecoEntrega.isNullOrBlank()
        ) {

            paradas.add(
                ParadaKeeta(
                    tipo =
                        TipoParadaKeeta.ENTREGA,

                    endereco =
                        enderecoEntrega
                )
            )
        }

        val resultado =
            ResultadoKeeta(
                valor =
                    valor,

                distancia =
                    distancia,

                coletaVisivel =
                    coletaVisivel,

                entregaVisivel =
                    entregaVisivel,

                nomeColeta =
                    nomeColeta,

                enderecoColeta =
                    enderecoColeta,

                enderecoEntrega =
                    enderecoEntrega,

                quantidadePedidos =
                    if (
                        paradas.any {
                            it.tipo ==
                                    TipoParadaKeeta.ENTREGA
                        }
                    ) {
                        1
                    } else {
                        null
                    },

                paradas =
                    paradas
            )

        registrarResultado(
            resultado =
                resultado,

            modo =
                "SIMPLES"
        )

        return resultado
    }

    /*
     * =========================================================
     * OFERTA AGRUPADA / POPUP
     *
     * Estrutura observada:
     *
     * R$ 10,50
     * 4,8 km total
     *
     * endereço da coleta
     *
     * 2 pedidos para coletar
     *
     * endereço entrega 1
     *
     * endereço entrega 2
     *
     * Aceitar(...)
     *
     * Nesse layout não dependemos de existir
     * literalmente "Coleta" ou "Entrega até".
     * =========================================================
     */

    private fun analisarOfertaAgrupada(
        linhas: List<LinhaOcr>,
        valor: Double?,
        distancia: Double?,
        linhaPedidos: LinhaOcr,
        quantidadePedidos: Int?
    ): ResultadoKeeta {

        val linhaDistancia =
            linhas.firstOrNull { linha ->

                linha.texto.contains(
                    "km total",
                    ignoreCase = true
                )
            }

        val limiteFinal =
            linhas
                .firstOrNull { linha ->

                    linha.y >
                            linhaPedidos.y &&
                            ehBotaoFinal(
                                linha.texto
                            )
                }
                ?.y
                ?: Int.MAX_VALUE

        /*
         * =====================================================
         * COLETA AGRUPADA
         *
         * Tudo entre "km total" e
         * "X pedidos para coletar".
         * =====================================================
         */

        val inicioColeta =
            linhaDistancia?.y
                ?: Int.MIN_VALUE

        val linhasEnderecoColeta =
            linhas
                .filter { linha ->

                    linha.y >
                            inicioColeta &&
                            linha.y <
                            linhaPedidos.y
                }
                .filter { linha ->

                    linha.texto
                        .isNotBlank()
                }
                .filterNot { linha ->

                    ehRuidoInterface(
                        linha.texto
                    )
                }
                .filterNot { linha ->

                    linha.texto.contains(
                        "km total",
                        ignoreCase = true
                    )
                }
                .filterNot { linha ->

                    Regex(
                        """R\$\s*\d""",
                        RegexOption.IGNORE_CASE
                    ).containsMatchIn(
                        linha.texto
                    )
                }
                .sortedBy { linha ->

                    linha.y
                }

        val enderecoColeta =
            if (
                linhasEnderecoColeta.isNotEmpty()
            ) {

                juntarLinhasComSobreposicao(
                    linhasEnderecoColeta
                        .map {
                            it.texto.trim()
                        }
                )

            } else {

                null
            }

        /*
         * =====================================================
         * ENTREGAS AGRUPADAS
         *
         * Pegamos tudo entre:
         *
         * "X pedidos para coletar"
         *
         * e
         *
         * botão final.
         *
         * Depois dividimos em blocos usando
         * o espaçamento vertical do OCR.
         * =====================================================
         */

        val linhasEntregas =
            linhas
                .filter { linha ->

                    linha.y >
                            linhaPedidos.y &&
                            linha.y <
                            limiteFinal
                }
                .filter { linha ->

                    linha.texto
                        .isNotBlank()
                }
                .filterNot { linha ->

                    ehRuidoInterface(
                        linha.texto
                    )
                }
                .filterNot { linha ->

                    ehBotaoFinal(
                        linha.texto
                    )
                }
                .sortedBy { linha ->

                    linha.y
                }

        val blocosEntrega =
            agruparLinhasPorEspaco(
                linhasEntregas
            )

        val enderecosEntrega =
            blocosEntrega
                .map { bloco ->

                    juntarLinhasComSobreposicao(
                        bloco.map {
                            it.texto.trim()
                        }
                    )
                }
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .filter {
                    pareceEndereco(
                        it
                    )
                }

        /*
         * Se o agrupamento vertical não conseguiu
         * separar tudo mas sabemos que existem
         * múltiplos pedidos, ainda preservamos
         * pelo menos o bloco disponível.
         */

        val paradas =
            mutableListOf<ParadaKeeta>()

        if (
            !enderecoColeta.isNullOrBlank()
        ) {

            paradas.add(
                ParadaKeeta(
                    tipo =
                        TipoParadaKeeta.COLETA,

                    endereco =
                        enderecoColeta
                )
            )
        }

        enderecosEntrega
            .forEach { endereco ->

                paradas.add(
                    ParadaKeeta(
                        tipo =
                            TipoParadaKeeta.ENTREGA,

                        endereco =
                            endereco
                    )
                )
            }

        /*
         * Compatibilidade com o código atual:
         *
         * enderecoColeta recebe a primeira coleta.
         * enderecoEntrega recebe a primeira entrega.
         *
         * Depois adaptaremos o LeitorTela para usar
         * a lista completa.
         */
        val primeiraEntrega =
            enderecosEntrega
                .firstOrNull()

        val resultado =
            ResultadoKeeta(
                valor =
                    valor,

                distancia =
                    distancia,

                coletaVisivel =
                    !enderecoColeta.isNullOrBlank(),

                entregaVisivel =
                    enderecosEntrega.isNotEmpty(),

                nomeColeta =
                    null,

                enderecoColeta =
                    enderecoColeta,

                enderecoEntrega =
                    primeiraEntrega,

                quantidadePedidos =
                    quantidadePedidos,

                paradas =
                    paradas
            )

        registrarResultado(
            resultado =
                resultado,

            modo =
                "AGRUPADO"
        )

        return resultado
    }

    /*
     * =========================================================
     * AGRUPAMENTO VERTICAL
     *
     * Endereço com duas linhas:
     *
     * Avenida Andrômeda, 500, Avenida
     * Andrômeda, 500, Barueri, SP
     *
     * deve permanecer no mesmo bloco.
     *
     * Um espaço vertical maior indica
     * outra parada.
     * =========================================================
     */

    private fun agruparLinhasPorEspaco(
        linhas: List<LinhaOcr>
    ): List<List<LinhaOcr>> {

        if (
            linhas.isEmpty()
        ) {

            return emptyList()
        }

        val blocos =
            mutableListOf<
                    MutableList<LinhaOcr>
                    >()

        var blocoAtual =
            mutableListOf(
                linhas.first()
            )

        for (
        i in 1 until linhas.size
        ) {

            val anterior =
                linhas[i - 1]

            val atual =
                linhas[i]

            val finalAnterior =
                anterior.y +
                        anterior.altura

            val espaco =
                atual.y -
                        finalAnterior

            /*
             * Um gap maior normalmente significa
             * outra linha lógica / outra parada.
             *
             * O valor é propositalmente tolerante.
             */
            val novaParada =
                espaco >
                        35

            if (
                novaParada
            ) {

                blocos.add(
                    blocoAtual
                )

                blocoAtual =
                    mutableListOf()
            }

            blocoAtual.add(
                atual
            )
        }

        if (
            blocoAtual.isNotEmpty()
        ) {

            blocos.add(
                blocoAtual
            )
        }

        return blocos
    }

    /*
     * =========================================================
     * DETECÇÃO BÁSICA DE ENDEREÇO
     * =========================================================
     */

    private fun pareceEndereco(
        texto: String
    ): Boolean {

        if (
            texto.isBlank()
        ) {

            return false
        }

        val possuiNumero =
            Regex(
                """\d"""
            ).containsMatchIn(
                texto
            )

        val possuiTipoVia =
            Regex(
                """\b(rua|r\.|avenida|av\.?|alameda|estrada|rodovia|travessa|praça|praca)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(
                texto
            )

        return possuiNumero ||
                possuiTipoVia
    }

    /*
     * =========================================================
     * RUÍDO DA INTERFACE
     * =========================================================
     */

    private fun ehRuidoInterface(
        texto: String
    ): Boolean {

        val textoLimpo =
            texto.trim()

        if (
            textoLimpo.isBlank()
        ) {

            return true
        }

        if (
            ehBotaoFinal(
                textoLimpo
            )
        ) {

            return true
        }

        val ruidos =
            listOf(
                "de olho",
                "ganhos nessa entrega",
                "novo pedido",
                "recusar",
                "aceitar",
                "pegar"
            )

        return ruidos.any { ruido ->

            textoLimpo.contains(
                ruido,
                ignoreCase = true
            )
        }
    }

    /*
     * =========================================================
     * JUNÇÃO DE LINHAS
     *
     * Mantemos a lógica que já estava
     * funcionando no seu parser.
     * =========================================================
     */

    private fun juntarLinhasComSobreposicao(
        linhas: List<String>
    ): String {

        if (
            linhas.isEmpty()
        ) {

            return ""
        }

        var resultado =
            linhas.first()
                .trim()

        for (
        i in 1 until linhas.size
        ) {

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

        for (
        quantidade in
        maximo downTo 1
        ) {

            val fimAtual =
                palavrasAtual
                    .takeLast(
                        quantidade
                    )
                    .map {
                        normalizarPalavra(
                            it
                        )
                    }

            val inicioProxima =
                palavrasProxima
                    .take(
                        quantidade
                    )
                    .map {
                        normalizarPalavra(
                            it
                        )
                    }

            if (
                fimAtual ==
                inicioProxima
            ) {

                val restante =
                    palavrasProxima
                        .drop(
                            quantidade
                        )
                        .joinToString(
                            " "
                        )

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
         * já existe no meio/fim da atual.
         */

        for (
        quantidade in
        palavrasProxima.size downTo 2
        ) {

            val prefixoProxima =
                palavrasProxima
                    .take(
                        quantidade
                    )
                    .map {
                        normalizarPalavra(
                            it
                        )
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
                        .drop(
                            inicio
                        )
                        .take(
                            quantidade
                        )
                        .map {
                            normalizarPalavra(
                                it
                            )
                        }

                if (
                    trechoAtual ==
                    prefixoProxima
                ) {

                    val antesDaRepeticao =
                        palavrasAtual
                            .take(
                                inicio
                            )
                            .joinToString(
                                " "
                            )

                    return if (
                        antesDaRepeticao
                            .isBlank()
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
                Regex(
                    "[^a-z0-9à-ú]"
                ),
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

    /*
     * =========================================================
     * LOGS
     * =========================================================
     */

    private fun registrarResultado(
        resultado: ResultadoKeeta,
        modo: String
    ) {

        Log.d(
            "GARUPA_KEETA",
            "📦 Keeta [$modo] | " +
                    "Valor: ${resultado.valor} | " +
                    "Distância: ${resultado.distancia} | " +
                    "Pedidos: ${resultado.quantidadePedidos} | " +
                    "Paradas: ${resultado.paradas.size}"
        )

        Log.d(
            "GARUPA_KEETA_B",
            "📍 Ponto B compatível | " +
                    "Local: ${resultado.nomeColeta} | " +
                    "Endereço: ${resultado.enderecoColeta}"
        )

        Log.d(
            "GARUPA_KEETA_C",
            "🏠 Ponto C compatível | " +
                    "Endereço: ${resultado.enderecoEntrega}"
        )

        resultado.paradas
            .forEachIndexed { indice, parada ->

                Log.d(
                    "GARUPA_PARADA",
                    "🧭 ${indice + 1}/${resultado.paradas.size} | " +
                            "${parada.tipo} | " +
                            "Nome: ${parada.nome} | " +
                            "Endereço: ${parada.endereco}"
                )
            }
    }
}