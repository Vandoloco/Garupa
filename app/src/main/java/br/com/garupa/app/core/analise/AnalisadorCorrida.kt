package br.com.garupa.app.core.analise

class AnalisadorCorrida {

    fun analisar(pedido: Pedido): String {

        val valorTotal = pedido.valorBase + pedido.taxaExtra

        val distanciaTotal =
            pedido.distanciaAteRetirada +
                    pedido.distanciaRetiradaAteEntrega

        val valorPorKm = valorTotal / distanciaTotal

        val sugestao = if (valorPorKm >= 1.60) {
            "Sugiro aceitar."
        } else {
            "Sugiro deixar passar."
        }

        return "Valor total: R$ $valorTotal | Distância total: $distanciaTotal km | Valor por km: R$ %.2f | $sugestao"
            .format(valorPorKm)
    }

}