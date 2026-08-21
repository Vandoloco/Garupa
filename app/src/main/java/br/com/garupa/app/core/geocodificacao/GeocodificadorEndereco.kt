package br.com.garupa.app.core.geocodificacao

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import br.com.garupa.app.core.localizacao.GerenciadorLocalizacao
import java.util.Locale
import kotlin.math.cos

data class CoordenadaEndereco(
    val latitude: Double,
    val longitude: Double
)

class GeocodificadorEndereco(
    contexto: Context
) {

    companion object {

        /*
         * Uma coleta de delivery precisa estar em uma região
         * plausível em relação ao piloto.
         *
         * Este raio serve principalmente para impedir que nomes
         * ambíguos como "Barceloneta" sejam resolvidos para
         * outro estado ou outro país.
         */
        private const val RAIO_BUSCA_KM =
            100.0

        /*
         * Proteção final.
         *
         * Mesmo que algum backend do Geocoder ignore ou interprete
         * mal a caixa geográfica, não aceitamos um resultado
         * absurdamente distante do piloto.
         */
        private const val DISTANCIA_MAXIMA_RESULTADO_KM =
            120.0

        private const val MAX_RESULTADOS =
            5
    }

    private val contextoAplicacao =
        contexto.applicationContext

    private val geocoder =
        Geocoder(
            contextoAplicacao,
            Locale(
                "pt",
                "BR"
            )
        )

    private val gerenciadorLocalizacao =
        GerenciadorLocalizacao(
            contextoAplicacao
        )

    fun buscar(
        endereco: String,
        aoEncontrar: (CoordenadaEndereco?) -> Unit
    ) {

        val enderecoLimpo =
            endereco.trim()

        if (
            enderecoLimpo.isBlank()
        ) {

            aoEncontrar(
                null
            )

            return
        }

        Log.d(
            "GARUPA_GEOCODER",
            "🔎 Buscando: $enderecoLimpo"
        )

        /*
         * =====================================================
         * CONTEXTO GEOGRÁFICO DO PILOTO
         * =====================================================
         *
         * O Geocoder não deve procurar um nome ambíguo no mundo
         * inteiro quando sabemos onde o piloto está.
         *
         * Exemplo real:
         *
         * "Barceloneta"
         *
         * sem contexto:
         * -> Barcelona / Espanha
         *
         * com posição do piloto em Alphaville:
         * -> busca limitada à região próxima.
         */
        gerenciadorLocalizacao
            .obterUltimaLocalizacao { localizacaoPiloto ->

                if (
                    localizacaoPiloto == null
                ) {

                    Log.d(
                        "GARUPA_GEOCODER",
                        "⚠️ Sem posição do piloto; geocodificação local cancelada | " +
                                enderecoLimpo
                    )

                    aoEncontrar(
                        null
                    )

                    return@obterUltimaLocalizacao
                }

                buscarProximoAoPiloto(
                    endereco =
                        enderecoLimpo,

                    latitudePiloto =
                        localizacaoPiloto.latitude,

                    longitudePiloto =
                        localizacaoPiloto.longitude,

                    aoEncontrar =
                        aoEncontrar
                )
            }
    }

    private fun buscarProximoAoPiloto(
        endereco: String,
        latitudePiloto: Double,
        longitudePiloto: Double,
        aoEncontrar: (CoordenadaEndereco?) -> Unit
    ) {

        val limites =
            calcularLimitesBusca(
                latitude =
                    latitudePiloto,

                longitude =
                    longitudePiloto,

                raioKm =
                    RAIO_BUSCA_KM
            )

        Log.d(
            "GARUPA_GEOCODER",
            "🗺️ Busca local | " +
                    "origem=$latitudePiloto,$longitudePiloto | " +
                    "raio=${RAIO_BUSCA_KM.toInt()} km | " +
                    "consulta=\"$endereco\""
        )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            try {

                geocoder.getFromLocationName(
                    endereco,
                    MAX_RESULTADOS,
                    limites.latitudeMinima,
                    limites.longitudeMinima,
                    limites.latitudeMaxima,
                    limites.longitudeMaxima
                ) { resultados ->

                    processarResultados(
                        endereco =
                            endereco,

                        resultados =
                            resultados,

                        latitudePiloto =
                            latitudePiloto,

                        longitudePiloto =
                            longitudePiloto,

                        aoEncontrar =
                            aoEncontrar
                    )
                }

            } catch (
                erro: Exception
            ) {

                Log.e(
                    "GARUPA_GEOCODER",
                    "❌ Erro ao iniciar busca local: $endereco",
                    erro
                )

                aoEncontrar(
                    null
                )
            }

        } else {

            Thread {

                try {

                    @Suppress("DEPRECATION")
                    val resultados =
                        geocoder.getFromLocationName(
                            endereco,
                            MAX_RESULTADOS,
                            limites.latitudeMinima,
                            limites.longitudeMinima,
                            limites.latitudeMaxima,
                            limites.longitudeMaxima
                        )
                            .orEmpty()

                    processarResultados(
                        endereco =
                            endereco,

                        resultados =
                            resultados,

                        latitudePiloto =
                            latitudePiloto,

                        longitudePiloto =
                            longitudePiloto,

                        aoEncontrar =
                            aoEncontrar
                    )

                } catch (
                    erro: Exception
                ) {

                    Log.e(
                        "GARUPA_GEOCODER",
                        "❌ Erro ao localizar endereço: $endereco",
                        erro
                    )

                    aoEncontrar(
                        null
                    )
                }

            }.start()
        }
    }

    private fun processarResultados(
        endereco: String,
        resultados: List<Address>,
        latitudePiloto: Double,
        longitudePiloto: Double,
        aoEncontrar: (CoordenadaEndereco?) -> Unit
    ) {

        if (
            resultados.isEmpty()
        ) {

            Log.d(
                "GARUPA_GEOCODER",
                "❌ Nenhum resultado local encontrado: $endereco"
            )

            aoEncontrar(
                null
            )

            return
        }

        /*
         * Escolhemos o resultado mais próximo do piloto.
         *
         * Mesmo dentro da caixa podem existir vários locais
         * com nomes parecidos.
         */
        val melhorResultado =
            resultados
                .map { enderecoEncontrado ->

                    val distanciaKm =
                        calcularDistanciaKm(
                            latitudeOrigem =
                                latitudePiloto,

                            longitudeOrigem =
                                longitudePiloto,

                            latitudeDestino =
                                enderecoEncontrado.latitude,

                            longitudeDestino =
                                enderecoEncontrado.longitude
                        )

                    enderecoEncontrado to
                            distanciaKm
                }
                .minByOrNull {
                    it.second
                }

        if (
            melhorResultado == null
        ) {

            aoEncontrar(
                null
            )

            return
        }

        val local =
            melhorResultado.first

        val distanciaKm =
            melhorResultado.second

        /*
         * Última barreira contra resultado geográfico absurdo.
         */
        if (
            distanciaKm >
            DISTANCIA_MAXIMA_RESULTADO_KM
        ) {

            Log.d(
                "GARUPA_GEOCODER",
                "🚫 Resultado rejeitado por distância | " +
                        "consulta=\"$endereco\" | " +
                        "resultado=${local.latitude},${local.longitude} | " +
                        "distancia=%.1f km".format(
                            distanciaKm
                        )
            )

            aoEncontrar(
                null
            )

            return
        }

        val coordenada =
            CoordenadaEndereco(
                latitude =
                    local.latitude,

                longitude =
                    local.longitude
            )

        Log.d(
            "GARUPA_GEOCODER",
            "📍 $endereco -> " +
                    "${coordenada.latitude}, " +
                    "${coordenada.longitude} | " +
                    "distanciaPiloto=%.1f km".format(
                        distanciaKm
                    )
        )

        aoEncontrar(
            coordenada
        )
    }

    private fun calcularDistanciaKm(
        latitudeOrigem: Double,
        longitudeOrigem: Double,
        latitudeDestino: Double,
        longitudeDestino: Double
    ): Double {

        val resultado =
            FloatArray(
                1
            )

        Location.distanceBetween(
            latitudeOrigem,
            longitudeOrigem,
            latitudeDestino,
            longitudeDestino,
            resultado
        )

        return resultado[0]
            .toDouble() /
                1000.0
    }

    private fun calcularLimitesBusca(
        latitude: Double,
        longitude: Double,
        raioKm: Double
    ): LimitesBusca {

        /*
         * Aproximação suficiente para criar a caixa de busca.
         *
         * 1 grau de latitude ≈ 111 km.
         */
        val deltaLatitude =
            raioKm /
                    111.0

        val latitudeEmRadianos =
            Math.toRadians(
                latitude
            )

        val fatorLongitude =
            cos(
                latitudeEmRadianos
            )
                .let { valor ->

                    if (
                        kotlin.math.abs(
                            valor
                        ) <
                        0.01
                    ) {
                        0.01
                    } else {
                        valor
                    }
                }

        val deltaLongitude =
            raioKm /
                    (
                            111.0 *
                                    kotlin.math.abs(
                                        fatorLongitude
                                    )
                            )

        return LimitesBusca(
            latitudeMinima =
                (latitude - deltaLatitude)
                    .coerceIn(
                        -90.0,
                        90.0
                    ),

            longitudeMinima =
                (longitude - deltaLongitude)
                    .coerceIn(
                        -180.0,
                        180.0
                    ),

            latitudeMaxima =
                (latitude + deltaLatitude)
                    .coerceIn(
                        -90.0,
                        90.0
                    ),

            longitudeMaxima =
                (longitude + deltaLongitude)
                    .coerceIn(
                        -180.0,
                        180.0
                    )
        )
    }

    private data class LimitesBusca(
        val latitudeMinima: Double,
        val longitudeMinima: Double,
        val latitudeMaxima: Double,
        val longitudeMaxima: Double
    )
}