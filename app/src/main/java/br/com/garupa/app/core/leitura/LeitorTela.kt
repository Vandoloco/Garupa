package br.com.garupa.app.core.leitura

import android.content.Context
import android.net.Uri
import android.util.Log
import br.com.garupa.app.core.geocodificacao.CoordenadaEndereco
import br.com.garupa.app.core.geocodificacao.GeocodificadorEndereco
import br.com.garupa.app.core.localizacao.GerenciadorLocalizacao
import br.com.garupa.app.core.parser.ParserKeeta
import br.com.garupa.app.core.rota.CalculadorRota
import br.com.garupa.app.core.rota.CoordenadaRota
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

    private val geocodificador =
        GeocodificadorEndereco(contexto)

    private val gerenciadorLocalizacao =
        GerenciadorLocalizacao(contexto)

    private val calculadorRota =
        CalculadorRota()

    private var enderecoBAtual: String? = null
    private var enderecoCAtual: String? = null

    private var coordenadaB: CoordenadaEndereco? = null
    private var coordenadaC: CoordenadaEndereco? = null

    private var ultimaRotaCalculada: String? = null

    fun lerImagem(
        caminhoImagem: String
    ) {

        val arquivo =
            File(caminhoImagem)

        if (!arquivo.exists()) {

            Log.d(
                "GARUPA",
                "🔎 Imagem não encontrada"
            )

            return
        }

        try {

            val uri =
                Uri.fromFile(
                    arquivo
                )

            val imagem =
                InputImage.fromFilePath(
                    contexto,
                    uri
                )

            reconhecedor
                .process(imagem)
                .addOnSuccessListener { resultado ->

                    if (
                        resultado.text.isBlank()
                    ) {

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
                                    texto =
                                        linha.text.trim(),

                                    x =
                                        caixa.left,

                                    y =
                                        caixa.top,

                                    largura =
                                        caixa.width(),

                                    altura =
                                        caixa.height()
                                )
                            }
                            .sortedBy { linha ->
                                linha.y
                            }

                    registrarPosicoes(
                        linhasOcr
                    )

                    val resultadoKeeta =
                        parserKeeta.analisar(
                            linhasOcr
                        )

                    processarEnderecos(
                        enderecoB =
                            resultadoKeeta.enderecoColeta,

                        enderecoC =
                            resultadoKeeta.enderecoEntrega
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

    private fun processarEnderecos(
        enderecoB: String?,
        enderecoC: String?
    ) {

        if (
            !enderecoB.isNullOrBlank() &&
            enderecoB != enderecoBAtual
        ) {

            enderecoBAtual =
                enderecoB

            coordenadaB =
                null

            ultimaRotaCalculada =
                null

            geocodificador.buscar(
                enderecoB
            ) { coordenada ->

                if (coordenada != null) {

                    coordenadaB =
                        coordenada

                    Log.d(
                        "GARUPA_COORD_B",
                        "📍 B = " +
                                "${coordenada.latitude}, " +
                                "${coordenada.longitude}"
                    )

                    tentarCalcularRotaCompleta()

                } else {

                    Log.d(
                        "GARUPA_COORD_B",
                        "❌ Não foi possível localizar B"
                    )
                }
            }
        }

        if (
            !enderecoC.isNullOrBlank() &&
            enderecoC != enderecoCAtual
        ) {

            enderecoCAtual =
                enderecoC

            coordenadaC =
                null

            ultimaRotaCalculada =
                null

            geocodificador.buscar(
                enderecoC
            ) { coordenada ->

                if (coordenada != null) {

                    coordenadaC =
                        coordenada

                    Log.d(
                        "GARUPA_COORD_C",
                        "🏠 C = " +
                                "${coordenada.latitude}, " +
                                "${coordenada.longitude}"
                    )

                    tentarCalcularRotaCompleta()

                } else {

                    Log.d(
                        "GARUPA_COORD_C",
                        "❌ Não foi possível localizar C"
                    )
                }
            }
        }
    }

    private fun tentarCalcularRotaCompleta() {

        val pontoB =
            coordenadaB
                ?: return

        val pontoC =
            coordenadaC
                ?: return

        val chaveRota =
            "${pontoB.latitude}," +
                    "${pontoB.longitude}|" +
                    "${pontoC.latitude}," +
                    "${pontoC.longitude}"

        if (
            chaveRota ==
            ultimaRotaCalculada
        ) {

            return
        }

        ultimaRotaCalculada =
            chaveRota

        Log.d(
            "GARUPA_ROTA",
            "🧭 B e C prontos. Buscando ponto A..."
        )

        gerenciadorLocalizacao
            .obterUltimaLocalizacao { localizacaoA ->

                if (localizacaoA == null) {

                    Log.d(
                        "GARUPA_ROTA",
                        "❌ Ponto A indisponível"
                    )

                    ultimaRotaCalculada =
                        null

                    return@obterUltimaLocalizacao
                }

                val pontoA =
                    CoordenadaRota(
                        latitude =
                            localizacaoA.latitude,

                        longitude =
                            localizacaoA.longitude
                    )

                val pontoBRota =
                    CoordenadaRota(
                        latitude =
                            pontoB.latitude,

                        longitude =
                            pontoB.longitude
                    )

                val pontoCRota =
                    CoordenadaRota(
                        latitude =
                            pontoC.latitude,

                        longitude =
                            pontoC.longitude
                    )

                Log.d(
                    "GARUPA_ROTA",
                    "📍 A = " +
                            "${localizacaoA.latitude}, " +
                            "${localizacaoA.longitude}"
                )

                calculadorRota.calcularABC(
                    pontoA =
                        pontoA,

                    pontoB =
                        pontoBRota,

                    pontoC =
                        pontoCRota
                ) { resultado ->

                    if (resultado != null) {

                        Log.d(
                            "GARUPA_ROTA_FINAL",
                            "✅ A → B = " +
                                    "%.2f km | ".format(
                                        resultado.distanciaABKm
                                    ) +
                                    "B → C = " +
                                    "%.2f km | ".format(
                                        resultado.distanciaBCKm
                                    ) +
                                    "TOTAL = " +
                                    "%.2f km".format(
                                        resultado.distanciaTotalKm
                                    )
                        )

                    } else {

                        Log.d(
                            "GARUPA_ROTA_FINAL",
                            "❌ Não foi possível calcular A → B → C"
                        )
                    }
                }
            }
    }

    private fun registrarPosicoes(
        linhas: List<LinhaOcr>
    ) {

        val texto =
            linhas.joinToString(
                "\n"
            ) { linha ->

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