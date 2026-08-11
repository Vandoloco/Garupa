package br.com.garupa.app.core.teste

import android.content.Context
import android.net.Uri
import android.util.Log
import br.com.garupa.app.core.leitura.LeitorTela
import java.io.File
import java.io.FileOutputStream

class TesteImagem(
    private val contexto: Context
) {

    private val leitorTela =
        LeitorTela(contexto)

    fun analisarImagem(uri: Uri) {

        try {

            val arquivoTeste =
                File(
                    contexto.cacheDir,
                    "garupa_teste.png"
                )

            contexto.contentResolver
                .openInputStream(uri)
                ?.use { entrada ->

                    FileOutputStream(arquivoTeste)
                        .use { saida ->

                            entrada.copyTo(saida)
                        }
                }
                ?: run {

                    Log.e(
                        "GARUPA_TESTE",
                        "🧪 Não foi possível abrir a imagem"
                    )

                    return
                }

            Log.d(
                "GARUPA_TESTE",
                "🧪 Print carregada: ${arquivoTeste.absolutePath}"
            )

            leitorTela.lerImagem(
                arquivoTeste.absolutePath
            )

        } catch (erro: Exception) {

            Log.e(
                "GARUPA_TESTE",
                "🧪 Erro ao processar print de teste",
                erro
            )
        }
    }
}