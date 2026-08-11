package br.com.garupa.app.core.memoria

data class OfertaTemporaria(
    var valor: Double? = null,
    var distanciaTotal: Double? = null,

    var nomeColeta: String? = null,
    var enderecoColeta: String? = null,

    var enderecoEntrega: String? = null,

    var completa: Boolean = false
)