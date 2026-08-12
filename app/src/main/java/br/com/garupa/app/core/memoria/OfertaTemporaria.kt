package br.com.garupa.app.core.memoria

data class OfertaTemporaria(

    var valor: Double? = null,

    var distanciaTotal: Double? = null,

    var nomeColeta: String? = null,

    var enderecoColeta: String? = null,

    var enderecoEntrega: String? = null,

    var completa: Boolean = false,

    /*
     * Usamos esta assinatura para identificar
     * a mesma oferta entre a tela inicial
     * e a tela subida do Keeta.
     */
    var assinaturaBase: String? = null,

    /*
     * Evita calcular a mesma oferta
     * mais de uma vez.
     */
    var analisada: Boolean = false
)