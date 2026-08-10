package br.com.garupa.app.core.olhos

import android.util.Log
import br.com.garupa.app.core.analise.Pedido

class Olhos {

    fun iniciar() {
        Log.d("GARUPA", "👀 Olhos prontos")
    }

    fun criarPedido(
        valorBase: Double,
        distanciaAteRetirada: Double,
        distanciaRetiradaAteEntrega: Double
    ): Pedido {

        return Pedido(
            valorBase = valorBase,
            taxaExtra = 0.0,
            distanciaAteRetirada = distanciaAteRetirada,
            distanciaRetiradaAteEntrega = distanciaRetiradaAteEntrega
        )
    }
}