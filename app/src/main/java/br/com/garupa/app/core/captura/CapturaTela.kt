package br.com.garupa.app.core.captura

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log

class CapturaTela(private val contexto: Context) {

    private val mediaProjectionManager =
        contexto.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager

    private var mediaProjection: MediaProjection? = null

    fun criarPedidoPermissao(): Intent {

        Log.d(
            "GARUPA",
            "📸 Solicitando autorização para captura de tela"
        )

        return mediaProjectionManager.createScreenCaptureIntent()
    }

    fun permissaoConcedida(
        resultCode: Int
    ): Boolean {

        return resultCode == Activity.RESULT_OK
    }

    fun guardarAutorizacao(
        resultCode: Int,
        data: Intent?
    ) {

        if (
            resultCode == Activity.RESULT_OK &&
            data != null
        ) {

            mediaProjection =
                mediaProjectionManager.getMediaProjection(
                    resultCode,
                    data
                )

            Log.d(
                "GARUPA",
                "📸 Autorização de captura armazenada"
            )

        } else {

            Log.d(
                "GARUPA",
                "📸 Não foi possível armazenar a autorização"
            )
        }
    }

    fun capturaDisponivel(): Boolean {
        return mediaProjection != null
    }
}