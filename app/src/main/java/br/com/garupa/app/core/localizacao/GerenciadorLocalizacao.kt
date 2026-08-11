package br.com.garupa.app.core.localizacao

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.location.LocationServices

class GerenciadorLocalizacao(
    private val contexto: Context
) {

    private val clienteLocalizacao =
        LocationServices.getFusedLocationProviderClient(contexto)

    @SuppressLint("MissingPermission")
    fun obterUltimaLocalizacao(
        aoObter: (LocalizacaoPiloto?) -> Unit
    ) {

        clienteLocalizacao.lastLocation
            .addOnSuccessListener { localizacao ->

                if (localizacao != null) {

                    val posicaoPiloto =
                        LocalizacaoPiloto(
                            latitude = localizacao.latitude,
                            longitude = localizacao.longitude
                        )

                    Log.d(
                        "GARUPA_LOCALIZACAO",
                        "📍 Piloto em: ${posicaoPiloto.latitude}, ${posicaoPiloto.longitude}"
                    )

                    aoObter(posicaoPiloto)

                } else {

                    Log.d(
                        "GARUPA_LOCALIZACAO",
                        "📍 Localização do piloto ainda indisponível"
                    )

                    aoObter(null)
                }
            }
            .addOnFailureListener { erro ->

                Log.e(
                    "GARUPA_LOCALIZACAO",
                    "📍 Erro ao obter localização",
                    erro
                )

                aoObter(null)
            }
    }
}