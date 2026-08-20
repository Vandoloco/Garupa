package br.com.garupa.app.core.captura

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import br.com.garupa.app.core.Garupa
import br.com.garupa.app.core.GarupaEstado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import br.com.garupa.app.core.leitura.LeitorTela
import br.com.garupa.app.core.localizacao.GerenciadorLocalizacao
import java.io.File
import java.io.FileOutputStream

class CapturaForegroundService : Service() {

    private val escopoServico =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.Main.immediate
        )

    private var trabalhoEstado:
            Job? =
        null

    companion object {

        const val CHANNEL_ID = "garupa_captura"
        const val NOTIFICATION_ID = 1001

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        const val INTERVALO_CAPTURA_MS = 1200L

        @Volatile
        var capturaAtiva: Boolean = false
            private set

        @Volatile
        var precisaNovaAutorizacao: Boolean = false
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    /*
     * Mantém a CPU disponível quando a tela apaga.
     *
     * O Foreground Service mantém o processo prioritário,
     * enquanto este WakeLock evita que o aparelho suspenda
     * o processamento do Ouvido durante o turno.
     */
    private var wakeLock:
            PowerManager.WakeLock? =
        null

    private lateinit var leitorTela: LeitorTela

    /*
     * Agora a localização do piloto também
     * pertence ao Foreground Service.
     */
    private lateinit var gerenciadorLocalizacao:
            GerenciadorLocalizacao

    private var ultimoFrameProcessado = 0L

    private val projectionCallback =
        object : MediaProjection.Callback() {

            override fun onStop() {

                Log.d(
                    "GARUPA_CAPTURA",
                    "🛑 MediaProjection encerrado pelo Android"
                )

                capturaAtiva = false
                precisaNovaAutorizacao = true

                liberarRecursosCaptura(
                    liberarProjection = false
                )

                /*
                 * Importante:
                 *
                 * A captura pode terminar,
                 * mas não paramos automaticamente
                 * a localização aqui.
                 *
                 * Enquanto o serviço estiver vivo,
                 * o Garupa continua acompanhando A.
                 */

                Log.d(
                    "GARUPA_CAPTURA",
                    "⚠️ Nova autorização de captura será necessária"
                )
            }
        }

    override fun onCreate() {
        super.onCreate()

        /*
         * OCR / Parser / memória da oferta.
         */
        leitorTela =
            LeitorTela(
                this
            )

        /*
         * Localização contínua do piloto.
         */
        gerenciadorLocalizacao =
            GerenciadorLocalizacao(
                this
            )

        criarCanalNotificacao()

        /*
         * O serviço entra em foreground antes de ativar
         * qualquer recurso sensível de background.
         */
        iniciarForeground()

        observarEstadoGarupa()

        /*
         * Mantém o processamento ativo mesmo com a tela
         * apagada durante o turno.
         */
        adquirirWakeLock()

        /*
         * O Ouvido passa a pertencer ao ciclo de vida
         * do Foreground Service. Assim ele não depende
         * da MainActivity permanecer visível.
         */
        iniciarOuvidoContinua()

        handlerThread =
            HandlerThread(
                "GarupaCapturaThread"
            )

        handlerThread.start()

        handler =
            Handler(
                handlerThread.looper
            )

        Log.d(
            "GARUPA_CAPTURA",
            "📸 Serviço de captura iniciado"
        )

        /*
         * A partir daqui o ponto A passa
         * a ser acompanhado pelo serviço,
         * e não depende mais da MainActivity
         * permanecer visível.
         */
        iniciarLocalizacaoContinua()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val resultCode =
            intent?.getIntExtra(
                EXTRA_RESULT_CODE,
                Activity.RESULT_CANCELED
            ) ?: Activity.RESULT_CANCELED

        val resultData: Intent? =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {

                intent?.getParcelableExtra(
                    EXTRA_RESULT_DATA,
                    Intent::class.java
                )

            } else {

                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(
                    EXTRA_RESULT_DATA
                )
            }

        if (
            resultCode != Activity.RESULT_OK ||
            resultData == null
        ) {

            Log.d(
                "GARUPA_CAPTURA",
                "⚠️ Serviço iniciado sem autorização válida"
            )

            return START_NOT_STICKY
        }

        if (mediaProjection != null) {

            Log.d(
                "GARUPA_CAPTURA",
                "ℹ️ Já existe uma sessão de captura ativa"
            )

            return START_NOT_STICKY
        }

        iniciarNovaSessao(
            resultCode =
                resultCode,

            resultData =
                resultData
        )

        return START_NOT_STICKY
    }

    /*
     * =========================================================
     * OUVIDO CONTÍNUO
     * =========================================================
     *
     * O SpeechRecognizer continua pertencendo ao objeto Garupa,
     * mas o Foreground Service mantém sua execução autorizada
     * enquanto o app estiver trabalhando em background.
     */
    private fun iniciarOuvidoContinua() {

        try {

            Log.d(
                "GARUPA_OUVIDO",
                "🎧 Foreground Service mantendo Ouvido ativo"
            )

            Garupa.iniciarEscuta()

        } catch (erro: Exception) {

            Log.e(
                "GARUPA_OUVIDO",
                "❌ Erro ao iniciar Ouvido no Foreground Service",
                erro
            )
        }
    }

    private fun pararOuvidoContinua() {

        try {

            Garupa.pararEscuta()

            Log.d(
                "GARUPA_OUVIDO",
                "🔇 Foreground Service parou o Ouvido"
            )

        } catch (erro: Exception) {

            Log.e(
                "GARUPA_OUVIDO",
                "❌ Erro ao parar Ouvido no Foreground Service",
                erro
            )
        }
    }

    /*
     * =========================================================
     * WAKE LOCK
     * =========================================================
     */

    private fun adquirirWakeLock() {

        if (
            wakeLock?.isHeld ==
            true
        ) {
            return
        }

        try {

            val powerManager =
                getSystemService(
                    POWER_SERVICE
                ) as PowerManager

            wakeLock =
                powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Garupa:TurnoAtivo"
                ).apply {

                    setReferenceCounted(
                        false
                    )

                    acquire()
                }

            Log.d(
                "GARUPA_SERVICO",
                "🔋 WakeLock adquirido para operação com tela apagada"
            )

        } catch (erro: Exception) {

            Log.e(
                "GARUPA_SERVICO",
                "❌ Erro ao adquirir WakeLock",
                erro
            )
        }
    }

    private fun liberarWakeLock() {

        try {

            if (
                wakeLock?.isHeld ==
                true
            ) {

                wakeLock?.release()
            }

        } catch (erro: Exception) {

            Log.e(
                "GARUPA_SERVICO",
                "⚠️ Erro ao liberar WakeLock",
                erro
            )

        } finally {

            wakeLock =
                null
        }
    }

    /*
     * =========================================================
     * LOCALIZAÇÃO CONTÍNUA DO PILOTO
     * =========================================================
     */

    private fun iniciarLocalizacaoContinua() {

        try {

            Log.d(
                "GARUPA_LOCALIZACAO",
                "🏍️ Foreground Service ativando localização contínua"
            )

            gerenciadorLocalizacao
                .iniciarAtualizacoes()

        } catch (erro: Exception) {

            Log.e(
                "GARUPA_LOCALIZACAO",
                "❌ Erro ao iniciar localização no serviço",
                erro
            )
        }
    }

    private fun pararLocalizacaoContinua() {

        if (
            !::gerenciadorLocalizacao.isInitialized
        ) {

            return
        }

        try {

            gerenciadorLocalizacao
                .pararAtualizacoes()

            Log.d(
                "GARUPA_LOCALIZACAO",
                "🔴 Foreground Service parou localização contínua"
            )

        } catch (erro: Exception) {

            Log.e(
                "GARUPA_LOCALIZACAO",
                "❌ Erro ao parar localização no serviço",
                erro
            )
        }
    }

    /*
     * =========================================================
     * MEDIA PROJECTION
     * =========================================================
     */

    private fun iniciarNovaSessao(
        resultCode: Int,
        resultData: Intent
    ) {

        try {

            val manager =
                getSystemService(
                    MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager

            mediaProjection =
                manager.getMediaProjection(
                    resultCode,
                    resultData
                )

            mediaProjection
                ?.registerCallback(
                    projectionCallback,
                    handler
                )

            capturaAtiva = true
            precisaNovaAutorizacao = false

            ultimoFrameProcessado = 0L

            Log.d(
                "GARUPA_CAPTURA",
                "✅ Nova sessão MediaProjection criada"
            )

            iniciarCaptura()

        } catch (erro: Exception) {

            capturaAtiva = false
            precisaNovaAutorizacao = true

            Log.e(
                "GARUPA_CAPTURA",
                "❌ Erro ao iniciar MediaProjection",
                erro
            )
        }
    }

    private fun iniciarCaptura() {

        val metricas =
            resources.displayMetrics

        val largura =
            metricas.widthPixels

        val altura =
            metricas.heightPixels

        val densidade =
            resources.configuration.densityDpi

        liberarImageReaderEVirtualDisplay()

        imageReader =
            ImageReader.newInstance(
                largura,
                altura,
                PixelFormat.RGBA_8888,
                2
            )

        imageReader
            ?.setOnImageAvailableListener(
                { reader ->

                    if (!capturaAtiva) {

                        reader
                            .acquireLatestImage()
                            ?.close()

                        return@setOnImageAvailableListener
                    }

                    val agora =
                        System.currentTimeMillis()

                    if (
                        agora -
                        ultimoFrameProcessado <
                        INTERVALO_CAPTURA_MS
                    ) {

                        reader
                            .acquireLatestImage()
                            ?.close()

                        return@setOnImageAvailableListener
                    }

                    val image =
                        reader.acquireLatestImage()
                            ?: return@setOnImageAvailableListener

                    ultimoFrameProcessado =
                        agora

                    try {

                        val plane =
                            image.planes[0]

                        val buffer =
                            plane.buffer

                        val pixelStride =
                            plane.pixelStride

                        val rowStride =
                            plane.rowStride

                        val rowPadding =
                            rowStride -
                                    pixelStride *
                                    largura

                        val bitmapCompleto =
                            Bitmap.createBitmap(
                                largura +
                                        rowPadding /
                                        pixelStride,
                                altura,
                                Bitmap.Config.ARGB_8888
                            )

                        bitmapCompleto
                            .copyPixelsFromBuffer(
                                buffer
                            )

                        val bitmapFinal =
                            Bitmap.createBitmap(
                                bitmapCompleto,
                                0,
                                0,
                                largura,
                                altura
                            )

                        bitmapCompleto.recycle()

                        salvarFrame(
                            bitmapFinal
                        )

                        bitmapFinal.recycle()

                    } catch (erro: Exception) {

                        Log.e(
                            "GARUPA_CAPTURA",
                            "❌ Erro ao processar frame",
                            erro
                        )

                    } finally {

                        image.close()
                    }

                },
                handler
            )

        try {

            virtualDisplay =
                mediaProjection
                    ?.createVirtualDisplay(
                        "GarupaCaptura",
                        largura,
                        altura,
                        densidade,
                        DisplayManager
                            .VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader?.surface,
                        null,
                        handler
                    )

            if (
                virtualDisplay != null
            ) {

                Log.d(
                    "GARUPA_CAPTURA",
                    "📸 Captura contínua iniciada"
                )

            } else {

                capturaAtiva = false
                precisaNovaAutorizacao = true

                Log.e(
                    "GARUPA_CAPTURA",
                    "❌ VirtualDisplay não foi criado"
                )
            }

        } catch (erro: Exception) {

            capturaAtiva = false
            precisaNovaAutorizacao = true

            Log.e(
                "GARUPA_CAPTURA",
                "❌ Erro ao criar VirtualDisplay",
                erro
            )
        }
    }

    private fun salvarFrame(
        bitmap: Bitmap
    ) {

        val arquivo =
            File(
                cacheDir,
                "garupa_tela.png"
            )

        FileOutputStream(
            arquivo
        ).use { output ->

            bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                output
            )
        }

        Log.d(
            "GARUPA_CAPTURA",
            "📸 Novo frame capturado"
        )

        leitorTela.lerImagem(
            arquivo.absolutePath
        )
    }

    /*
     * =========================================================
     * LIBERAÇÃO DE RECURSOS
     * =========================================================
     */

    private fun liberarImageReaderEVirtualDisplay() {

        try {

            virtualDisplay?.release()

        } catch (_: Exception) {
        }

        virtualDisplay = null

        try {

            imageReader?.close()

        } catch (_: Exception) {
        }

        imageReader = null
    }

    private fun liberarRecursosCaptura(
        liberarProjection: Boolean
    ) {

        liberarImageReaderEVirtualDisplay()

        if (
            liberarProjection
        ) {

            try {

                mediaProjection
                    ?.unregisterCallback(
                        projectionCallback
                    )

            } catch (_: Exception) {
            }

            try {

                mediaProjection
                    ?.stop()

            } catch (_: Exception) {
            }
        }

        mediaProjection =
            null
    }

    /*
     * =========================================================
     * FOREGROUND SERVICE
     * =========================================================
     */

    private fun iniciarForeground() {

        val notificacao =
            criarNotificacao(
                GarupaEstado
                    .estadoOperacional
                    .value
                    .textoTela
            )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.R
        ) {

            /*
             * Android 11+:
             *
             * - mediaProjection
             * - location
             * - microphone
             *
             * O tipo microphone é o que permite ao Garupa
             * continuar usando o microfone quando a tela
             * apagar e a Activity deixar de estar visível.
             */
            val tipos =
                ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                        ServiceInfo
                            .FOREGROUND_SERVICE_TYPE_LOCATION or
                        ServiceInfo
                            .FOREGROUND_SERVICE_TYPE_MICROPHONE

            startForeground(
                NOTIFICATION_ID,
                notificacao,
                tipos
            )

        } else if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

            /*
             * API 29 não possui o tipo MICROPHONE.
             */
            val tipos =
                ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                        ServiceInfo
                            .FOREGROUND_SERVICE_TYPE_LOCATION

            startForeground(
                NOTIFICATION_ID,
                notificacao,
                tipos
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notificacao
            )
        }
    }

    private fun criarNotificacao(
        textoEstado: String
    ) =
        NotificationCompat
            .Builder(
                this,
                CHANNEL_ID
            )
            .setContentTitle(
                "Garupa ativo"
            )
            .setContentText(
                textoEstado
            )
            .setSmallIcon(
                android.R.drawable.ic_menu_mylocation
            )
            .setOngoing(
                true
            )
            .setOnlyAlertOnce(
                true
            )
            .build()

    private fun observarEstadoGarupa() {

        trabalhoEstado
            ?.cancel()

        trabalhoEstado =
            escopoServico.launch {

                GarupaEstado
                    .estadoOperacional
                    .collectLatest { estado ->

                        atualizarNotificacaoEstado(
                            estado.textoTela
                        )
                    }
            }
    }

    private fun atualizarNotificacaoEstado(
        textoEstado: String
    ) {

        try {

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.notify(
                NOTIFICATION_ID,
                criarNotificacao(
                    textoEstado
                )
            )

            Log.d(
                "GARUPA_NOTIFICACAO",
                "📌 Estado na notificação: $textoEstado"
            )

        } catch (erro: Exception) {

            Log.e(
                "GARUPA_NOTIFICACAO",
                "❌ Erro ao atualizar estado da notificação",
                erro
            )
        }
    }

    private fun criarCanalNotificacao() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val canal =
                NotificationChannel(
                    CHANNEL_ID,
                    "Garupa ativo",
                    NotificationManager
                        .IMPORTANCE_LOW
                )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager
                .createNotificationChannel(
                    canal
                )
        }
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }

    override fun onDestroy() {

        capturaAtiva =
            false

        /*
         * O serviço é dono do ciclo de vida das capacidades
         * que precisam continuar funcionando em background.
         */
        pararOuvidoContinua()

        pararLocalizacaoContinua()

        liberarWakeLock()

        liberarRecursosCaptura(
            liberarProjection =
                true
        )

        if (
            ::handlerThread.isInitialized
        ) {

            handlerThread.quitSafely()
        }

        trabalhoEstado
            ?.cancel()

        escopoServico.cancel()

        Log.d(
            "GARUPA_CAPTURA",
            "📴 Foreground Service destruído"
        )

        super.onDestroy()
    }
}