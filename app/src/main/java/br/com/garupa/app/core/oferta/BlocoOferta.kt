package br.com.garupa.app.core.oferta

data class EvidenciaBlocoOferta(

    /*
     * Texto reconhecido pelo OCR.
     */
    val texto: String,

    /*
     * Posição original na tela.
     */
    val x: Int,
    val y: Int,

    val largura: Int,
    val altura: Int,

    /*
     * Indica se esta linha já parece
     * um endereço.
     */
    val pareceEndereco: Boolean = false,

    /*
     * Indica se o texto possui algum
     * rótulo explícito de parada.
     *
     * Exemplo:
     *
     * Coleta 1
     * Entrega 1
     */
    val tipoParadaSugerido: TipoParadaOferta =
        TipoParadaOferta.DESCONHECIDA,

    /*
     * Ordem explícita, quando existir.
     *
     * Coleta 1 -> 1
     * Entrega 2 -> 2
     */
    val ordemSugerida: Int? = null
)

data class BlocoOferta(

    /*
     * Todas as evidências OCR agrupadas
     * dentro deste bloco.
     *
     * Um bloco pode conter, por exemplo:
     *
     * Hino'motto Sushi Delivery!
     * Av. Dr. Carlos...
     * Vila Campesina...
     *
     * sem que ainda tenhamos decidido
     * exatamente o significado de cada linha.
     */
    val evidencias: MutableList<EvidenciaBlocoOferta> =
        mutableListOf(),

    /*
     * Limites aproximados do bloco
     * dentro da tela.
     */
    var xMin: Int = Int.MAX_VALUE,
    var yMin: Int = Int.MAX_VALUE,
    var xMax: Int = Int.MIN_VALUE,
    var yMax: Int = Int.MIN_VALUE,

    /*
     * Resultado interpretado futuramente.
     *
     * Nesta fase permanecem neutros.
     */
    var tipoSugerido: TipoParadaOferta =
        TipoParadaOferta.DESCONHECIDA,

    var nomeSugerido: String? = null,

    var enderecoSugerido: String? = null,

    var ordemSugerida: Int? = null,

    /*
     * Confiança da interpretação do bloco.
     *
     * Por enquanto começa em zero.
     * Será calculada em outra camada.
     */
    var confianca: Double = 0.0
) {

    fun adicionar(
        evidencia: EvidenciaBlocoOferta
    ) {

        evidencias.add(
            evidencia
        )

        xMin =
            minOf(
                xMin,
                evidencia.x
            )

        yMin =
            minOf(
                yMin,
                evidencia.y
            )

        xMax =
            maxOf(
                xMax,
                evidencia.x +
                        evidencia.largura
            )

        yMax =
            maxOf(
                yMax,
                evidencia.y +
                        evidencia.altura
            )
    }

    fun centroX(): Int {

        if (
            xMin == Int.MAX_VALUE ||
            xMax == Int.MIN_VALUE
        ) {
            return 0
        }

        return (
                xMin +
                        xMax
                ) / 2
    }

    fun centroY(): Int {

        if (
            yMin == Int.MAX_VALUE ||
            yMax == Int.MIN_VALUE
        ) {
            return 0
        }

        return (
                yMin +
                        yMax
                ) / 2
    }

    fun alturaBloco(): Int {

        if (
            yMin == Int.MAX_VALUE ||
            yMax == Int.MIN_VALUE
        ) {
            return 0
        }

        return yMax -
                yMin
    }

    fun larguraBloco(): Int {

        if (
            xMin == Int.MAX_VALUE ||
            xMax == Int.MIN_VALUE
        ) {
            return 0
        }

        return xMax -
                xMin
    }

    fun textoCompleto(): String {

        return evidencias
            .sortedBy {
                it.y
            }
            .joinToString(
                " "
            ) {
                it.texto.trim()
            }
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }
}