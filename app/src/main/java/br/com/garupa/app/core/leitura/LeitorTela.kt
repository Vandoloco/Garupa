package br.com.garupa.app.core.leitura

import android.content.Context
import android.net.Uri
import android.util.Log
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

                    val textoReconhecido =
                        resultado.text.trim()

                    if (textoReconhecido.isEmpty()) {

                        Log.d(
                            "GARUPA_OCR",
                            "👁️ Nenhum texto reconhecido"
                        )

                        return@addOnSuccessListener
                    }

                    Log.d(
                        "GARUPA_OCR",
                        "👁️ Texto reconhecido:\n$textoReconhecido"
                    )

                    analisarTexto(
                        textoReconhecido
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

    private fun analisarTexto(
        texto: String
    ) {

        val regexValor =
            Regex(
                """R\$\s*(\d+[.,]\d{2})"""
            )

        val regexDistancia =
            Regex(
                """(\d+[.,]\d+)\s*km"""
            )

        val valorEncontrado =
            regexValor
                .find(texto)
                ?.groupValues
                ?.get(1)
                ?.replace(",", ".")
                ?.toDoubleOrNull()

        val distanciaEncontrada =
            regexDistancia
                .find(texto)
                ?.groupValues
                ?.get(1)
                ?.replace(",", ".")
                ?.toDoubleOrNull()

        if (
            valorEncontrado != null &&
            distanciaEncontrada != null
        ) {

            Log.d(
                "GARUPA_PEDIDO",
                "📦 Oferta detectada | " +
                        "Valor: R$ %.2f | ".format(valorEncontrado) +
                        "Distância: %.1f km".format(distanciaEncontrada)
            )

        } else {

            Log.d(
                "GARUPA_PEDIDO",
                "📦 Nenhuma oferta completa detectada"
            )
        }
    }
}