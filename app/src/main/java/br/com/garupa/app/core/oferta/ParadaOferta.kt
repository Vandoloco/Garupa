package br.com.garupa.app.core.oferta

enum class TipoParadaOferta {
    COLETA,
    ENTREGA,
    DESCONHECIDA
}

enum class FonteEvidenciaParada {
    TEXTO,
    ROTULO,
    COR,
    POSICAO,
    MAPA,
    PARSER_ESPECIFICO
}

data class ParadaOferta(

    /*
     * Tipo lógico da parada.
     *
     * Exemplos:
     *
     * COLETA  -> restaurante / loja
     * ENTREGA -> cliente
     */
    val tipo: TipoParadaOferta =
        TipoParadaOferta.DESCONHECIDA,

    /*
     * Nome visível da parada.
     *
     * Exemplos:
     *
     * "Barceloneta"
     * "Piola - Alphaville"
     */
    val nome: String? = null,

    /*
     * Endereço completo, quando disponível.
     *
     * Nem toda plataforma mostra isso
     * logo na primeira tela.
     */
    val endereco: String? = null,

    /*
     * Ordem apresentada na oferta.
     *
     * Exemplos:
     *
     * Coleta 1
     * Entrega 1
     * Entrega 2
     */
    val ordem: Int? = null,

    /*
     * Coordenadas visuais aproximadas
     * da evidência principal na tela.
     *
     * Isso será útil para associar texto
     * com marcador/colorização.
     */
    val x: Int? = null,
    val y: Int? = null,

    /*
     * Evidências usadas para classificar
     * esta parada.
     *
     * Exemplo:
     *
     * ROTULO + TEXTO
     * COR + POSICAO
     */
    val evidencias: Set<FonteEvidenciaParada> =
        emptySet(),

    /*
     * Confiança entre 0.0 e 1.0.
     *
     * 1.0 = praticamente explícito.
     *
     * Exemplos:
     *
     * "Coleta 1" escrito na tela:
     * confiança alta.
     *
     * Inferido apenas pela posição:
     * confiança menor.
     */
    val confianca: Double = 0.0
)