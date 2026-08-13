package br.com.garupa.app.core.rota

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CoordenadaRota(
    val latitude: Double,
    val longitude: Double
)

data class ResultadoRota(
    val distanciaABKm: Double,
    val distanciaBCKm: Double,
    val distanciaTotalKm: Double,
    val tempoABSegundos: Double,
    val tempoBCSegundos: Double
)

/*
 * Resultado genérico para rotas com
 * qualquer quantidade de paradas.
 */
data class TrechoRota(
    val indiceOrigem: Int,
    val indiceDestino: Int,
    val distanciaKm: Double,
    val tempoSegundos: Double
)

data class ResultadoRotaMultipla(
    val trechos: List<TrechoRota>,
    val distanciaTotalKm: Double,
    val tempoTotalSegundos: Double
)

class CalculadorRota {

    /*
     * =========================================================
     * MÉTODO ANTIGO
     *
     * Mantido para não quebrar o fluxo atual:
     *
     * A → B → C
     * =========================================================
     */
    fun calcularABC(
        pontoA: CoordenadaRota,
        pontoB: CoordenadaRota,
        pontoC: CoordenadaRota,
        aoCalcular: (ResultadoRota?) -> Unit
    ) {

        calcularMultiplaParada(
            pontoInicial =
                pontoA,

            paradas =
                listOf(
                    pontoB,
                    pontoC
                )
        ) { resultadoMultiplo ->

            if (
                resultadoMultiplo == null ||
                resultadoMultiplo.trechos.size < 2
            ) {

                Log.e(
                    "GARUPA_ROTA",
                    "❌ Não foi possível calcular A → B → C"
                )

                aoCalcular(
                    null
                )

                return@calcularMultiplaParada
            }

            val trechoAB =
                resultadoMultiplo.trechos[0]

            val trechoBC =
                resultadoMultiplo.trechos[1]

            val resultado =
                ResultadoRota(
                    distanciaABKm =
                        trechoAB.distanciaKm,

                    distanciaBCKm =
                        trechoBC.distanciaKm,

                    distanciaTotalKm =
                        resultadoMultiplo.distanciaTotalKm,

                    tempoABSegundos =
                        trechoAB.tempoSegundos,

                    tempoBCSegundos =
                        trechoBC.tempoSegundos
                )

            Log.d(
                "GARUPA_ROTA",
                "🏍️ A → B = %.2f km".format(
                    resultado.distanciaABKm
                )
            )

            Log.d(
                "GARUPA_ROTA",
                "🏍️ B → C = %.2f km".format(
                    resultado.distanciaBCKm
                )
            )

            Log.d(
                "GARUPA_ROTA",
                "🛣️ Total A → B → C = %.2f km".format(
                    resultado.distanciaTotalKm
                )
            )

            aoCalcular(
                resultado
            )
        }
    }

    /*
     * =========================================================
     * NOVO MÉTODO
     *
     * pontoInicial = posição atual do piloto A
     *
     * paradas =
     * [
     *   coleta,
     *   entrega 1,
     *   entrega 2,
     *   ...
     * ]
     *
     * Exemplo:
     *
     * A → B → C1 → C2
     * =========================================================
     */
    fun calcularMultiplaParada(
        pontoInicial: CoordenadaRota,
        paradas: List<CoordenadaRota>,
        aoCalcular: (ResultadoRotaMultipla?) -> Unit
    ) {

        if (
            paradas.isEmpty()
        ) {

            Log.e(
                "GARUPA_ROTA_MULTI",
                "❌ Nenhuma parada informada"
            )

            aoCalcular(
                null
            )

            return
        }

        Thread {

            try {

                /*
                 * Montamos a sequência completa:
                 *
                 * índice 0 = piloto A
                 * índice 1 = primeira parada
                 * índice 2 = segunda parada
                 * ...
                 */
                val pontos =
                    mutableListOf<CoordenadaRota>()

                pontos.add(
                    pontoInicial
                )

                pontos.addAll(
                    paradas
                )

                val trechos =
                    mutableListOf<TrechoRota>()

                var distanciaTotal =
                    0.0

                var tempoTotal =
                    0.0

                for (
                i in 0 until
                        pontos.size - 1
                ) {

                    val origem =
                        pontos[i]

                    val destino =
                        pontos[i + 1]

                    Log.d(
                        "GARUPA_ROTA_MULTI",
                        "🧭 Calculando trecho ${i + 1}: " +
                                "$i → ${i + 1}"
                    )

                    val trecho =
                        calcularTrecho(
                            origem =
                                origem,

                            destino =
                                destino
                        )

                    if (
                        trecho == null
                    ) {

                        Log.e(
                            "GARUPA_ROTA_MULTI",
                            "❌ Falha no trecho " +
                                    "${i + 1}/${pontos.size - 1}"
                        )

                        aoCalcular(
                            null
                        )

                        return@Thread
                    }

                    val resultadoTrecho =
                        TrechoRota(
                            indiceOrigem =
                                i,

                            indiceDestino =
                                i + 1,

                            distanciaKm =
                                trecho.first,

                            tempoSegundos =
                                trecho.second
                        )

                    trechos.add(
                        resultadoTrecho
                    )

                    distanciaTotal +=
                        trecho.first

                    tempoTotal +=
                        trecho.second

                    Log.d(
                        "GARUPA_ROTA_MULTI",
                        "✅ Trecho ${i + 1} | " +
                                "%.2f km | ".format(
                                    trecho.first
                                ) +
                                "%.0f s".format(
                                    trecho.second
                                )
                    )
                }

                val resultado =
                    ResultadoRotaMultipla(
                        trechos =
                            trechos,

                        distanciaTotalKm =
                            distanciaTotal,

                        tempoTotalSegundos =
                            tempoTotal
                    )

                Log.d(
                    "GARUPA_ROTA_MULTI",
                    "🏁 Rota completa | " +
                            "paradas=${paradas.size} | " +
                            "distância=%.2f km | ".format(
                                resultado.distanciaTotalKm
                            ) +
                            "tempo=%.0f s".format(
                                resultado.tempoTotalSegundos
                            )
                )

                aoCalcular(
                    resultado
                )

            } catch (erro: Exception) {

                Log.e(
                    "GARUPA_ROTA_MULTI",
                    "❌ Erro ao calcular rota multiparada",
                    erro
                )

                aoCalcular(
                    null
                )
            }

        }.start()
    }

    /*
     * =========================================================
     * CÁLCULO DE UM ÚNICO TRECHO
     * =========================================================
     */
    private fun calcularTrecho(
        origem: CoordenadaRota,
        destino: CoordenadaRota
    ): Pair<Double, Double>? {

        var conexao: HttpURLConnection? =
            null

        return try {

            val json =
                """
                {
                  "locations": [
                    {
                      "lat": ${origem.latitude},
                      "lon": ${origem.longitude}
                    },
                    {
                      "lat": ${destino.latitude},
                      "lon": ${destino.longitude}
                    }
                  ],
                  "costing": "motorcycle",
                  "units": "kilometers"
                }
                """.trimIndent()

            val url =
                URL(
                    "https://valhalla1.openstreetmap.de/route"
                )

            conexao =
                url.openConnection()
                        as HttpURLConnection

            conexao.requestMethod =
                "POST"

            conexao.doOutput =
                true

            conexao.connectTimeout =
                10000

            conexao.readTimeout =
                15000

            conexao.setRequestProperty(
                "Content-Type",
                "application/json; charset=UTF-8"
            )

            conexao.setRequestProperty(
                "Accept",
                "application/json"
            )

            conexao.setRequestProperty(
                "X-Client-Id",
                "br.com.garupa.app"
            )

            conexao.outputStream
                .bufferedWriter(
                    Charsets.UTF_8
                )
                .use { escritor ->

                    escritor.write(
                        json
                    )

                    escritor.flush()
                }

            val codigo =
                conexao.responseCode

            if (
                codigo !in 200..299
            ) {

                val erroServidor =
                    conexao.errorStream
                        ?.bufferedReader()
                        ?.use {
                            it.readText()
                        }
                        ?: "Sem detalhes"

                Log.e(
                    "GARUPA_ROTA",
                    "❌ Valhalla $codigo: $erroServidor"
                )

                return null
            }

            val resposta =
                conexao.inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            val objeto =
                JSONObject(
                    resposta
                )

            val resumo =
                objeto
                    .getJSONObject(
                        "trip"
                    )
                    .getJSONObject(
                        "summary"
                    )

            val distancia =
                resumo.getDouble(
                    "length"
                )

            val tempo =
                resumo.getDouble(
                    "time"
                )

            Pair(
                distancia,
                tempo
            )

        } catch (erro: Exception) {

            Log.e(
                "GARUPA_ROTA",
                "❌ Erro no trecho da rota",
                erro
            )

            null

        } finally {

            conexao
                ?.disconnect()
        }
    }
}