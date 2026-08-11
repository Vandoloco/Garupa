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
import br.com.garupa.app.core.teste.TesteImagem
import br.com.garupa.app.ui.theme.GarupaTheme

class MainActivity : ComponentActivity() {

    private lateinit var capturaTela: CapturaTela
    private lateinit var gerenciadorLocalizacao: GerenciadorLocalizacao
    private lateinit var testeImagem: TesteImagem

    /*
     * TESTE TEMPORÁRIO
     *
     * Abre a galeria para escolher uma print.
     * Depois a imagem é enviada para o mesmo OCR
     * utilizado pelo Garupa.
     */
    private val selecionarImagemTeste =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            if (uri != null) {

                Log.d(
                    "GARUPA_TESTE",
                    "🧪 Print selecionada"
                )

                testeImagem.analisarImagem(uri)

            } else {

                Log.d(
                    "GARUPA_TESTE",
                    "🧪 Nenhuma print selecionada"
                )
            }
        }

    /*
     * Permissão de localização.
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
                    "📍 Permissão de localização não concedida"
                )

                iniciarPedidoCaptura()
            }
        }

    /*
     * Autorização para captura da tela inteira.
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
                    "📸 Autorização enviada para o serviço de captura"
                )

                /*
                 * Depois da autorização,
                 * abre a galeria para o teste offline.
                 */
                abrirGaleriaTeste()

            } else {

                Log.d(
                    "GARUPA",
                    "📸 Captura de tela não autorizada"
                )

                /*
                 * Mesmo sem captura,
                 * ainda podemos testar uma print.
                 */
                abrirGaleriaTeste()
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        /*
         * Inicia o Garupa.
         */
        Garupa.iniciar(this)

        /*
         * Componentes principais.
         */
        capturaTela =
            CapturaTela(this)

        gerenciadorLocalizacao =
            GerenciadorLocalizacao(this)

        testeImagem =
            TesteImagem(this)

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

        verificarPermissaoLocalizacao()
    }

    /*
     * Verifica se o piloto já autorizou
     * o acesso à localização.
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

    /*
     * Obtém o ponto A:
     * posição atual do piloto.
     */
    private fun obterLocalizacaoPiloto() {

        gerenciadorLocalizacao
            .obterUltimaLocalizacao { localizacao ->

                if (localizacao != null) {

                    Log.d(
                        "GARUPA_LOCALIZACAO",
                        "📍 Localização recebida com sucesso"
                    )

                } else {

                    Log.d(
                        "GARUPA_LOCALIZACAO",
                        "📍 Não foi possível obter a localização"
                    )
                }

                iniciarPedidoCaptura()
            }
    }

    /*
     * Solicita captura da tela inteira.
     */
    private fun iniciarPedidoCaptura() {

        pedidoCapturaTela.launch(
            capturaTela.criarPedidoPermissao()
        )
    }

    /*
     * Abre a galeria somente para
     * nosso modo de desenvolvimento/teste.
     */
    private fun abrirGaleriaTeste() {

        Log.d(
            "GARUPA_TESTE",
            "🧪 Abrindo galeria para teste"
        )

        selecionarImagemTeste.launch(
            "image/*"
        )
    }
}