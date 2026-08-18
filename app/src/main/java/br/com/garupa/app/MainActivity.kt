package br.com.garupa.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import br.com.garupa.app.core.Garupa
import br.com.garupa.app.core.captura.CapturaForegroundService
import br.com.garupa.app.core.captura.CapturaTela
import br.com.garupa.app.core.localizacao.GerenciadorLocalizacao
import br.com.garupa.app.ui.theme.GarupaTheme

class MainActivity : ComponentActivity() {

    private lateinit var capturaTela:
            CapturaTela

    private lateinit var gerenciadorLocalizacao:
            GerenciadorLocalizacao

    /*
     * =========================================================
     * BLUETOOTH
     * =========================================================
     */

    private val pedidoBluetooth =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { concedida ->

            if (
                concedida
            ) {

                Log.d(
                    "GARUPA_BLUETOOTH",
                    "🎧 Permissão Bluetooth concedida"
                )

            } else {

                Log.d(
                    "GARUPA_BLUETOOTH",
                    "⚠️ Bluetooth não autorizado; Garupa poderá usar microfone do celular"
                )
            }

            verificarPermissaoMicrofone()
        }

    /*
     * =========================================================
     * MICROFONE
     * =========================================================
     */

    private val pedidoMicrofone =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { concedida ->

            if (
                concedida
            ) {

                Log.d(
                    "GARUPA_OUVIDO",
                    "🎤 Permissão de microfone concedida"
                )

                Garupa.iniciarEscuta()

            } else {

                Log.d(
                    "GARUPA_OUVIDO",
                    "❌ Permissão de microfone não concedida"
                )
            }

            verificarPermissaoLocalizacao()
        }

    /*
     * =========================================================
     * LOCALIZAÇÃO
     * =========================================================
     */

    private val pedidoLocalizacao =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissoes ->

            val localizacaoPrecisa =
                permissoes[
                    Manifest.permission.ACCESS_FINE_LOCATION
                ] == true

            val localizacaoAproximada =
                permissoes[
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ] == true

            if (
                localizacaoPrecisa ||
                localizacaoAproximada
            ) {

                Log.d(
                    "GARUPA_LOCALIZACAO",
                    "📍 Permissão de localização concedida"
                )

                obterLocalizacaoPiloto()

            } else {

                Log.d(
                    "GARUPA_LOCALIZACAO",
                    "❌ Permissão de localização não concedida"
                )

                iniciarPedidoCaptura()
            }
        }

    /*
     * =========================================================
     * CAPTURA
     * =========================================================
     */

    private val pedidoCapturaTela =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { resultado ->

            if (
                ::capturaTela.isInitialized &&
                capturaTela.permissaoConcedida(
                    resultado.resultCode
                ) &&
                resultado.data != null
            ) {

                val intentServico =
                    Intent(
                        this,
                        CapturaForegroundService::class.java
                    ).apply {

                        putExtra(
                            CapturaForegroundService.EXTRA_RESULT_CODE,
                            resultado.resultCode
                        )

                        putExtra(
                            CapturaForegroundService.EXTRA_RESULT_DATA,
                            resultado.data
                        )
                    }

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O
                ) {

                    startForegroundService(
                        intentServico
                    )

                } else {

                    startService(
                        intentServico
                    )
                }

                Log.d(
                    "GARUPA",
                    "📸 Captura contínua autorizada e serviço iniciado"
                )

            } else {

                Log.d(
                    "GARUPA",
                    "📸 Captura de tela não autorizada"
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        Garupa.iniciar(
            this
        )

        capturaTela =
            CapturaTela(
                this
            )

        gerenciadorLocalizacao =
            GerenciadorLocalizacao(
                this
            )

        enableEdgeToEdge()

        setContent {

            GarupaTheme {

                Scaffold(
                    modifier =
                        Modifier.fillMaxSize()
                ) { innerPadding ->

                    GarupaTela(
                        modifier =
                            Modifier.padding(
                                innerPadding
                            )
                    )
                }
            }
        }

        verificarPermissaoBluetooth()
    }

    /*
     * =========================================================
     * BLUETOOTH
     * =========================================================
     */

    private fun verificarPermissaoBluetooth() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.S
        ) {

            verificarPermissaoMicrofone()

            return
        }

        val permissao =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            )

        if (
            permissao ==
            PackageManager.PERMISSION_GRANTED
        ) {

            Log.d(
                "GARUPA_BLUETOOTH",
                "🎧 Bluetooth já autorizado"
            )

            verificarPermissaoMicrofone()

        } else {

            pedidoBluetooth.launch(
                Manifest.permission.BLUETOOTH_CONNECT
            )
        }
    }

    /*
     * =========================================================
     * MICROFONE
     * =========================================================
     */

    private fun verificarPermissaoMicrofone() {

        val permissao =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            )

        if (
            permissao ==
            PackageManager.PERMISSION_GRANTED
        ) {

            Log.d(
                "GARUPA_OUVIDO",
                "🎤 Microfone já autorizado"
            )

            Garupa.iniciarEscuta()

            verificarPermissaoLocalizacao()

        } else {

            pedidoMicrofone.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    /*
     * =========================================================
     * LOCALIZAÇÃO
     * =========================================================
     */

    private fun verificarPermissaoLocalizacao() {

        val fine =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )

        val coarse =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )

        if (
            fine ==
            PackageManager.PERMISSION_GRANTED ||
            coarse ==
            PackageManager.PERMISSION_GRANTED
        ) {

            obterLocalizacaoPiloto()

        } else {

            pedidoLocalizacao.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun obterLocalizacaoPiloto() {

        gerenciadorLocalizacao
            .obterUltimaLocalizacao { localizacao ->

                if (
                    localizacao != null
                ) {

                    Log.d(
                        "GARUPA_LOCALIZACAO",
                        "✅ Ponto A inicial disponível: " +
                                "${localizacao.latitude}, " +
                                "${localizacao.longitude}"
                    )

                } else {

                    Log.d(
                        "GARUPA_LOCALIZACAO",
                        "⚠️ Ponto A inicial ainda indisponível"
                    )
                }

                iniciarPedidoCaptura()
            }
    }

    private fun iniciarPedidoCaptura() {

        pedidoCapturaTela.launch(
            capturaTela.criarPedidoPermissao()
        )
    }
}