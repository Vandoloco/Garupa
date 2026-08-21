package br.com.garupa.app.core.rota

import android.location.Location
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
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

private data class RespostaHttpRota(
    val codigoHttp: Int?,
    val corpo: String?,
    val erro: Exception?
)

class CalculadorRota {

    companion object {

        private const val URL_VALHALLA =
            "https://valhalla1.openstreetmap.de/route"

        /*
         * O Garupa precisa responder rápido.
         */
        private const val TIMEOUT_CONEXAO_MS =
            4_000

        private const val TIMEOUT_LEITURA_MS =
            6_000

        /*
         * Retry controlado para falhas transitórias.
         */
        private const val MAX_TENTATIVAS =
            2

        private const val ESPERA_ENTRE_TENTATIVAS_MS =
            350L

        /*
         * =====================================================
         * FALLBACK LOCAL
         * =====================================================
         *
         * Quando o serviço externo de rota não responde,
         * usamos a distância geográfica entre os pontos e
         * aplicamos um fator viário conservador.
         *
         * Em área urbana, a distância percorrida por ruas
         * costuma ser maior que a linha reta.
         *
         * Este fator NÃO substitui a rota real.
         * Ele existe para o Garupa não ficar sem resposta.
         */
        private const val FATOR_VIARIO_FALLBACK =
            1.30

        /*
         * Estimativa simples de velocidade média urbana
         * usada SOMENTE para preencher tempo no fallback.
         *
         * A decisão atual do Garupa usa distância/R$/km,
         * então este tempo não altera a decisão.
         */
        private const val VELOCIDADE_MEDIA_FALLBACK_KMH =
            25.0
    }

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
     * ROTA MULTIPARADA
     * =========================================================
     *
     * Primeiro tenta rota real via Valhalla.
     *
     * Se o serviço estiver indisponível depois das tentativas
     * controladas, calcula uma estimativa local imediatamente.
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

                val pontos =
                    mutableListOf<CoordenadaRota>()

                pontos.add(
                    pontoInicial
                )

                pontos.addAll(
                    paradas
                )

                Log.d(
                    "GARUPA_ROTA_MULTI",
                    "🌐 Calculando rota completa em uma requisição | " +
                            "pontos=${pontos.size} | " +
                            "paradas=${paradas.size}"
                )

                val resultadoReal =
                    calcularRotaCompletaComRetry(
                        pontos =
                            pontos
                    )

                val resultadoFinal =
                    if (
                        resultadoReal != null
                    ) {

                        Log.d(
                            "GARUPA_ROTA_FONTE",
                            "✅ Fonte da distância: ROTA_REAL"
                        )

                        resultadoReal

                    } else {

                        Log.d(
                            "GARUPA_ROTA_FONTE",
                            "⚠️ Valhalla indisponível | usando FALLBACK_LOCAL"
                        )

                        calcularFallbackLocal(
                            pontos =
                                pontos
                        )
                    }

                if (
                    resultadoFinal == null
                ) {

                    Log.e(
                        "GARUPA_ROTA_MULTI",
                        "❌ Não foi possível obter nem rota real nem fallback"
                    )

                    aoCalcular(
                        null
                    )

                    return@Thread
                }

                resultadoFinal.trechos
                    .forEachIndexed { indice, trecho ->

                        Log.d(
                            "GARUPA_ROTA_MULTI",
                            "✅ Trecho ${indice + 1} | " +
                                    "${trecho.indiceOrigem} → " +
                                    "${trecho.indiceDestino} | " +
                                    "%.2f km | ".format(
                                        trecho.distanciaKm
                                    ) +
                                    "%.0f s".format(
                                        trecho.tempoSegundos
                                    )
                        )
                    }

                Log.d(
                    "GARUPA_ROTA_MULTI",
                    "🏁 Rota completa | " +
                            "paradas=${paradas.size} | " +
                            "distância=%.2f km | ".format(
                                resultadoFinal.distanciaTotalKm
                            ) +
                            "tempo=%.0f s".format(
                                resultadoFinal.tempoTotalSegundos
                            )
                )

                aoCalcular(
                    resultadoFinal
                )

            } catch (
                erro: Exception
            ) {

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
     * RETRY CONTROLADO
     * =========================================================
     */
    private fun calcularRotaCompletaComRetry(
        pontos: List<CoordenadaRota>
    ): ResultadoRotaMultipla? {

        for (
        tentativa in 1..MAX_TENTATIVAS
        ) {

            val resposta =
                executarRequisicaoValhalla(
                    pontos =
                        pontos,

                    tentativa =
                        tentativa
                )

            val erro =
                resposta.erro

            if (
                erro == null &&
                resposta.codigoHttp != null &&
                resposta.codigoHttp in 200..299 &&
                resposta.corpo != null
            ) {

                return processarRespostaValhalla(
                    resposta =
                        resposta.corpo,

                    quantidadePontos =
                        pontos.size
                )
            }

            val falhaTransitoria =
                when {

                    erro is SocketTimeoutException ->
                        true

                    resposta.codigoHttp in
                            listOf(
                                502,
                                503,
                                504
                            ) ->
                        true

                    else ->
                        false
                }

            if (
                !falhaTransitoria
            ) {

                Log.e(
                    "GARUPA_ROTA",
                    "❌ Falha não transitória; rota real cancelada"
                )

                return null
            }

            if (
                tentativa >=
                MAX_TENTATIVAS
            ) {

                Log.e(
                    "GARUPA_ROTA",
                    "❌ Falha transitória persistiu após " +
                            "$MAX_TENTATIVAS tentativas"
                )

                return null
            }

            Log.d(
                "GARUPA_ROTA",
                "🔄 Falha transitória. Nova tentativa em " +
                        "${ESPERA_ENTRE_TENTATIVAS_MS} ms | " +
                        "tentativa=${tentativa + 1}/$MAX_TENTATIVAS"
            )

            try {

                Thread.sleep(
                    ESPERA_ENTRE_TENTATIVAS_MS
                )

            } catch (
                interrompida: InterruptedException
            ) {

                Thread.currentThread()
                    .interrupt()

                return null
            }
        }

        return null
    }

    /*
     * =========================================================
     * REQUISIÇÃO HTTP
     * =========================================================
     */
    private fun executarRequisicaoValhalla(
        pontos: List<CoordenadaRota>,
        tentativa: Int
    ): RespostaHttpRota {

        var conexao: HttpURLConnection? =
            null

        return try {

            val locationsJson =
                pontos
                    .joinToString(
                        separator = ","
                    ) { ponto ->

                        """
                        {
                          "lat": ${ponto.latitude},
                          "lon": ${ponto.longitude}
                        }
                        """.trimIndent()
                    }

            val json =
                """
                {
                  "locations": [
                    $locationsJson
                  ],
                  "costing": "motorcycle",
                  "units": "kilometers"
                }
                """.trimIndent()

            val url =
                URL(
                    URL_VALHALLA
                )

            conexao =
                url.openConnection()
                        as HttpURLConnection

            conexao.requestMethod =
                "POST"

            conexao.doOutput =
                true

            conexao.connectTimeout =
                TIMEOUT_CONEXAO_MS

            conexao.readTimeout =
                TIMEOUT_LEITURA_MS

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

            val inicio =
                System.currentTimeMillis()

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

            val tempoRespostaMs =
                System.currentTimeMillis() -
                        inicio

            Log.d(
                "GARUPA_ROTA",
                "🌐 Valhalla respondeu em ${tempoRespostaMs} ms | " +
                        "HTTP $codigo | " +
                        "tentativa=$tentativa/$MAX_TENTATIVAS"
            )

            val corpo =
                if (
                    codigo in 200..299
                ) {

                    conexao.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                } else {

                    conexao.errorStream
                        ?.bufferedReader()
                        ?.use {
                            it.readText()
                        }
                        ?: "Sem detalhes"
                }

            if (
                codigo !in 200..299
            ) {

                Log.e(
                    "GARUPA_ROTA",
                    "❌ Valhalla $codigo: $corpo"
                )
            }

            RespostaHttpRota(
                codigoHttp =
                    codigo,

                corpo =
                    corpo,

                erro =
                    null
            )

        } catch (
            erro: SocketTimeoutException
        ) {

            Log.e(
                "GARUPA_ROTA",
                "⏱️ Timeout do Valhalla | " +
                        "tentativa=$tentativa/$MAX_TENTATIVAS | " +
                        "conexão=${TIMEOUT_CONEXAO_MS}ms | " +
                        "leitura=${TIMEOUT_LEITURA_MS}ms"
            )

            RespostaHttpRota(
                codigoHttp =
                    null,

                corpo =
                    null,

                erro =
                    erro
            )

        } catch (
            erro: Exception
        ) {

            Log.e(
                "GARUPA_ROTA",
                "❌ Erro na requisição de rota | " +
                        "tentativa=$tentativa/$MAX_TENTATIVAS",
                erro
            )

            RespostaHttpRota(
                codigoHttp =
                    null,

                corpo =
                    null,

                erro =
                    erro
            )

        } finally {

            conexao
                ?.disconnect()
        }
    }

    /*
     * =========================================================
     * FALLBACK LOCAL DE DISTÂNCIA
     * =========================================================
     *
     * Soma:
     *
     * A → B
     * B → C1
     * C1 → C2
     * ...
     *
     * Para cada trecho:
     *
     * distância em linha reta × fator viário.
     */
    private fun calcularFallbackLocal(
        pontos: List<CoordenadaRota>
    ): ResultadoRotaMultipla? {

        if (
            pontos.size < 2
        ) {

            return null
        }

        val trechos =
            mutableListOf<TrechoRota>()

        var distanciaTotalKm =
            0.0

        var tempoTotalSegundos =
            0.0

        for (
        indice in
        0 until pontos.size - 1
        ) {

            val origem =
                pontos[indice]

            val destino =
                pontos[indice + 1]

            val distanciaRetaKm =
                calcularDistanciaGeograficaKm(
                    origem =
                        origem,

                    destino =
                        destino
                )

            val distanciaEstimadaKm =
                distanciaRetaKm *
                        FATOR_VIARIO_FALLBACK

            val tempoEstimadoSegundos =
                if (
                    VELOCIDADE_MEDIA_FALLBACK_KMH >
                    0.0
                ) {

                    (
                            distanciaEstimadaKm /
                                    VELOCIDADE_MEDIA_FALLBACK_KMH
                            ) *
                            3600.0

                } else {

                    0.0
                }

            trechos.add(
                TrechoRota(
                    indiceOrigem =
                        indice,

                    indiceDestino =
                        indice + 1,

                    distanciaKm =
                        distanciaEstimadaKm,

                    tempoSegundos =
                        tempoEstimadoSegundos
                )
            )

            distanciaTotalKm +=
                distanciaEstimadaKm

            tempoTotalSegundos +=
                tempoEstimadoSegundos

            Log.d(
                "GARUPA_ROTA_FALLBACK",
                "📐 Trecho ${indice + 1} | " +
                        "reta=%.2f km | ".format(
                            distanciaRetaKm
                        ) +
                        "estimada=%.2f km | ".format(
                            distanciaEstimadaKm
                        ) +
                        "fator=$FATOR_VIARIO_FALLBACK"
            )
        }

        Log.d(
            "GARUPA_ROTA_FALLBACK",
            "✅ Fallback local concluído | " +
                    "distância=%.2f km | ".format(
                        distanciaTotalKm
                    ) +
                    "trechos=${trechos.size}"
        )

        return ResultadoRotaMultipla(
            trechos =
                trechos,

            distanciaTotalKm =
                distanciaTotalKm,

            tempoTotalSegundos =
                tempoTotalSegundos
        )
    }

    private fun calcularDistanciaGeograficaKm(
        origem: CoordenadaRota,
        destino: CoordenadaRota
    ): Double {

        val resultado =
            FloatArray(
                1
            )

        Location.distanceBetween(
            origem.latitude,
            origem.longitude,
            destino.latitude,
            destino.longitude,
            resultado
        )

        return resultado[0]
            .toDouble() /
                1000.0
    }

    /*
     * =========================================================
     * PROCESSAMENTO DA RESPOSTA REAL
     * =========================================================
     */
    private fun processarRespostaValhalla(
        resposta: String,
        quantidadePontos: Int
    ): ResultadoRotaMultipla? {

        return try {

            val objeto =
                JSONObject(
                    resposta
                )

            val trip =
                objeto.getJSONObject(
                    "trip"
                )

            val resumo =
                trip.getJSONObject(
                    "summary"
                )

            val distanciaTotal =
                resumo.getDouble(
                    "length"
                )

            val tempoTotal =
                resumo.getDouble(
                    "time"
                )

            val legs =
                trip.getJSONArray(
                    "legs"
                )

            val quantidadeTrechosEsperada =
                quantidadePontos - 1

            if (
                legs.length() !=
                quantidadeTrechosEsperada
            ) {

                Log.e(
                    "GARUPA_ROTA",
                    "❌ Quantidade inesperada de trechos | " +
                            "esperado=$quantidadeTrechosEsperada | " +
                            "recebido=${legs.length()}"
                )

                return null
            }

            val trechos =
                mutableListOf<TrechoRota>()

            for (
            indice in
            0 until legs.length()
            ) {

                val leg =
                    legs.getJSONObject(
                        indice
                    )

                val resumoTrecho =
                    leg.getJSONObject(
                        "summary"
                    )

                val distanciaTrecho =
                    resumoTrecho.getDouble(
                        "length"
                    )

                val tempoTrecho =
                    resumoTrecho.getDouble(
                        "time"
                    )

                trechos.add(
                    TrechoRota(
                        indiceOrigem =
                            indice,

                        indiceDestino =
                            indice + 1,

                        distanciaKm =
                            distanciaTrecho,

                        tempoSegundos =
                            tempoTrecho
                    )
                )
            }

            ResultadoRotaMultipla(
                trechos =
                    trechos,

                distanciaTotalKm =
                    distanciaTotal,

                tempoTotalSegundos =
                    tempoTotal
            )

        } catch (
            erro: Exception
        ) {

            Log.e(
                "GARUPA_ROTA",
                "❌ Erro ao interpretar resposta do Valhalla",
                erro
            )

            null
        }
    }
}