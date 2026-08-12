package br.com.garupa.app.core.localizacao

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class GerenciadorLocalizacao(
    contexto: Context
) {

    private val clienteLocalizacao: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(
            contexto.applicationContext
        )

    /*
     * Callback usado para receber a posição
     * continuamente enquanto o Garupa estiver ativo.
     */
    private val callbackLocalizacao =
        object : LocationCallback() {

            override fun onLocationResult(
                resultado: LocationResult
            ) {

                val localizacao =
                    resultado.lastLocation
                        ?: return

                atualizarCache(
                    localizacao
                )

                Log.d(
                    "GARUPA_LOCALIZACAO",
                    "🏍️ A atualizado: " +
                            "${localizacao.latitude}, " +
                            "${localizacao.longitude} | " +
                            "precisão: ${localizacao.accuracy} m"
                )
            }
        }

    companion object {

        /*
         * Cache compartilhado entre todas
         * as instâncias do GerenciadorLocalizacao.
         */
        @Volatile
        private var localizacaoEmCache: LocalizacaoPiloto? =
            null

        @Volatile
        private var horarioLocalizacaoCache: Long =
            0L

        /*
         * Para análise de oferta, queremos uma
         * posição bem recente.
         */
        private const val VALIDADE_CACHE_MS =
            5_000L

        /*
         * Atualização desejada enquanto
         * o piloto estiver em movimento.
         */
        private const val INTERVALO_ATUALIZACAO_MS =
            2_000L

        private const val INTERVALO_MINIMO_MS =
            1_000L

        @Volatile
        private var atualizacaoContinuaAtiva =
            false
    }

    /*
     * Inicia acompanhamento contínuo do ponto A.
     *
     * Chamaremos isso quando o Garupa entrar
     * em estado ativo.
     */
    @SuppressLint("MissingPermission")
    fun iniciarAtualizacoes() {

        if (atualizacaoContinuaAtiva) {

            Log.d(
                "GARUPA_LOCALIZACAO",
                "ℹ️ Atualização contínua já está ativa"
            )

            return
        }

        val requisicao =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                INTERVALO_ATUALIZACAO_MS
            )
                .setMinUpdateIntervalMillis(
                    INTERVALO_MINIMO_MS
                )
                .build()

        Log.d(
            "GARUPA_LOCALIZACAO",
            "🟢 Iniciando atualização contínua do ponto A"
        )

        clienteLocalizacao
            .requestLocationUpdates(
                requisicao,
                callbackLocalizacao,
                Looper.getMainLooper()
            )

        atualizacaoContinuaAtiva =
            true
    }

    /*
     * Para as atualizações contínuas.
     *
     * Usaremos quando o Garupa deixar
     * de precisar acompanhar o piloto.
     */
    fun pararAtualizacoes() {

        if (!atualizacaoContinuaAtiva) {
            return
        }

        clienteLocalizacao
            .removeLocationUpdates(
                callbackLocalizacao
            )

        atualizacaoContinuaAtiva =
            false

        Log.d(
            "GARUPA_LOCALIZACAO",
            "🔴 Atualização contínua do ponto A interrompida"
        )
    }

    /*
     * Entrega ao restante do Garupa
     * a posição mais recente disponível.
     *
     * Se o cache tiver até 5 segundos,
     * usamos imediatamente.
     *
     * Se estiver velho ou vazio,
     * buscamos uma posição atual.
     */
    @SuppressLint("MissingPermission")
    fun obterUltimaLocalizacao(
        aoObter: (LocalizacaoPiloto?) -> Unit
    ) {

        val cache =
            obterCacheValido()

        if (cache != null) {

            Log.d(
                "GARUPA_LOCALIZACAO",
                "📍 Usando A recente: " +
                        "${cache.latitude}, " +
                        "${cache.longitude}"
            )

            aoObter(
                cache
            )

            return
        }

        Log.d(
            "GARUPA_LOCALIZACAO",
            "📡 A está antigo ou ausente. Buscando posição atual..."
        )

        val cancellationTokenSource =
            CancellationTokenSource()

        clienteLocalizacao
            .getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            )
            .addOnSuccessListener { localizacao ->

                if (localizacao != null) {

                    atualizarCache(
                        localizacao
                    )

                    val posicao =
                        LocalizacaoPiloto(
                            latitude =
                                localizacao.latitude,

                            longitude =
                                localizacao.longitude
                        )

                    Log.d(
                        "GARUPA_LOCALIZACAO",
                        "📍 A atualizado sob demanda: " +
                                "${posicao.latitude}, " +
                                "${posicao.longitude}"
                    )

                    aoObter(
                        posicao
                    )

                } else {

                    buscarUltimaLocalizacaoConhecida(
                        aoObter
                    )
                }
            }
            .addOnFailureListener { erro ->

                Log.e(
                    "GARUPA_LOCALIZACAO",
                    "❌ Erro ao buscar localização atual",
                    erro
                )

                buscarUltimaLocalizacaoConhecida(
                    aoObter
                )
            }
    }

    /*
     * Fallback do Android.
     */
    @SuppressLint("MissingPermission")
    private fun buscarUltimaLocalizacaoConhecida(
        aoObter: (LocalizacaoPiloto?) -> Unit
    ) {

        /*
         * Outra instância pode ter atualizado
         * o cache enquanto aguardávamos.
         */
        val cache =
            obterCacheValido()

        if (cache != null) {

            Log.d(
                "GARUPA_LOCALIZACAO",
                "📍 Usando A recente após fallback: " +
                        "${cache.latitude}, " +
                        "${cache.longitude}"
            )

            aoObter(
                cache
            )

            return
        }

        clienteLocalizacao
            .lastLocation
            .addOnSuccessListener { localizacao ->

                if (localizacao != null) {

                    atualizarCache(
                        localizacao
                    )

                    val posicao =
                        LocalizacaoPiloto(
                            latitude =
                                localizacao.latitude,

                            longitude =
                                localizacao.longitude
                        )

                    Log.d(
                        "GARUPA_LOCALIZACAO",
                        "📍 Última posição conhecida usada: " +
                                "${posicao.latitude}, " +
                                "${posicao.longitude}"
                    )

                    aoObter(
                        posicao
                    )

                } else {

                    Log.d(
                        "GARUPA_LOCALIZACAO",
                        "❌ Ponto A indisponível"
                    )

                    aoObter(
                        null
                    )
                }
            }
            .addOnFailureListener { erro ->

                Log.e(
                    "GARUPA_LOCALIZACAO",
                    "❌ Erro ao obter última posição conhecida",
                    erro
                )

                aoObter(
                    obterCacheValido()
                )
            }
    }

    /*
     * Atualiza a memória compartilhada.
     */
    private fun atualizarCache(
        localizacao: Location
    ) {

        localizacaoEmCache =
            LocalizacaoPiloto(
                latitude =
                    localizacao.latitude,

                longitude =
                    localizacao.longitude
            )

        horarioLocalizacaoCache =
            System.currentTimeMillis()
    }

    /*
     * Retorna somente uma posição
     * suficientemente recente.
     */
    private fun obterCacheValido(): LocalizacaoPiloto? {

        val localizacao =
            localizacaoEmCache
                ?: return null

        val idade =
            System.currentTimeMillis() -
                    horarioLocalizacaoCache

        if (
            idade >
            VALIDADE_CACHE_MS
        ) {

            return null
        }

        return localizacao
    }
}