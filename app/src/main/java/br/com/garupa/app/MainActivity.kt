package br.com.garupa.app

import android.content.Intent
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
import br.com.garupa.app.core.Garupa
import br.com.garupa.app.core.captura.CapturaForegroundService
import br.com.garupa.app.core.captura.CapturaTela
import br.com.garupa.app.ui.theme.GarupaTheme

class MainActivity : ComponentActivity() {

    private lateinit var capturaTela: CapturaTela

    private val pedidoCapturaTela =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { resultado ->

            if (
                ::capturaTela.isInitialized &&
                capturaTela.permissaoConcedida(resultado.resultCode) &&
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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intentServico)
                } else {
                    startService(intentServico)
                }

                Log.d(
                    "GARUPA",
                    "📸 Autorização enviada para o serviço de captura"
                )

            } else {

                Log.d(
                    "GARUPA",
                    "📸 Captura de tela não autorizada"
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Garupa.iniciar(this)

        capturaTela = CapturaTela(this)

        enableEdgeToEdge()

        setContent {
            GarupaTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->

                    GarupaTela(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        pedidoCapturaTela.launch(
            capturaTela.criarPedidoPermissao()
        )
    }
}