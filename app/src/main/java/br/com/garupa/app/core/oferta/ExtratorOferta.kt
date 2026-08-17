package br.com.garupa.app.core.oferta

import android.util.Log
import br.com.garupa.app.core.leitura.LinhaOcr
import kotlin.math.abs

data class ResultadoExtratorOferta(
    val valor: Double?,
    val distanciaKm: Double?,
    val paradasObservadas: List<ParadaOferta>
)

class ExtratorOferta {

    private val agrupadorBlocosOferta =
        AgrupadorBlocosOferta()

    private val classificadorEvidencia =
        ClassificadorEvidenciaOferta()

    fun extrair(
        linhas: List<LinhaOcr>
    ): ResultadoExtratorOferta {

        val linhasOrdenadas =
            linhas.sortedBy {
                it.y
            }

        agrupadorBlocosOferta.agrupar(
            linhasOrdenadas
        )

        val textoCompleto =
            linhasOrdenadas.joinToString(
                "\n"
            ) {
                it.texto
            }

        val valor =
            extrairValor(
                textoCompleto
            )

        val distanciaKm =
            extrairDistancia(
                textoCompleto
            )

        val paradasRotuladasBase =
            extrairParadasPorRotulo(
                linhasOrdenadas
            )

        val paradasRotuladas =
            enriquecerParadasRotuladas(
                paradas =
                    paradasRotuladasBase,

                linhas =
                    linhasOrdenadas
            )

        /*
         * =====================================================
         * MULTIPARADA POR QUANTIDADE EXPLÍCITA
         * =====================================================
         *
         * Exemplo observado:
         *
         * Alameda Rio Negro, 1033
         * 2 pedidos para coletar
         * Avenida Andrômeda, 500 ...
         * Avenida Andrômeda, 500 ...
         *
         * Quando há rótulo explícito de parada, preservamos o
         * fluxo rotulado. Quando não há, a quantidade explícita
         * pode estruturar 1 coleta + N entregas.
         */
        val paradasMultiparada =
            if (
                paradasRotuladas.isEmpty()
            ) {

                extrairMultiparadaPorQuantidade(
                    linhasOrdenadas
                )

            } else {

                emptyList()
            }

        val paradasPorContextoEntrega =
            if (
                paradasMultiparada.isEmpty()
            ) {

                extrairEntregasPorContexto(
                    linhasOrdenadas
                )

            } else {

                emptyList()
            }

        val paradasInferidasSemRotulo =
            if (
                paradasRotuladas.isEmpty() &&
                paradasMultiparada.isEmpty()
            ) {

                extrairParadasPorSequenciaSemantica(
                    linhasOrdenadas
                )

            } else {

                emptyList()
            }

        val enderecosGenericos =
            if (
                paradasMultiparada.isEmpty()
            ) {

                extrairParadasPorEndereco(
                    linhasOrdenadas
                )

            } else {

                emptyList()
            }

        extrairCandidatosEspaciais(
            linhasOrdenadas
        )

        val todas =
            mutableListOf<ParadaOferta>()

        todas.addAll(
            paradasRotuladas
        )

        todas.addAll(
            paradasMultiparada
        )

        todas.addAll(
            paradasPorContextoEntrega
        )

        todas.addAll(
            paradasInferidasSemRotulo
        )

        todas.addAll(
            enderecosGenericos
        )

        val paradasObservadas =
            deduplicarParadas(
                todas
            )

        val resultado =
            ResultadoExtratorOferta(
                valor =
                    valor,

                distanciaKm =
                    distanciaKm,

                paradasObservadas =
                    paradasObservadas
            )

        Log.d(
            "GARUPA_EXTRATOR_OFERTA",
            "🧠 Extração genérica | " +
                    "valor=${resultado.valor} | " +
                    "distancia=${resultado.distanciaKm} | " +
                    "paradas=${resultado.paradasObservadas.size}"
        )

        resultado
            .paradasObservadas
            .forEachIndexed { indice, parada ->

                Log.d(
                    "GARUPA_EXTRATOR_PARADA",
                    "🧭 ${indice + 1}/${resultado.paradasObservadas.size} | " +
                            "tipo=${parada.tipo} | " +
                            "ordem=${parada.ordem} | " +
                            "nome=${parada.nome} | " +
                            "endereco=${parada.endereco} | " +
                            "confianca=${"%.2f".format(parada.confianca)} | " +
                            "evidencias=${parada.evidencias.joinToString("+")}"
                )
            }

        return resultado
    }

    private fun extrairValor(
        textoCompleto: String
    ): Double? {

        return Regex(
            """R\$\s*(\d+[.,]\d{2})""",
            RegexOption.IGNORE_CASE
        )
            .find(
                textoCompleto
            )
            ?.groupValues
            ?.getOrNull(
                1
            )
            ?.replace(
                ",",
                "."
            )
            ?.toDoubleOrNull()
    }

    private fun extrairDistancia(
        textoCompleto: String
    ): Double? {

        return Regex(
            """(\d+(?:[.,]\d+)?)\s*km""",
            RegexOption.IGNORE_CASE
        )
            .findAll(
                textoCompleto
            )
            .mapNotNull { resultado ->

                resultado
                    .groupValues
                    .getOrNull(
                        1
                    )
                    ?.replace(
                        ",",
                        "."
                    )
                    ?.toDoubleOrNull()
            }
            .firstOrNull { distancia ->

                distancia > 0.0 &&
                        distancia <= 100.0
            }
    }

    private fun extrairParadasPorRotulo(
        linhas: List<LinhaOcr>
    ): List<ParadaOferta> {

        val resultado =
            mutableListOf<ParadaOferta>()

        linhas.forEach { linhaRotulo ->

            val classificacao =
                classificarRotuloParada(
                    linhaRotulo.texto
                )
                    ?: return@forEach

            val linhaAssociada =
                procurarTextoAssociado(
                    rotulo =
                        linhaRotulo,

                    linhas =
                        linhas
                )

            val textoAssociado =
                linhaAssociada
                    ?.texto
                    ?.let {
                        limparTexto(
                            it
                        )
                    }
                    ?.takeIf {
                        it.isNotBlank()
                    }

            val endereco =
                textoAssociado
                    ?.takeIf {
                        pareceEndereco(
                            it
                        )
                    }

            val nome =
                textoAssociado
                    ?.takeIf {
                        endereco == null
                    }

            val evidencias =
                mutableSetOf(
                    FonteEvidenciaParada.ROTULO,
                    FonteEvidenciaParada.POSICAO
                )

            if (
                textoAssociado != null
            ) {

                evidencias.add(
                    FonteEvidenciaParada.TEXTO
                )
            }

            resultado.add(
                ParadaOferta(
                    tipo =
                        classificacao.tipo,

                    nome =
                        nome,

                    endereco =
                        endereco,

                    ordem =
                        classificacao.ordem,

                    x =
                        linhaRotulo.x,

                    y =
                        linhaRotulo.y,

                    evidencias =
                        evidencias,

                    confianca =
                        if (
                            textoAssociado != null
                        ) {
                            0.97
                        } else {
                            0.82
                        }
                )
            )
        }

        return resultado
    }

    private fun enriquecerParadasRotuladas(
        paradas: List<ParadaOferta>,
        linhas: List<LinhaOcr>
    ): List<ParadaOferta> {

        if (
            paradas.isEmpty()
        ) {

            return emptyList()
        }

        val classificadas =
            classificadorEvidencia.classificar(
                linhas
            )

        val enderecos =
            classificadas.filter {
                it.tipo ==
                        TipoEvidenciaOferta.ENDERECO
            }

        return paradas.map { parada ->

            if (
                !parada.endereco.isNullOrBlank()
            ) {

                return@map parada
            }

            val yParada =
                parada.y
                    ?: return@map parada

            val xParada =
                parada.x
                    ?: return@map parada

            val enderecoCompativel =
                enderecos
                    .filter { evidencia ->

                        val linha =
                            evidencia.linha

                        if (
                            linha.y <= yParada
                        ) {
                            return@filter false
                        }

                        val distanciaY =
                            linha.y -
                                    yParada

                        if (
                            distanciaY !in 20..220
                        ) {
                            return@filter false
                        }

                        val diferencaX =
                            abs(
                                linha.x -
                                        xParada
                            )

                        diferencaX <= 120
                    }
                    .minByOrNull {
                        it.linha.y
                    }

            if (
                enderecoCompativel == null
            ) {

                return@map parada
            }

            val endereco =
                limparTexto(
                    enderecoCompativel
                        .linha
                        .texto
                )

            Log.d(
                "GARUPA_INFERENCIA_PARADA",
                "🧩 Parada rotulada enriquecida | " +
                        "tipo=${parada.tipo} | " +
                        "nome=${parada.nome} | " +
                        "endereco=$endereco"
            )

            parada.copy(
                endereco =
                    endereco,

                evidencias =
                    parada.evidencias +
                            FonteEvidenciaParada.TEXTO +
                            FonteEvidenciaParada.POSICAO,

                confianca =
                    maxOf(
                        parada.confianca,
                        0.97
                    )
            )
        }
    }

    private fun extrairEntregasPorContexto(
        linhas: List<LinhaOcr>
    ): List<ParadaOferta> {

        val classificadas =
            classificadorEvidencia.classificar(
                linhas
            )

        val contextosEntrega =
            classificadas.filter { evidencia ->

                evidencia.tipo ==
                        TipoEvidenciaOferta.TEMPO &&
                        evidencia.linha.texto.contains(
                            "entrega",
                            ignoreCase = true
                        )
            }

        val enderecos =
            classificadas.filter {
                it.tipo ==
                        TipoEvidenciaOferta.ENDERECO
            }

        val resultado =
            mutableListOf<ParadaOferta>()

        contextosEntrega.forEach { contexto ->

            val linhaContexto =
                contexto.linha

            val endereco =
                enderecos
                    .filter { evidencia ->

                        val linha =
                            evidencia.linha

                        if (
                            linha.y <=
                            linhaContexto.y
                        ) {
                            return@filter false
                        }

                        val distanciaY =
                            linha.y -
                                    (
                                            linhaContexto.y +
                                                    linhaContexto.altura
                                            )

                        if (
                            distanciaY !in 0..120
                        ) {
                            return@filter false
                        }

                        abs(
                            linha.x -
                                    linhaContexto.x
                        ) <= 120
                    }
                    .minByOrNull {
                        it.linha.y
                    }
                    ?: return@forEach

            resultado.add(
                ParadaOferta(
                    tipo =
                        TipoParadaOferta.ENTREGA,

                    nome =
                        null,

                    endereco =
                        limparTexto(
                            endereco
                                .linha
                                .texto
                        ),

                    ordem =
                        null,

                    x =
                        endereco.linha.x,

                    y =
                        endereco.linha.y,

                    evidencias =
                        setOf(
                            FonteEvidenciaParada.TEXTO,
                            FonteEvidenciaParada.POSICAO
                        ),

                    confianca =
                        0.91
                )
            )
        }

        return resultado
    }

    /*
     * =========================================================
     * MULTIPARADA POR QUANTIDADE EXPLÍCITA
     * =========================================================
     *
     * Interpreta estruturas como:
     *
     * ENDEREÇO DA COLETA
     * 2 pedidos para coletar
     * ENDEREÇO DA ENTREGA 1
     * ENDEREÇO DA ENTREGA 2
     *
     * Regras de segurança:
     *
     * - exige quantidade explícita de pedidos para coletar;
     * - exige um endereço imediatamente acima do contexto;
     * - exige pelo menos N ocorrências de endereço abaixo;
     * - preserva duas entregas no mesmo endereço quando elas
     *   aparecem em posições Y distintas;
     * - não interfere em telas com rótulo explícito de parada.
     */
    private fun extrairMultiparadaPorQuantidade(
        linhas: List<LinhaOcr>
    ): List<ParadaOferta> {

        val classificadas =
            classificadorEvidencia.classificar(
                linhas
            )

        val contexto =
            linhas
                .mapNotNull { linha ->

                    val texto =
                        limparTexto(
                            linha.texto.lowercase()
                        )

                    val match =
                        Regex(
                            """\b(\d+)\s+pedidos?\s+(?:para\s+)?coletar\b""",
                            RegexOption.IGNORE_CASE
                        ).find(
                            texto
                        )

                    val quantidade =
                        match
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.toIntOrNull()

                    if (
                        quantidade != null &&
                        quantidade in 2..10
                    ) {

                        linha to quantidade

                    } else {

                        null
                    }
                }
                .firstOrNull()
                ?: return emptyList()

        val linhaContexto =
            contexto.first

        val quantidadePedidos =
            contexto.second

        val enderecos =
            classificadas
                .filter {
                    it.tipo ==
                            TipoEvidenciaOferta.ENDERECO
                }
                .map {
                    it.linha
                }
                .sortedBy {
                    it.y
                }

        /*
         * A coleta deve ser o endereço mais próximo acima do
         * texto "N pedidos para coletar".
         */
        val enderecoColeta =
            enderecos
                .filter { linha ->

                    linha.y <
                            linhaContexto.y
                }
                .filter { linha ->

                    val distanciaY =
                        linhaContexto.y -
                                (
                                        linha.y +
                                                linha.altura
                                        )

                    distanciaY in 0..220
                }
                .filter { linha ->

                    abs(
                        linha.x -
                                linhaContexto.x
                    ) <= 160
                }
                .maxByOrNull {
                    it.y
                }
                ?: return emptyList()

        /*
         * As entregas aparecem abaixo do contexto. Ocorrências
         * repetidas muito próximas são tratadas como fragmentos
         * do mesmo bloco OCR; ocorrências separadas verticalmente
         * continuam sendo paradas distintas, mesmo com endereço
         * textual igual.
         */
        val candidatosEntrega =
            enderecos
                .filter { linha ->

                    linha.y >
                            linhaContexto.y
                }
                .filter { linha ->

                    abs(
                        linha.x -
                                linhaContexto.x
                    ) <= 180
                }
                .sortedBy {
                    it.y
                }

        val entregasConsolidadas =
            mutableListOf<LinhaOcr>()

        candidatosEntrega.forEach { candidata ->

            val ultima =
                entregasConsolidadas
                    .lastOrNull()

            if (
                ultima == null
            ) {

                entregasConsolidadas.add(
                    candidata
                )

            } else {

                val mesmoNucleo =
                    extrairNucleoEndereco(
                        normalizar(
                            ultima.texto
                        )
                    ) ==
                            extrairNucleoEndereco(
                                normalizar(
                                    candidata.texto
                                )
                            )

                val distanciaVertical =
                    candidata.y -
                            (
                                    ultima.y +
                                            ultima.altura
                                    )

                /*
                 * Até 70 px: provável fragmento OCR do mesmo
                 * endereço. Acima disso: nova ocorrência visual,
                 * portanto pode ser outra entrega legítima.
                 */
                if (
                    mesmoNucleo &&
                    distanciaVertical in -10..70
                ) {

                    val melhor =
                        if (
                            candidata.texto.length >
                            ultima.texto.length
                        ) {

                            candidata

                        } else {

                            ultima
                        }

                    entregasConsolidadas[
                        entregasConsolidadas.lastIndex
                    ] = melhor

                } else {

                    entregasConsolidadas.add(
                        candidata
                    )
                }
            }
        }

        if (
            entregasConsolidadas.size <
            quantidadePedidos
        ) {

            Log.d(
                "GARUPA_INFERENCIA_PARADA",
                "⏳ Multiparada incompleta | " +
                        "esperadas=$quantidadePedidos | " +
                        "encontradas=${entregasConsolidadas.size}"
            )

            return emptyList()
        }

        val entregasSelecionadas =
            entregasConsolidadas
                .take(
                    quantidadePedidos
                )

        val resultado =
            mutableListOf<ParadaOferta>()

        resultado.add(
            ParadaOferta(
                tipo =
                    TipoParadaOferta.COLETA,

                nome =
                    null,

                endereco =
                    limparTexto(
                        enderecoColeta.texto
                    ),

                ordem =
                    1,

                x =
                    enderecoColeta.x,

                y =
                    enderecoColeta.y,

                evidencias =
                    setOf(
                        FonteEvidenciaParada.TEXTO,
                        FonteEvidenciaParada.POSICAO
                    ),

                confianca =
                    0.94
            )
        )

        entregasSelecionadas
            .forEachIndexed { indice, linha ->

                resultado.add(
                    ParadaOferta(
                        tipo =
                            TipoParadaOferta.ENTREGA,

                        nome =
                            null,

                        endereco =
                            limparTexto(
                                linha.texto
                            ),

                        ordem =
                            indice + 1,

                        x =
                            linha.x,

                        y =
                            linha.y,

                        evidencias =
                            setOf(
                                FonteEvidenciaParada.TEXTO,
                                FonteEvidenciaParada.POSICAO
                            ),

                        confianca =
                            0.92
                    )
                )
            }

        Log.d(
            "GARUPA_INFERENCIA_PARADA",
            "🧠 Multiparada estruturada | " +
                    "coleta=${enderecoColeta.texto} | " +
                    "pedidos=$quantidadePedidos | " +
                    "entregas=${entregasSelecionadas.size}"
        )

        return resultado
    }

    private fun extrairParadasPorSequenciaSemantica(
        linhas: List<LinhaOcr>
    ): List<ParadaOferta> {

        val classificadas =
            classificadorEvidencia.classificar(
                linhas
            )

        val nomes =
            classificadas.filter {
                it.tipo ==
                        TipoEvidenciaOferta.NOME_LOCAL
            }

        val enderecos =
            classificadas.filter {
                it.tipo ==
                        TipoEvidenciaOferta.ENDERECO
            }

        if (
            nomes.isEmpty() ||
            enderecos.isEmpty()
        ) {

            return emptyList()
        }

        val pares =
            mutableListOf<
                    Pair<
                            EvidenciaClassificadaOferta,
                            EvidenciaClassificadaOferta
                            >
                    >()

        enderecos.forEach { endereco ->

            val linhaEndereco =
                endereco.linha

            val nomeCompativel =
                nomes
                    .filter { nome ->

                        val linhaNome =
                            nome.linha

                        if (
                            linhaNome.y >=
                            linhaEndereco.y
                        ) {

                            return@filter false
                        }

                        val distanciaY =
                            linhaEndereco.y -
                                    (
                                            linhaNome.y +
                                                    linhaNome.altura
                                            )

                        if (
                            distanciaY !in 0..100
                        ) {

                            return@filter false
                        }

                        val diferencaX =
                            abs(
                                linhaNome.x -
                                        linhaEndereco.x
                            )

                        diferencaX <= 90
                    }
                    .minByOrNull { nome ->

                        linhaEndereco.y -
                                (
                                        nome.linha.y +
                                                nome.linha.altura
                                        )
                    }

            if (
                nomeCompativel != null
            ) {

                pares.add(
                    nomeCompativel to
                            endereco
                )
            }
        }

        val paresUnicos =
            pares.distinctBy {

                "${it.first.linha.x}|" +
                        "${it.first.linha.y}|" +
                        "${it.second.linha.x}|" +
                        "${it.second.linha.y}"
            }

        /*
         * Só inferimos coleta + entrega quando
         * existe exatamente um par claro.
         *
         * Em telas multiparada, preferimos não chutar.
         */
        if (
            paresUnicos.size != 1
        ) {

            return emptyList()
        }

        val (
            evidenciaNome,
            evidenciaEndereco
        ) =
            paresUnicos.first()

        val linhaNome =
            evidenciaNome.linha

        val linhaEndereco =
            evidenciaEndereco.linha

        Log.d(
            "GARUPA_INFERENCIA_PARADA",
            "🧠 Sequência espacial sem rótulo | " +
                    "nome=${linhaNome.texto} | " +
                    "endereco=${linhaEndereco.texto}"
        )

        return listOf(
            ParadaOferta(
                tipo =
                    TipoParadaOferta.COLETA,

                nome =
                    limparTexto(
                        linhaNome.texto
                    ),

                endereco =
                    null,

                ordem =
                    1,

                x =
                    linhaNome.x,

                y =
                    linhaNome.y,

                evidencias =
                    setOf(
                        FonteEvidenciaParada.TEXTO,
                        FonteEvidenciaParada.POSICAO
                    ),

                confianca =
                    0.82
            ),

            ParadaOferta(
                tipo =
                    TipoParadaOferta.ENTREGA,

                nome =
                    null,

                endereco =
                    limparTexto(
                        linhaEndereco.texto
                    ),

                ordem =
                    1,

                x =
                    linhaEndereco.x,

                y =
                    linhaEndereco.y,

                evidencias =
                    setOf(
                        FonteEvidenciaParada.TEXTO,
                        FonteEvidenciaParada.POSICAO
                    ),

                confianca =
                    0.86
            )
        )
    }

    private fun procurarTextoAssociado(
        rotulo: LinhaOcr,
        linhas: List<LinhaOcr>
    ): LinhaOcr? {

        return linhas
            .asSequence()
            .filter {
                it !== rotulo
            }
            .filter {
                it.texto.isNotBlank()
            }
            .filter {
                it.y > rotulo.y
            }
            .filter { candidata ->

                val distanciaVertical =
                    candidata.y -
                            (
                                    rotulo.y +
                                            rotulo.altura
                                    )

                distanciaVertical in
                        -10..160
            }
            .filterNot {
                ehRuidoInterface(
                    it.texto
                )
            }
            .filter {
                classificarRotuloParada(
                    it.texto
                ) == null
            }
            .filter {
                candidatoEstaNaMesmaRegiaoHorizontal(
                    rotulo,
                    it
                )
            }
            .minByOrNull {
                calcularCustoEspacial(
                    rotulo,
                    it
                )
            }
    }

    private fun candidatoEstaNaMesmaRegiaoHorizontal(
        rotulo: LinhaOcr,
        candidata: LinhaOcr
    ): Boolean {

        val centroRotulo =
            rotulo.x +
                    rotulo.largura / 2

        val centroCandidata =
            candidata.x +
                    candidata.largura / 2

        return existeSobreposicaoHorizontal(
            rotulo,
            candidata
        ) ||
                abs(
                    centroRotulo -
                            centroCandidata
                ) <= 180
    }

    private fun calcularCustoEspacial(
        rotulo: LinhaOcr,
        candidata: LinhaOcr
    ): Int {

        val centroRotulo =
            rotulo.x +
                    rotulo.largura / 2

        val centroCandidata =
            candidata.x +
                    candidata.largura / 2

        val distanciaX =
            abs(
                centroRotulo -
                        centroCandidata
            )

        val distanciaY =
            abs(
                candidata.y -
                        (
                                rotulo.y +
                                        rotulo.altura
                                )
            )

        val penalidade =
            if (
                existeSobreposicaoHorizontal(
                    rotulo,
                    candidata
                )
            ) {
                0
            } else {
                120
            }

        return distanciaY * 3 +
                distanciaX +
                penalidade
    }

    private fun existeSobreposicaoHorizontal(
        a: LinhaOcr,
        b: LinhaOcr
    ): Boolean {

        return maxOf(
            a.x,
            b.x
        ) <
                minOf(
                    a.x +
                            a.largura,

                    b.x +
                            b.largura
                )
    }

    private fun extrairParadasPorEndereco(
        linhas: List<LinhaOcr>
    ): List<ParadaOferta> {

        return linhas
            .filter {
                pareceEndereco(
                    it.texto
                )
            }
            .filterNot {
                ehRuidoInterface(
                    it.texto
                )
            }
            .map { linha ->

                ParadaOferta(
                    tipo =
                        TipoParadaOferta.DESCONHECIDA,

                    nome =
                        null,

                    endereco =
                        limparTexto(
                            linha.texto
                        ),

                    ordem =
                        null,

                    x =
                        linha.x,

                    y =
                        linha.y,

                    evidencias =
                        setOf(
                            FonteEvidenciaParada.TEXTO,
                            FonteEvidenciaParada.POSICAO
                        ),

                    confianca =
                        0.60
                )
            }
    }

    private fun extrairCandidatosEspaciais(
        linhas: List<LinhaOcr>
    ): List<LinhaOcr> {

        val candidatos =
            linhas
                .filter { linha ->

                    val texto =
                        limparTexto(
                            linha.texto
                        )

                    texto.length >= 4 &&
                            !ehRuidoInterface(
                                texto
                            ) &&
                            classificarRotuloParada(
                                texto
                            ) == null &&
                            !texto.contains(
                                "R$",
                                ignoreCase = true
                            )
                }
                .sortedBy {
                    it.y
                }

        candidatos
            .forEachIndexed { indice, linha ->

                Log.d(
                    "GARUPA_CANDIDATO_PARADA",
                    "🔎 ${indice + 1}/${candidatos.size} | " +
                            "texto=${limparTexto(linha.texto)} | " +
                            "x=${linha.x} | " +
                            "y=${linha.y} | " +
                            "endereco=${pareceEndereco(linha.texto)}"
                )
            }

        return candidatos
    }

    private data class RotuloParada(
        val tipo: TipoParadaOferta,
        val ordem: Int?
    )

    private fun classificarRotuloParada(
        texto: String
    ): RotuloParada? {

        val normalizado =
            limparTexto(
                texto.lowercase()
            )

        Regex(
            """^coleta\s*(\d+)?(?:\s*[•\-].*)?$""",
            RegexOption.IGNORE_CASE
        )
            .find(
                normalizado
            )
            ?.let {

                return RotuloParada(
                    TipoParadaOferta.COLETA,
                    it.groupValues
                        .getOrNull(
                            1
                        )
                        ?.takeIf { valor ->
                            valor.isNotBlank()
                        }
                        ?.toIntOrNull()
                )
            }

        Regex(
            """^entrega\s*(\d+)?(?:\s*[•\-].*)?$""",
            RegexOption.IGNORE_CASE
        )
            .find(
                normalizado
            )
            ?.let {

                return RotuloParada(
                    TipoParadaOferta.ENTREGA,
                    it.groupValues
                        .getOrNull(
                            1
                        )
                        ?.takeIf { valor ->
                            valor.isNotBlank()
                        }
                        ?.toIntOrNull()
                )
            }

        Regex(
            """^\S{1,2}\s+(coleta|entrega)$""",
            RegexOption.IGNORE_CASE
        )
            .find(
                normalizado
            )
            ?.let {

                val tipo =
                    if (
                        it.groupValues[1]
                            .equals(
                                "coleta",
                                ignoreCase = true
                            )
                    ) {

                        TipoParadaOferta.COLETA

                    } else {

                        TipoParadaOferta.ENTREGA
                    }

                return RotuloParada(
                    tipo =
                        tipo,

                    ordem =
                        null
                )
            }

        return null
    }

    private fun deduplicarParadas(
        paradas: List<ParadaOferta>
    ): List<ParadaOferta> {

        val resultado =
            mutableListOf<ParadaOferta>()

        paradas
            .sortedBy {
                it.y ?: Int.MAX_VALUE
            }
            .forEach { candidata ->

                val indice =
                    resultado.indexOfFirst { existente ->

                        paradasSaoEquivalentes(
                            existente,
                            candidata
                        )
                    }

                if (
                    indice < 0
                ) {

                    resultado.add(
                        candidata
                    )

                } else {

                    resultado[indice] =
                        escolherMelhorParada(
                            resultado[indice],
                            candidata
                        )
                }
            }

        return resultado
            .sortedWith(
                compareBy<ParadaOferta> {

                    when (
                        it.tipo
                    ) {

                        TipoParadaOferta.COLETA ->
                            0

                        TipoParadaOferta.ENTREGA ->
                            1

                        TipoParadaOferta.DESCONHECIDA ->
                            2
                    }
                }
                    .thenBy {
                        it.ordem ?: Int.MAX_VALUE
                    }
                    .thenBy {
                        it.y ?: Int.MAX_VALUE
                    }
            )
    }

    /*
     * =========================================================
     * DEDUPLICAÇÃO DE PARADAS
     * =========================================================
     *
     * Agora também detectamos endereços cujo começo
     * é igual até número da via.
     *
     * Exemplo:
     *
     * Avenida Andrômeda, 500, Avenida
     * Avenida Andrômeda, 500, Barueri
     *
     * → mesma parada observada.
     */

    private fun paradasSaoEquivalentes(
        a: ParadaOferta,
        b: ParadaOferta
    ): Boolean {

        /*
         * Duas paradas classificadas do mesmo tipo com ordens
         * explícitas diferentes são paradas distintas, mesmo que
         * o endereço seja exatamente o mesmo.
         *
         * Isso é essencial para multiparadas como:
         * ENTREGA 1 -> Avenida Andrômeda, 500
         * ENTREGA 2 -> Avenida Andrômeda, 500
         */
        if (
            a.tipo != TipoParadaOferta.DESCONHECIDA &&
            b.tipo != TipoParadaOferta.DESCONHECIDA &&
            a.tipo == b.tipo &&
            a.ordem != null &&
            b.ordem != null &&
            a.ordem != b.ordem
        ) {

            return false
        }

        val enderecoA =
            a.endereco
                ?.let {
                    normalizar(
                        it
                    )
                }

        val enderecoB =
            b.endereco
                ?.let {
                    normalizar(
                        it
                    )
                }

        if (
            !enderecoA.isNullOrBlank() &&
            !enderecoB.isNullOrBlank()
        ) {

            /*
             * Igualdade direta ou uma versão
             * contida na outra.
             */
            if (
                enderecoA ==
                enderecoB ||
                enderecoA.contains(
                    enderecoB
                ) ||
                enderecoB.contains(
                    enderecoA
                )
            ) {

                return true
            }

            /*
             * Comparação do núcleo:
             *
             * nome da via + número.
             */
            val nucleoA =
                extrairNucleoEndereco(
                    enderecoA
                )

            val nucleoB =
                extrairNucleoEndereco(
                    enderecoB
                )

            if (
                nucleoA.isNotBlank() &&
                nucleoB.isNotBlank() &&
                nucleoA ==
                nucleoB
            ) {

                return true
            }
        }

        if (
            a.tipo !=
            TipoParadaOferta.DESCONHECIDA &&
            b.tipo !=
            TipoParadaOferta.DESCONHECIDA &&
            a.tipo ==
            b.tipo &&
            a.ordem != null &&
            b.ordem != null &&
            a.ordem ==
            b.ordem
        ) {

            return true
        }

        val nomeA =
            a.nome
                ?.let {
                    normalizar(
                        it
                    )
                }

        val nomeB =
            b.nome
                ?.let {
                    normalizar(
                        it
                    )
                }

        return a.tipo ==
                b.tipo &&
                !nomeA.isNullOrBlank() &&
                !nomeB.isNullOrBlank() &&
                (
                        nomeA ==
                                nomeB ||
                                nomeA.contains(
                                    nomeB
                                ) ||
                                nomeB.contains(
                                    nomeA
                                )
                        )
    }

    private fun escolherMelhorParada(
        a: ParadaOferta,
        b: ParadaOferta
    ): ParadaOferta {

        if (
            a.tipo ==
            b.tipo &&
            a.tipo !=
            TipoParadaOferta.DESCONHECIDA
        ) {

            val nome =
                when {

                    !a.nome.isNullOrBlank() &&
                            b.nome.isNullOrBlank() ->
                        a.nome

                    a.nome.isNullOrBlank() &&
                            !b.nome.isNullOrBlank() ->
                        b.nome

                    else -> {

                        if (
                            pontuarQualidadeParada(
                                b
                            ) >
                            pontuarQualidadeParada(
                                a
                            )
                        ) {

                            b.nome

                        } else {

                            a.nome
                        }
                    }
                }

            val endereco =
                when {

                    !a.endereco.isNullOrBlank() ->
                        a.endereco

                    !b.endereco.isNullOrBlank() ->
                        b.endereco

                    else ->
                        null
                }

            return a.copy(
                nome =
                    nome,

                endereco =
                    endereco,

                ordem =
                    a.ordem ?:
                    b.ordem,

                evidencias =
                    a.evidencias +
                            b.evidencias,

                confianca =
                    maxOf(
                        a.confianca,
                        b.confianca
                    )
            )
        }

        return if (
            pontuarQualidadeParada(
                b
            ) >
            pontuarQualidadeParada(
                a
            )
        ) {

            b

        } else {

            a
        }
    }

    private fun pontuarQualidadeParada(
        parada: ParadaOferta
    ): Int {

        var pontos =
            0

        if (
            parada.tipo !=
            TipoParadaOferta.DESCONHECIDA
        ) {

            pontos += 30
        }

        if (
            parada.ordem != null
        ) {

            pontos += 10
        }

        if (
            !parada.nome.isNullOrBlank()
        ) {

            pontos +=
                20 +
                        parada.nome.length
        }

        if (
            !parada.endereco.isNullOrBlank()
        ) {

            pontos +=
                40 +
                        parada.endereco.length
        }

        pontos +=
            (
                    parada.confianca *
                            20
                    ).toInt()

        return pontos
    }

    /*
     * =========================================================
     * NÚCLEO DO ENDEREÇO
     * =========================================================
     *
     * Mantemos as palavras até encontrar o primeiro número.
     *
     * Exemplos:
     *
     * avenida andromeda 500 avenida
     * avenida andromeda 500 barueri
     *
     * ambos viram:
     *
     * avenida andromeda 500
     */

    private fun extrairNucleoEndereco(
        enderecoNormalizado: String
    ): String {

        val palavras =
            enderecoNormalizado
                .split(
                    Regex("\\s+")
                )
                .filter {
                    it.isNotBlank()
                }

        if (
            palavras.isEmpty()
        ) {

            return ""
        }

        val resultado =
            mutableListOf<String>()

        var encontrouNumero =
            false

        for (
        palavra in palavras
        ) {

            resultado.add(
                palavra
            )

            if (
                palavra.any {
                    it.isDigit()
                }
            ) {

                encontrouNumero =
                    true

                break
            }
        }

        return if (
            encontrouNumero
        ) {

            resultado.joinToString(
                " "
            )

        } else {

            enderecoNormalizado
        }
    }

    private fun pareceEndereco(
        texto: String
    ): Boolean {

        val possuiVia =
            Regex(
                """\b(rua|r\.|avenida|av\.?|alameda|estrada|rodovia|travessa|praça|praca)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(
                texto
            )

        val possuiNumero =
            Regex(
                """\b\d{1,6}\b"""
            ).containsMatchIn(
                texto
            )

        return possuiVia &&
                possuiNumero
    }

    private fun ehRuidoInterface(
        texto: String
    ): Boolean {

        val ruidos =
            listOf(
                "r$",
                "distância total",
                "distancia total",
                "km",
                "aceitar",
                "recusar",
                "rejeitar",
                "pegar",
                "ganhos",
                "tempo aproximado",
                "possibilidade de devolução",
                "possibilidade de devolucao",
                "rota para moto",
                "aceitar pedido",
                "google lens",
                "editar",
                "excluir",
                "compartilhar favorito"
            )

        return ruidos.any { ruido ->

            texto.contains(
                ruido,
                ignoreCase = true
            )
        }
    }

    private fun limparTexto(
        texto: String
    ): String {

        return texto
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    private fun normalizar(
        texto: String
    ): String {

        return limparTexto(
            texto.lowercase()
        )
            .replace(
                Regex(
                    "[^a-z0-9à-ú ]"
                ),
                ""
            )
    }
}