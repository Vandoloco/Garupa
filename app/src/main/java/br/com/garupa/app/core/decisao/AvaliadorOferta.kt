package br.com.garupa.app.core.decisao

import android.util.Log

data class ResultadoAvaliacao(
    val valorOferta: Double,
    val distanciaTotalKm: Double,
    val valorPorKm: Double,
    val sugestao: String
)

class AvaliadorOferta {

    companion object {
        const val VALOR_MINIMO_POR_KM = 1.60
    }

    fun avaliar(
        valorOferta: Double,
        distanciaTotalKm: Double
    ): ResultadoAvaliacao {

        val valorPorKm =
            if (distanciaTotalKm > 0.0) {
                valorOferta / distanciaTotalKm
            } else {
                0.0
            }

        val sugestao =
            if (valorPorKm >= VALOR_MINIMO_POR_KM) {
                "Sugiro aceitar."
            } else {
                "Sugiro deixar passar."
            }

        val resultado =
            ResultadoAvaliacao(
                valorOferta = valorOferta,
                distanciaTotalKm = distanciaTotalKm,
                valorPorKm = valorPorKm,
                sugestao = sugestao
            )

        Log.d(
            "GARUPA_DECISAO",
            "💰 Valor: R$ %.2f | ".format(resultado.valorOferta) +
                    "Distância real: %.2f km | ".format(resultado.distanciaTotalKm) +
                    "R$/km: %.2f | ".format(resultado.valorPorKm) +
                    resultado.sugestao
        )

        return resultado
    }
}