package br.com.garupa.app

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
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
import br.com.garupa.app.core.acessibilidade.GarupaAccessibilityService
import br.com.garupa.app.core.captura.CapturaForegroundService
import br.com.garupa.app.core.captura.CapturaTela
import br.com.garupa.app.core.localizacao.GerenciadorLocalizacao
import br.com.garupa.app.core.monitoramento.NivelRegistroGarupa
import br.com.garupa.app.ui.theme.GarupaTheme
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

class MainActivity : ComponentActivity() {

    private lateinit var capturaTela:
            CapturaTela

    private lateinit var gerenciadorLocalizacao:
            GerenciadorLocalizacao

    /*
     * =========================================================
     * NOTIFICAÇÕES
     * =========================================================
     */

    private val pedidoNotificacoes =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { concedida ->

            if (concedida) {
                Log.d(
                    "GARUPA_NOTIFICACAO",
                    "🔔 Permissão de notificações concedida"
                )
            } else {
                Log.d(
                    "GARUPA_NOTIFICACAO",
                    "⚠️ Permissão de notificações não concedida"
                )
            }

            verificarPermissaoBluetooth()
        }

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

                verificarAcessibilidade()
            }
        }

    /*
     * =========================================================
     * ACESSIBILIDADE
     * =========================================================
     */

    private val pedidoAcessibilidade =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {

            /*
             * Ao voltar das Configurações, verificamos novamente.
             * O Android exige que o próprio usuário ative o serviço.
             */
            verificarAcessibilidade()
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

                Garupa
                    .obterMonitor()
                    ?.registrar(
                        nivel =
                            NivelRegistroGarupa.INFO,

                        categoria =
                            "CAPTURA",

                        mensagem =
                            "Captura contínua autorizada e serviço iniciado"
                    )

            } else {

                Log.d(
                    "GARUPA",
                    "📸 Captura de tela não autorizada"
                )

                Garupa
                    .obterMonitor()
                    ?.registrar(
                        nivel =
                            NivelRegistroGarupa.AVISO,

                        categoria =
                            "CAPTURA",

                        mensagem =
                            "Captura de tela não autorizada"
                    )
            }
        }

    /*
     * =========================================================
     * ON CREATE
     * =========================================================
     */

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        /*
         * =====================================================
         * FIREBASE APP CHECK - DEBUG
         * =====================================================
         *
         * Esta configuração é somente para desenvolvimento.
         *
         * Ao iniciar o app, o Firebase vai gerar um token
         * de debug que cadastraremos no Console Firebase.
         *
         * Mais adiante, para produção, trocamos este provider
         * por um provider real do App Check.
         */
        FirebaseAppCheck
            .getInstance()
            .installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )

        Log.d(
            "GARUPA_FIREBASE",
            "🔥 Firebase App Check DEBUG inicializado"
        )

        /*
         * =====================================================
         * GARUPA
         * =====================================================
         */

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

        verificarPermissaoNotificacoes()
    }

    /*
     * =========================================================
     * NOTIFICAÇÕES
     * =========================================================
     */

    private fun verificarPermissaoNotificacoes() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU
        ) {
            verificarPermissaoBluetooth()
            return
        }

        val permissao =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )

        if (
            permissao ==
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(
                "GARUPA_NOTIFICACAO",
                "🔔 Notificações já autorizadas"
            )

            verificarPermissaoBluetooth()
        } else {
            pedidoNotificacoes.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
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

                verificarAcessibilidade()
            }
    }

    /*
     * =========================================================
     * ACESSIBILIDADE
     * =========================================================
     */

    private fun verificarAcessibilidade() {

        if (
            acessibilidadeGarupaAtiva()
        ) {

            Log.d(
                "GARUPA_ACESSIBILIDADE",
                "👁️ Acessibilidade do Garupa já está ativa"
            )

            Garupa
                .obterMonitor()
                ?.registrar(
                    nivel =
                        NivelRegistroGarupa.INFO,

                    categoria =
                        "ACESSIBILIDADE",

                    mensagem =
                        "Serviço de acessibilidade ativo"
                )

            iniciarPedidoCaptura()

            return
        }

        Log.d(
            "GARUPA_ACESSIBILIDADE",
            "⚠️ Acessibilidade do Garupa está desativada"
        )

        Garupa
            .obterMonitor()
            ?.registrar(
                nivel =
                    NivelRegistroGarupa.AVISO,

                categoria =
                    "ACESSIBILIDADE",

                mensagem =
                    "Serviço de acessibilidade desativado"
            )

        val intent =
            Intent(
                Settings.ACTION_ACCESSIBILITY_SETTINGS
            )

        pedidoAcessibilidade.launch(
            intent
        )
    }

    private fun acessibilidadeGarupaAtiva():
            Boolean {

        val componenteGarupa =
            ComponentName(
                this,
                GarupaAccessibilityService::class.java
            )

        val accessibilityManager =
            getSystemService(
                AccessibilityManager::class.java
            )

        val servicosAtivos =
            accessibilityManager
                .getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                )

        return servicosAtivos
            .any { servico ->

                val infoServico =
                    servico.resolveInfo.serviceInfo

                val componente =
                    ComponentName(
                        infoServico.packageName,
                        infoServico.name
                    )

                componente ==
                        componenteGarupa
            }
    }

    /*
     * =========================================================
     * CAPTURA DE TELA
     * =========================================================
     */

    private fun iniciarPedidoCaptura() {

        pedidoCapturaTela.launch(
            capturaTela.criarPedidoPermissao()
        )
    }
}