package br.com.garupa.app.core.modelos

data class Corrida(
    val valor: Double,
    val distanciaAteColeta: Double,
    val distanciaAteEntrega: Double,
    val aplicativo: String
)