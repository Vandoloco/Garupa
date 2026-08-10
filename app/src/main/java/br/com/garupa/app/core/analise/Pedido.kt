package br.com.garupa.app.core.analise

data class Pedido(
    val valorBase: Double,
    val taxaExtra: Double,
    val distanciaAteRetirada: Double,
    val distanciaRetiradaAteEntrega: Double
)