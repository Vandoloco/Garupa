package br.com.garupa.app.core.monitoramento

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NivelRegistroGarupa {
    INFO,
    AVISO,
    ERRO,
    CRITICO
}

data class EventoRegistroGarupa(
    val horario: Long = System.currentTimeMillis(),
    val nivel: NivelRegistroGarupa,
    val categoria: String,
    val mensagem: String
) {

    fun formatar(): String {

        val formatoHorario =
            SimpleDateFormat(
                "HH:mm:ss.SSS",
                Locale.getDefault()
            )

        val horarioFormatado =
            formatoHorario.format(
                Date(horario)
            )

        return "$horarioFormatado | " +
                "${nivel.name} | " +
                "$categoria | " +
                mensagem
    }
}