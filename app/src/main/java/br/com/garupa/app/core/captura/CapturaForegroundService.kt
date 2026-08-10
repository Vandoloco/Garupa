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
import java.io.File
import java.io.FileOutputStream

class CapturaForegroundService : Service() {

    companion object {

        const val CHANNEL_ID = "garupa_captura"
        const val NOTIFICATION_ID = 1001

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        // Intervalo inicial para testes.
        const val INTERVALO_CAPTURA_MS = 1200L
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var leitorTela: LeitorTela

    private var ultimoFrameProcessado = 0L

    private val projectionCallback =
        object : MediaProjection.Callback() {

            override fun onStop() {

                Log.d(
                    "GARUPA",
                    "📸 Captura de tela encerrada"
                )

                liberarCaptura()
            }
        }

    override fun onCreate() {
        super.onCreate()

        leitorTela = LeitorTela(this)

        criarCanalNotificacao()

        val notificacao =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Garupa ativo")
                .setContentText("Observando a tela para analisar pedidos")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startForeground(
                NOTIFICATION_ID,
                notificacao,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notificacao
            )
        }

        handlerThread =
            HandlerThread("GarupaCapturaThread")

        handlerThread.start()

        handler =
            Handler(handlerThread.looper)

        Log.d(
            "GARUPA",
            "📸 Serviço de captura iniciado"
        )
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

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
            resultCode == Activity.RESULT_OK &&
            resultData != null &&
            mediaProjection == null
        ) {

            val manager =
                getSystemService(MEDIA_PROJECTION_SERVICE)
                        as MediaProjectionManager

            mediaProjection =
                manager.getMediaProjection(
                    resultCode,
                    resultData
                )

            mediaProjection?.registerCallback(
                projectionCallback,
                handler
            )

            Log.d(
                "GARUPA",
                "📸 MediaProjection criado com sucesso"
            )

            iniciarCaptura()
        }

        return START_NOT_STICKY
    }

    private fun iniciarCaptura() {

        val metricas = resources.displayMetrics

        val largura = metricas.widthPixels
        val altura = metricas.heightPixels
        val densidade = resources.configuration.densityDpi

        imageReader =
            ImageReader.newInstance(
                largura,
                altura,
                PixelFormat.RGBA_8888,
                2
            )

        imageReader?.setOnImageAvailableListener(
            { reader ->

                val agora = System.currentTimeMillis()

                if (
                    agora - ultimoFrameProcessado <
                    INTERVALO_CAPTURA_MS
                ) {

                    val imagemIgnorada =
                        reader.acquireLatestImage()

                    imagemIgnorada?.close()

                    return@setOnImageAvailableListener
                }

                val image =
                    reader.acquireLatestImage()
                        ?: return@setOnImageAvailableListener

                ultimoFrameProcessado = agora

                try {

                    val plane = image.planes[0]
                    val buffer = plane.buffer

                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride

                    val rowPadding =
                        rowStride - pixelStride * largura

                    val bitmapCompleto =
                        Bitmap.createBitmap(
                            largura + rowPadding / pixelStride,
                            altura,
                            Bitmap.Config.ARGB_8888
                        )

                    bitmapCompleto.copyPixelsFromBuffer(
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

                    salvarFrame(bitmapFinal)

                    bitmapFinal.recycle()

                } catch (e: Exception) {

                    Log.e(
                        "GARUPA",
                        "📸 Erro ao capturar frame",
                        e
                    )

                } finally {

                    image.close()
                }

            },
            handler
        )

        virtualDisplay =
            mediaProjection?.createVirtualDisplay(
                "GarupaCaptura",
                largura,
                altura,
                densidade,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                handler
            )

        Log.d(
            "GARUPA",
            "📸 Captura contínua iniciada"
        )
    }

    private fun salvarFrame(
        bitmap: Bitmap
    ) {

        val arquivo =
            File(
                cacheDir,
                "garupa_tela.png"
            )

        FileOutputStream(arquivo).use { output ->

            bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                output
            )
        }

        Log.d(
            "GARUPA",
            "📸 Novo frame capturado"
        )

        leitorTela.lerImagem(
            arquivo.absolutePath
        )
    }

    private fun liberarCaptura() {

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        mediaProjection = null
    }

    private fun criarCanalNotificacao() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val canal =
                NotificationChannel(
                    CHANNEL_ID,
                    "Captura de tela do Garupa",
                    NotificationManager.IMPORTANCE_LOW
                )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
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

        mediaProjection?.stop()

        liberarCaptura()

        if (::handlerThread.isInitialized) {
            handlerThread.quitSafely()
        }

        super.onDestroy()
    }
}