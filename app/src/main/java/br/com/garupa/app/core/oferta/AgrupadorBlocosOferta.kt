package br.com.garupa.app.core.oferta

import android.util.Log
import br.com.garupa.app.core.leitura.LinhaOcr
import kotlin.math.abs

class AgrupadorBlocosOferta {

    private val classificadorEvidencia =
        ClassificadorEvidenciaOferta()

    fun agrupar(
        linhas: List<LinhaOcr>
    ): List<BlocoOferta> {

        val relevantes =
            linhas
                .filter {
                    it.texto.isNotBlank()
                }
                .sortedBy {
                    it.y
                }

        /*
         * Classificação semântica das linhas.
         *
         * Por enquanto isso NÃO muda a montagem
         * dos blocos. Serve apenas para diagnóstico.
         */
        val classificadas =
            classificadorEvidencia.classificar(
                relevantes
            )

        val classificacaoPorLinha =
            classificadas.associateBy {
                it.linha
            }

        val blocos =
            mutableListOf<BlocoOferta>()

        relevantes.forEach { linha ->

            val classificacao =
                classificacaoPorLinha[
                    linha
                ]

            val tipoParadaSugerido =
                detectarTipoParada(
                    linha.texto
                )

            val evidencia =
                EvidenciaBlocoOferta(
                    texto =
                        linha.texto.trim(),

                    x =
                        linha.x,

                    y =
                        linha.y,

                    largura =
                        linha.largura,

                    altura =
                        linha.altura,

                    pareceEndereco =
                        classificacao?.tipo ==
                                TipoEvidenciaOferta.ENDERECO,

                    tipoParadaSugerido =
                        tipoParadaSugerido,

                    ordemSugerida =
                        detectarOrdem(
                            linha.texto
                        )
                )

            val blocoCompativel =
                blocos
                    .filter { bloco ->

                        blocoEhCompativel(
                            bloco =
                                bloco,

                            evidencia =
                                evidencia
                        )
                    }
                    .minByOrNull { bloco ->

                        custoAssociacao(
                            bloco =
                                bloco,

                            evidencia =
                                evidencia
                        )
                    }

            if (
                blocoCompativel != null
            ) {

                blocoCompativel.adicionar(
                    evidencia
                )

            } else {

                val novoBloco =
                    BlocoOferta()

                novoBloco.adicionar(
                    evidencia
                )

                blocos.add(
                    novoBloco
                )
            }
        }

        val resultado =
            blocos
                .filter {
                    it.evidencias.isNotEmpty()
                }
                .sortedBy {
                    it.yMin
                }

        resultado
            .forEachIndexed { indice, bloco ->

                Log.d(
                    "GARUPA_BLOCO_OFERTA",
                    "🧩 BLOCO ${indice + 1}/${resultado.size} | " +
                            "x=${bloco.xMin}-${bloco.xMax} | " +
                            "y=${bloco.yMin}-${bloco.yMax} | " +
                            "linhas=${bloco.evidencias.size}"
                )

                /*
                 * Agora mostramos a semântica
                 * de cada evidência dentro do bloco.
                 */
                bloco.evidencias
                    .sortedBy {
                        it.y
                    }
                    .forEach { evidencia ->

                        val linhaOriginal =
                            relevantes.firstOrNull { linha ->

                                linha.x ==
                                        evidencia.x &&
                                        linha.y ==
                                        evidencia.y &&
                                        linha.texto.trim() ==
                                        evidencia.texto.trim()
                            }

                        val classificacao =
                            linhaOriginal
                                ?.let {
                                    classificacaoPorLinha[
                                        it
                                    ]
                                }

                        Log.d(
                            "GARUPA_BLOCO_EVIDENCIA",
                            "   ↳ " +
                                    "tipo=${classificacao?.tipo ?: TipoEvidenciaOferta.DESCONHECIDA} | " +
                                    "conf=${"%.2f".format(classificacao?.confianca ?: 0.0)} | " +
                                    "x=${evidencia.x} | " +
                                    "y=${evidencia.y} | " +
                                    "texto=${evidencia.texto}"
                        )
                    }

                Log.d(
                    "GARUPA_BLOCO_OFERTA",
                    "🧩 TEXTO BLOCO ${indice + 1} | " +
                            bloco.textoCompleto()
                )
            }

        return resultado
    }

    private fun blocoEhCompativel(
        bloco: BlocoOferta,
        evidencia: EvidenciaBlocoOferta
    ): Boolean {

        if (
            bloco.evidencias.isEmpty()
        ) {

            return false
        }

        val ultima =
            bloco.evidencias
                .maxByOrNull {
                    it.y
                }
                ?: return false

        val fimUltima =
            ultima.y +
                    ultima.altura

        val distanciaVertical =
            evidencia.y -
                    fimUltima

        if (
            distanciaVertical <
            -20
        ) {

            return false
        }

        if (
            distanciaVertical >
            90
        ) {

            return false
        }

        val centroEvidencia =
            evidencia.x +
                    evidencia.largura / 2

        val centroBloco =
            bloco.centroX()

        val distanciaHorizontal =
            abs(
                centroEvidencia -
                        centroBloco
            )

        val sobrepoe =
            existeSobreposicaoHorizontal(
                bloco =
                    bloco,

                evidencia =
                    evidencia
            )

        return sobrepoe ||
                distanciaHorizontal <= 170
    }

    private fun existeSobreposicaoHorizontal(
        bloco: BlocoOferta,
        evidencia: EvidenciaBlocoOferta
    ): Boolean {

        val inicioA =
            bloco.xMin

        val fimA =
            bloco.xMax

        val inicioB =
            evidencia.x

        val fimB =
            evidencia.x +
                    evidencia.largura

        return maxOf(
            inicioA,
            inicioB
        ) <
                minOf(
                    fimA,
                    fimB
                )
    }

    private fun custoAssociacao(
        bloco: BlocoOferta,
        evidencia: EvidenciaBlocoOferta
    ): Int {

        val ultima =
            bloco.evidencias
                .maxByOrNull {
                    it.y
                }

        val distanciaY =
            if (
                ultima != null
            ) {

                abs(
                    evidencia.y -
                            (
                                    ultima.y +
                                            ultima.altura
                                    )
                )

            } else {

                Int.MAX_VALUE / 4
            }

        val centroEvidencia =
            evidencia.x +
                    evidencia.largura / 2

        val distanciaX =
            abs(
                centroEvidencia -
                        bloco.centroX()
            )

        val penalidadeSemSobreposicao =
            if (
                existeSobreposicaoHorizontal(
                    bloco,
                    evidencia
                )
            ) {

                0

            } else {

                100
            }

        return distanciaY * 3 +
                distanciaX +
                penalidadeSemSobreposicao
    }

    private fun detectarTipoParada(
        texto: String
    ): TipoParadaOferta {

        val normalizado =
            texto.lowercase()

        return when {

            Regex(
                """\bcoleta\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(
                normalizado
            ) -> {

                TipoParadaOferta.COLETA
            }

            Regex(
                """\bentrega\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(
                normalizado
            ) -> {

                TipoParadaOferta.ENTREGA
            }

            else -> {

                TipoParadaOferta.DESCONHECIDA
            }
        }
    }

    private fun detectarOrdem(
        texto: String
    ): Int? {

        val resultado =
            Regex(
                """\b(?:coleta|entrega)\s*(\d+)\b""",
                RegexOption.IGNORE_CASE
            ).find(
                texto
            )

        return resultado
            ?.groupValues
            ?.getOrNull(
                1
            )
            ?.toIntOrNull()
    }
}