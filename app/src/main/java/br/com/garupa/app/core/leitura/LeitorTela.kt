package br.com.garupa.app.core.leitura

import android.content.Context
import android.net.Uri
import android.util.Log
import br.com.garupa.app.core.parser.ParserKeeta
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

class LeitorTela(
    private val contexto: Context
) {

    private val reconhecedor =
        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

    private val parserKeeta =
        ParserKeeta()

    fun lerImagem(caminhoImagem: String) {

        val arquivo = File(caminhoImagem)

        if (!arquivo.exists()) {

            Log.d(
                "GARUPA",
                "🔎 Imagem não encontrada"
            )

            return
        }

        try {

            val uri = Uri.fromFile(arquivo)

            val imagem =
                InputImage.fromFilePath(
                    contexto,
                    uri
                )

            reconhecedor
                .process(imagem)
                .addOnSuccessListener { resultado ->

                    if (resultado.text.isBlank()) {

                        Log.d(
                            "GARUPA_OCR",
                            "👁️ Nenhum texto reconhecido"
                        )

                        return@addOnSuccessListener
                    }

                    val linhasOcr =
                        resultado.textBlocks
                            .flatMap { bloco ->
                                bloco.lines
                            }
                            .mapNotNull { linha ->

                                val caixa =
                                    linha.boundingBox
                                        ?: return@mapNotNull null

                                LinhaOcr(
                                    texto = linha.text.trim(),
                                    x = caixa.left,
                                    y = caixa.top,
                                    largura = caixa.width(),
                                    altura = caixa.height()
                                )
                            }
                            .sortedBy { linha ->
                                linha.y
                            }

                    registrarPosicoes(
                        linhasOcr
                    )

                    parserKeeta.analisar(
                        linhasOcr
                    )
                }
                .addOnFailureListener { erro ->

                    Log.e(
                        "GARUPA_OCR",
                        "👁️ Erro no reconhecimento de texto",
                        erro
                    )
                }

        } catch (erro: Exception) {

            Log.e(
                "GARUPA_OCR",
                "👁️ Erro ao preparar imagem",
                erro
            )
        }
    }

    private fun registrarPosicoes(
        linhas: List<LinhaOcr>
    ) {

        val texto =
            linhas.joinToString("\n") { linha ->

                "x=${linha.x} " +
                        "y=${linha.y} " +
                        "w=${linha.largura} " +
                        "h=${linha.altura} " +
                        "| ${linha.texto}"
            }

        Log.d(
            "GARUPA_OCR_POSICAO",
            "\n$texto"
        )
    }
}