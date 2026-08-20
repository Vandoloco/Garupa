package br.com.garupa.app.core.monitoramento

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MonitorGarupa(
    private val contexto: Context
) {

    private var arquivoSessao: File? =
        null

    private val formatoNomeArquivo =
        SimpleDateFormat(
            "yyyy-MM-dd_HH-mm-ss",
            Locale.getDefault()
        )

    fun iniciarSessao() {

        val pasta =
            File(
                contexto.filesDir,
                "monitoramento"
            )

        if (!pasta.exists()) {
            pasta.mkdirs()
        }

        val nomeArquivo =
            "garupa_sessao_" +
                    formatoNomeArquivo.format(
                        Date()
                    ) +
                    ".log"

        arquivoSessao =
            File(
                pasta,
                nomeArquivo
            )

        registrar(
            nivel =
                NivelRegistroGarupa.INFO,

            categoria =
                "SESSAO",

            mensagem =
                "Sessão de monitoramento iniciada"
        )
    }

    fun registrar(
        nivel: NivelRegistroGarupa,
        categoria: String,
        mensagem: String
    ) {

        val evento =
            EventoRegistroGarupa(
                nivel =
                    nivel,

                categoria =
                    categoria,

                mensagem =
                    mensagem
            )

        val linha =
            evento.formatar()

        Log.d(
            "GARUPA_MONITOR",
            linha
        )

        try {

            arquivoSessao
                ?.appendText(
                    linha + "\n"
                )

        } catch (
            erro: Exception
        ) {

            Log.e(
                "GARUPA_MONITOR",
                "Erro ao gravar registro",
                erro
            )
        }
    }

    fun encerrarSessao() {

        registrar(
            nivel =
                NivelRegistroGarupa.INFO,

            categoria =
                "SESSAO",

            mensagem =
                "Sessão de monitoramento encerrada"
        )

        arquivoSessao =
            null
    }

    fun obterArquivoSessao():
            File? {

        return arquivoSessao
    }
}