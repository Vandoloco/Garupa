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

class CalculadorRota {

    fun calcularABC(
        pontoA: CoordenadaRota,
        pontoB: CoordenadaRota,
        pontoC: CoordenadaRota,
        aoCalcular: (ResultadoRota?) -> Unit
    ) {

        Thread {

            try {

                val rotaAB =
                    calcularTrecho(
                        origem = pontoA,
                        destino = pontoB
                    )

                if (rotaAB == null) {

                    Log.e(
                        "GARUPA_ROTA",
                        "❌ Não foi possível calcular A → B"
                    )

                    aoCalcular(null)
                    return@Thread
                }

                val rotaBC =
                    calcularTrecho(
                        origem = pontoB,
                        destino = pontoC
                    )

                if (rotaBC == null) {

                    Log.e(
                        "GARUPA_ROTA",
                        "❌ Não foi possível calcular B → C"
                    )

                    aoCalcular(null)
                    return@Thread
                }

                val distanciaTotal =
                    rotaAB.first +
                            rotaBC.first

                val resultado =
                    ResultadoRota(
                        distanciaABKm =
                            rotaAB.first,

                        distanciaBCKm =
                            rotaBC.first,

                        distanciaTotalKm =
                            distanciaTotal,

                        tempoABSegundos =
                            rotaAB.second,

                        tempoBCSegundos =
                            rotaBC.second
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

            } catch (erro: Exception) {

                Log.e(
                    "GARUPA_ROTA",
                    "❌ Erro ao calcular rota completa",
                    erro
                )

                aoCalcular(null)
            }

        }.start()
    }

    private fun calcularTrecho(
        origem: CoordenadaRota,
        destino: CoordenadaRota
    ): Pair<Double, Double>? {

        var conexao: HttpURLConnection? = null

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

            conexao?.disconnect()
        }
    }
}