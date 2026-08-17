package br.com.garupa.app.core.oferta

import br.com.garupa.app.core.leitura.LinhaOcr

data class EvidenciaClassificadaOferta(
    val linha: LinhaOcr,
    val tipo: TipoEvidenciaOferta,
    val confianca: Double
)

class ClassificadorEvidenciaOferta {

    fun classificar(
        linhas: List<LinhaOcr>
    ): List<EvidenciaClassificadaOferta> {

        return linhas.map { linha ->
            classificarLinha(
                linha
            )
        }
    }

    private fun classificarLinha(
        linha: LinhaOcr
    ): EvidenciaClassificadaOferta {

        val texto =
            limparTexto(
                linha.texto
            )

        /*
         * =====================================================
         * VALOR
         * =====================================================
         */
        if (
            Regex(
                """R\$\s*\d+[.,]\d{2}""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(
                texto
            )
        ) {

            return resultado(
                linha,
                TipoEvidenciaOferta.VALOR,
                0.99
            )
        }

        /*
         * =====================================================
         * DISTÂNCIA
         * =====================================================
         */
        if (
            Regex(
                """\d+(?:[.,]\d+)?\s*km\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(
                texto
            )
        ) {

            return resultado(
                linha,
                TipoEvidenciaOferta.DISTANCIA,
                0.97
            )
        }

        /*
         * =====================================================
         * AÇÃO
         * =====================================================
         */
        if (
            Regex(
                """\b(aceitar|recusar|rejeitar|pegar)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(
                texto
            )
        ) {

            return resultado(
                linha,
                TipoEvidenciaOferta.ACAO,
                0.99
            )
        }

        /*
         * =====================================================
         * RÓTULO DE PARADA
         * =====================================================
         */
        if (
            ehRotuloParada(
                texto
            )
        ) {

            return resultado(
                linha,
                TipoEvidenciaOferta.ROTULO_PARADA,
                0.98
            )
        }

        /*
         * =====================================================
         * ENDEREÇO
         * =====================================================
         */
        if (
            pareceEndereco(
                texto
            )
        ) {

            return resultado(
                linha,
                TipoEvidenciaOferta.ENDERECO,
                0.95
            )
        }

        /*
         * =====================================================
         * TEMPO
         * =====================================================
         */
        if (
            pareceTempo(
                texto
            )
        ) {

            return resultado(
                linha,
                TipoEvidenciaOferta.TEMPO,
                0.90
            )
        }

        /*
         * =====================================================
         * COMPLEMENTO DE LOCAL
         * =====================================================
         */
        if (
            pareceComplementoLocal(
                texto
            )
        ) {

            return resultado(
                linha,
                TipoEvidenciaOferta.COMPLEMENTO_LOCAL,
                0.72
            )
        }

        /*
         * =====================================================
         * CONTEXTO DE PEDIDOS / MULTIPARADAS
         * =====================================================
         */
        if (
            pareceContextoQuantidadePedidos(
                texto
            )
        ) {

            return resultado(
                linha,
                TipoEvidenciaOferta.CONTEXTO_OFERTA,
                0.96
            )
        }

        /*
         * =====================================================
         * CONTEXTO EXPLÍCITO DA OFERTA
         * =====================================================
         */
        if (
            pareceContextoOferta(
                texto
            )
        ) {

            return resultado(
                linha,
                TipoEvidenciaOferta.CONTEXTO_OFERTA,
                0.92
            )
        }

        /*
         * =====================================================
         * RUÍDO / CONTROLES DE INTERFACE
         * =====================================================
         */
        if (
            pareceRuidoInterface(
                texto
            )
        ) {

            return resultado(
                linha,
                TipoEvidenciaOferta.DESCONHECIDA,
                0.10
            )
        }

        /*
         * =====================================================
         * CONTEXTO DE MAPA
         * =====================================================
         */
        if (
            pareceTextoMapa(
                texto
            )
        ) {

            return resultado(
                linha,
                TipoEvidenciaOferta.CONTEXTO_MAPA,
                0.65
            )
        }

        /*
         * =====================================================
         * NOME DE LOCAL
         * =====================================================
         */
        if (
            pareceNomeLocal(
                texto
            )
        ) {

            return resultado(
                linha,
                TipoEvidenciaOferta.NOME_LOCAL,
                0.68
            )
        }

        /*
         * =====================================================
         * CONTEXTO GENÉRICO
         * =====================================================
         */
        if (
            texto.length >= 8
        ) {

            return resultado(
                linha,
                TipoEvidenciaOferta.CONTEXTO_OFERTA,
                0.45
            )
        }

        return resultado(
            linha,
            TipoEvidenciaOferta.DESCONHECIDA,
            0.20
        )
    }

    private fun resultado(
        linha: LinhaOcr,
        tipo: TipoEvidenciaOferta,
        confianca: Double
    ): EvidenciaClassificadaOferta {

        return EvidenciaClassificadaOferta(
            linha = linha,
            tipo = tipo,
            confianca = confianca
        )
    }

    /*
     * =========================================================
     * RÓTULO DE PARADA
     * =========================================================
     */

    private fun ehRotuloParada(
        texto: String
    ): Boolean {

        val normalizado =
            limparTexto(
                texto.lowercase()
            )

        if (
            normalizado == "coleta" ||
            normalizado == "entrega"
        ) {
            return true
        }

        if (
            Regex(
                """^(coleta|entrega)\s+\d+$""",
                RegexOption.IGNORE_CASE
            ).matches(
                normalizado
            )
        ) {
            return true
        }

        if (
            Regex(
                """^\d+\s+(coleta|entrega)$""",
                RegexOption.IGNORE_CASE
            ).matches(
                normalizado
            )
        ) {
            return true
        }

        /*
         * Tolerância a pequeno erro OCR:
         *
         * Y Coleta
         * I Coleta
         * ? Entrega
         */
        if (
            Regex(
                """^\S{1,2}\s+(coleta|entrega)$""",
                RegexOption.IGNORE_CASE
            ).matches(
                normalizado
            )
        ) {
            return true
        }

        if (
            Regex(
                """^(coleta|entrega)\s+\S{1,2}$""",
                RegexOption.IGNORE_CASE
            ).matches(
                normalizado
            )
        ) {
            return true
        }

        return false
    }

    /*
     * =========================================================
     * ENDEREÇO
     * =========================================================
     */

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

    /*
     * =========================================================
     * TEMPO
     * =========================================================
     */

    private fun pareceTempo(
        texto: String
    ): Boolean {

        val possuiHorario =
            Regex(
                """\b\d{1,2}:\d{2}\b"""
            ).containsMatchIn(
                texto
            )

        val possuiMinutos =
            Regex(
                """\b\d+\s*min\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(
                texto
            )

        val possuiSegundos =
            Regex(
                """\b\d+\s*s\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(
                texto
            )

        val possuiExpressaoTemporal =
            Regex(
                """\b(até|ate|tempo|aproximado)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(
                texto
            )

        return possuiHorario ||
                possuiMinutos ||
                possuiSegundos ||
                possuiExpressaoTemporal
    }

    /*
     * =========================================================
     * COMPLEMENTO DE LOCAL
     * =========================================================
     */

    private fun pareceComplementoLocal(
        texto: String
    ): Boolean {

        if (
            texto.length < 6 ||
            texto.length > 120
        ) {
            return false
        }

        val possuiEstado =
            Regex(
                """\b[A-Z]{2}\b"""
            ).containsMatchIn(
                texto
            )

        val possuiSeparador =
            texto.contains(",") ||
                    texto.contains(" - ")

        val temPalavras =
            texto
                .split(
                    Regex("\\s+")
                )
                .count {
                    it.length >= 3
                } >= 2

        return temPalavras &&
                (
                        possuiEstado ||
                                possuiSeparador
                        )
    }

    /*
     * =========================================================
     * QUANTIDADE DE PEDIDOS / PARADAS
     * =========================================================
     */

    private fun pareceContextoQuantidadePedidos(
        texto: String
    ): Boolean {

        val normalizado =
            limparTexto(
                texto.lowercase()
            )

        if (
            Regex(
                """^\d+\s+pedidos?\s+(?:para\s+)?(coletar|entregar)$""",
                RegexOption.IGNORE_CASE
            ).matches(
                normalizado
            )
        ) {
            return true
        }

        if (
            Regex(
                """^\d+\s+(coletas|entregas)$""",
                RegexOption.IGNORE_CASE
            ).matches(
                normalizado
            )
        ) {
            return true
        }

        if (
            Regex(
                """^pedidos?\s+(?:para\s+)?(coletar|entregar)$""",
                RegexOption.IGNORE_CASE
            ).matches(
                normalizado
            )
        ) {
            return true
        }

        val possuiPedido =
            Regex(
                """\bpedidos?\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(
                normalizado
            )

        val possuiAcaoParada =
            Regex(
                """\b(coletar|entregar)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(
                normalizado
            )

        return possuiPedido &&
                possuiAcaoParada
    }

    /*
     * =========================================================
     * CONTEXTO DA OFERTA
     * =========================================================
     */

    private fun pareceContextoOferta(
        texto: String
    ): Boolean {

        val expressoes =
            listOf(
                "entrega food",
                "entrega de comida",
                "ganhos nessa entrega",
                "ganho nessa entrega",
                "novo pedido",
                "pedido aguardando",
                "pedido disponível",
                "pedido disponivel",
                "distância total",
                "distancia total",
                "rota para moto",
                "possibilidade de devolução",
                "possibilidade de devolucao"
            )

        return expressoes.any { expressao ->

            texto.contains(
                expressao,
                ignoreCase = true
            )
        }
    }

    /*
     * =========================================================
     * RUÍDO / INTERFACE
     * =========================================================
     */

    private fun pareceRuidoInterface(
        texto: String
    ): Boolean {

        val normalizado =
            limparTexto(
                texto.lowercase()
            )

        val termosExatos =
            setOf(
                "google lens",
                "editar",
                "excluir",
                "mais",
                "visualizar",
                "favorito",
                "compartilhar"
            )

        if (
            normalizado in termosExatos
        ) {
            return true
        }

        val expressoes =
            listOf(
                "compartilhar favorito",
                "adicionar aos favoritos",
                "remover dos favoritos"
            )

        return expressoes.any { expressao ->
            normalizado.contains(
                expressao
            )
        }
    }

    /*
     * =========================================================
     * MAPA
     * =========================================================
     */

    private fun pareceTextoMapa(
        texto: String
    ): Boolean {

        if (
            texto.length !in 4..22
        ) {
            return false
        }

        if (
            texto.any {
                it.isDigit()
            }
        ) {
            return false
        }

        if (
            texto.contains("-") ||
            texto.contains(",")
        ) {
            return false
        }

        val letras =
            texto.filter {
                it.isLetter()
            }

        if (
            letras.length < 4
        ) {
            return false
        }

        val maiusculas =
            letras.count {
                it.isUpperCase()
            }

        val proporcaoMaiusculas =
            maiusculas.toDouble() /
                    letras.length.toDouble()

        return proporcaoMaiusculas >= 0.80
    }

    /*
     * =========================================================
     * NOME LOCAL
     * =========================================================
     */

    private fun pareceNomeLocal(
        texto: String
    ): Boolean {

        if (
            texto.length !in 4..80
        ) {
            return false
        }

        /*
         * =====================================================
         * NOVO:
         * CEP, código e texto majoritariamente numérico
         * NÃO podem virar nome de estabelecimento.
         *
         * Exemplos:
         *
         * 06454-913
         * 06023-000
         * 12345
         * =====================================================
         */

        val quantidadeLetras =
            texto.count {
                it.isLetter()
            }

        val quantidadeDigitos =
            texto.count {
                it.isDigit()
            }

        if (
            quantidadeDigitos >= 5 &&
            quantidadeLetras <= 2
        ) {
            return false
        }

        if (
            Regex(
                """^\d{5}-?\d{3}$"""
            ).matches(
                texto.trim()
            )
        ) {
            return false
        }

        /*
         * Também rejeitamos textos sem nenhuma letra,
         * mesmo que tenham símbolos.
         */
        if (
            quantidadeLetras == 0
        ) {
            return false
        }

        if (
            Regex(
                """R\$|\bkm\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(
                texto
            )
        ) {
            return false
        }

        if (
            ehRotuloParada(
                texto
            ) ||
            pareceEndereco(
                texto
            ) ||
            pareceTempo(
                texto
            ) ||
            pareceContextoQuantidadePedidos(
                texto
            ) ||
            pareceContextoOferta(
                texto
            ) ||
            pareceRuidoInterface(
                texto
            )
        ) {
            return false
        }

        val palavras =
            texto
                .split(
                    Regex("\\s+")
                )
                .filter {
                    it.length >= 2
                }

        if (
            palavras.isEmpty()
        ) {
            return false
        }

        return palavras.size <= 7
    }

    /*
     * =========================================================
     * TEXTO
     * =========================================================
     */

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
}