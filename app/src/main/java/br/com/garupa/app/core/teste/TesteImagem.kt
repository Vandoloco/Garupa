package br.com.garupa.app.core.teste

import android.content.Context
import android.net.Uri
import android.util.Log
import br.com.garupa.app.core.leitura.LeitorTela
import br.com.garupa.app.core.leitura.OrigemLeitura
import java.io.File
import java.io.FileOutputStream

class TesteImagem(
    private val contexto: Context
) {

    private val leitorTela =
        LeitorTela(contexto)

    fun analisarImagem(uri: Uri) {

        try {

            /*
             * Cada print selecionada recebe um arquivo próprio.
             * Isso evita sobrescrever uma imagem enquanto o OCR
             * anterior ainda está processando.
             */
            val arquivoTeste =
                File(
                    contexto.cacheDir,
                    "garupa_teste_${System.nanoTime()}.png"
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
                "🧪 Nova print carregada | " +
                        "arquivo=${arquivoTeste.name}"
            )

            leitorTela.lerImagem(
                caminhoImagem = arquivoTeste.absolutePath,
                origem = OrigemLeitura.TESTE
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