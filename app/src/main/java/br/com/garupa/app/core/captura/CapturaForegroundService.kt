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
import android.util.Log
import androidx.core.app.NotificationCompat
import br.com.garupa.app.core.leitura.LeitorTela
import br.com.garupa.app.core.localizacao.GerenciadorLocalizacao
import java.io.File
import java.io.FileOutputStream

class CapturaForegroundService : Service() {

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

        iniciarForeground()

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
            NotificationCompat
                .Builder(
                    this,
                    CHANNEL_ID
                )
                .setContentTitle(
                    "Garupa ativo"
                )
                .setContentText(
                    "Analisando ofertas e acompanhando sua posição"
                )
                .setSmallIcon(
                    android.R.drawable.ic_menu_mylocation
                )
                .setOngoing(
                    true
                )
                .build()

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

            /*
             * O mesmo serviço agora declara:
             *
             * - mediaProjection
             * - location
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
         * Agora o serviço é dono também
         * da atualização de A.
         */
        pararLocalizacaoContinua()

        liberarRecursosCaptura(
            liberarProjection =
                true
        )

        if (
            ::handlerThread.isInitialized
        ) {

            handlerThread.quitSafely()
        }

        Log.d(
            "GARUPA_CAPTURA",
            "📴 Foreground Service destruído"
        )

        super.onDestroy()
    }
}