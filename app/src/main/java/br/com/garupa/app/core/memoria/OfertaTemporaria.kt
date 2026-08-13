package br.com.garupa.app.core.memoria

import br.com.garupa.app.core.parser.ParadaKeeta

data class OfertaTemporaria(

    var valor: Double? = null,

    var distanciaTotal: Double? = null,

    /*
     * CAMPOS ANTIGOS
     *
     * Continuam existindo para manter
     * compatibilidade com o fluxo atual.
     */
    var nomeColeta: String? = null,

    var enderecoColeta: String? = null,

    var enderecoEntrega: String? = null,

    /*
     * NOVO
     *
     * Guarda todas as paradas reconhecidas
     * durante os diferentes frames da oferta.
     *
     * Pedido simples:
     * COLETA + ENTREGA
     *
     * Pedido agrupado:
     * COLETA + ENTREGA + ENTREGA...
     */
    var paradas: MutableList<ParadaKeeta> =
        mutableListOf(),

    /*
     * Quantidade informada pelo próprio Keeta.
     *
     * Exemplo:
     * "2 pedidos para coletar"
     */
    var quantidadePedidos: Int? = null,

    var completa: Boolean = false,

    /*
     * Identifica a mesma oferta entre
     * diferentes frames/telas do Keeta.
     */
    var assinaturaBase: String? = null,

    /*
     * Evita calcular a mesma oferta
     * mais de uma vez.
     */
    var analisada: Boolean = false
)