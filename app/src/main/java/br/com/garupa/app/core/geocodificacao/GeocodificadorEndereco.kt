package br.com.garupa.app.core.geocodificacao

import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.util.Log
import java.util.Locale

data class CoordenadaEndereco(
    val latitude: Double,
    val longitude: Double
)

class GeocodificadorEndereco(
    private val contexto: Context
) {

    private val geocoder =
        Geocoder(
            contexto,
            Locale("pt", "BR")
        )

    fun buscar(
        endereco: String,
        aoEncontrar: (CoordenadaEndereco?) -> Unit
    ) {

        if (endereco.isBlank()) {

            aoEncontrar(null)
            return
        }

        Log.d(
            "GARUPA_GEOCODER",
            "🔎 Buscando: $endereco"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            geocoder.getFromLocationName(
                endereco,
                1
            ) { resultados ->

                val local =
                    resultados.firstOrNull()

                if (local != null) {

                    val coordenada =
                        CoordenadaEndereco(
                            latitude = local.latitude,
                            longitude = local.longitude
                        )

                    Log.d(
                        "GARUPA_GEOCODER",
                        "📍 $endereco -> " +
                                "${coordenada.latitude}, " +
                                "${coordenada.longitude}"
                    )

                    aoEncontrar(coordenada)

                } else {

                    Log.d(
                        "GARUPA_GEOCODER",
                        "❌ Endereço não encontrado: $endereco"
                    )

                    aoEncontrar(null)
                }
            }

        } else {

            Thread {

                try {

                    @Suppress("DEPRECATION")
                    val resultados =
                        geocoder.getFromLocationName(
                            endereco,
                            1
                        )

                    val local =
                        resultados?.firstOrNull()

                    val coordenada =
                        local?.let {

                            CoordenadaEndereco(
                                latitude = it.latitude,
                                longitude = it.longitude
                            )
                        }

                    Log.d(
                        "GARUPA_GEOCODER",
                        "📍 $endereco -> $coordenada"
                    )

                    aoEncontrar(coordenada)

                } catch (erro: Exception) {

                    Log.e(
                        "GARUPA_GEOCODER",
                        "❌ Erro ao localizar endereço: $endereco",
                        erro
                    )

                    aoEncontrar(null)
                }

            }.start()
        }
    }
}