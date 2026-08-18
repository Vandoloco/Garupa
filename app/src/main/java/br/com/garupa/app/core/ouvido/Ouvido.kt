package br.com.garupa.app.core.ouvido

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat

class Ouvido(
    contexto: Context
) {

    private val contextoAplicacao =
        contexto.applicationContext

    /*
     * SpeechRecognizer exige a MAIN THREAD.
     *
     * Tudo que inicia, pausa, cancela ou destrói
     * o reconhecimento passa por este Handler.
     */
    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private val audioManager =
        contextoAplicacao.getSystemService(
            AudioManager::class.java
        )

    private var reconhecedor:
            SpeechRecognizer? =
        null

    @Volatile
    private var escutando =
        false

    @Volatile
    private var deveContinuarEscutando =
        false

    private var callbackAudioRegistrado =
        false

    private var aoReconhecerFala:
            ((String) -> Unit)? =
        null

    /*
     * =========================================================
     * MONITOR DE DISPOSITIVOS DE ÁUDIO
     * =========================================================
     *
     * Se o intercom conectar/desconectar ou o Android
     * alterar a rota, tentamos recuperar o Bluetooth.
     */
    private val callbackAudio =
        object :
            AudioDeviceCallback() {

            override fun onAudioDevicesAdded(
                addedDevices: Array<out AudioDeviceInfo>
            ) {

                Log.d(
                    "GARUPA_BLUETOOTH",
                    "🎧 Dispositivo de áudio conectado"
                )

                handler.postDelayed(
                    {
                        garantirRotaIntercom()
                    },
                    500L
                )
            }

            override fun onAudioDevicesRemoved(
                removedDevices: Array<out AudioDeviceInfo>
            ) {

                Log.d(
                    "GARUPA_BLUETOOTH",
                    "🎧 Dispositivo de áudio removido"
                )

                handler.postDelayed(
                    {
                        garantirRotaIntercom()
                    },
                    500L
                )
            }
        }

    fun definirAoReconhecerFala(
        callback: (String) -> Unit
    ) {

        aoReconhecerFala =
            callback
    }

    /*
     * =========================================================
     * INICIALIZAÇÃO
     * =========================================================
     */

    fun iniciar() {

        /*
         * Garante execução na MAIN THREAD.
         */
        if (
            Looper.myLooper() !=
            Looper.getMainLooper()
        ) {

            handler.post {
                iniciar()
            }

            return
        }

        if (
            reconhecedor != null
        ) {
            return
        }

        if (
            !SpeechRecognizer.isRecognitionAvailable(
                contextoAplicacao
            )
        ) {

            Log.e(
                "GARUPA_OUVIDO",
                "❌ Reconhecimento de voz indisponível neste aparelho"
            )

            return
        }

        /*
         * O Android passa a tratar a sessão como
         * comunicação de voz.
         */
        audioManager.mode =
            AudioManager.MODE_IN_COMMUNICATION

        registrarMonitorAudio()

        garantirRotaIntercom()

        reconhecedor =
            SpeechRecognizer.createSpeechRecognizer(
                contextoAplicacao
            )

        reconhecedor
            ?.setRecognitionListener(
                criarListener()
            )

        Log.d(
            "GARUPA_OUVIDO",
            "🎤 Ouvido inicializado"
        )
    }

    /*
     * =========================================================
     * ESCUTA CONTÍNUA
     * =========================================================
     */

    fun comecarEscutaContinua() {

        /*
         * IMPORTANTE:
         *
         * Este método pode ser chamado pelo callback do TTS,
         * que não necessariamente roda na MAIN THREAD.
         *
         * SpeechRecognizer.startListening() só pode acontecer
         * na thread principal.
         */
        handler.post {

            if (
                reconhecedor == null
            ) {

                iniciar()
            }

            if (
                reconhecedor == null
            ) {

                Log.e(
                    "GARUPA_OUVIDO",
                    "❌ Não foi possível iniciar reconhecimento"
                )

                return@post
            }

            deveContinuarEscutando =
                true

            garantirRotaIntercom()

            iniciarNovaEscuta()
        }
    }

    fun pararEscuta() {

        /*
         * Também precisa ocorrer na MAIN THREAD.
         */
        handler.post {

            deveContinuarEscutando =
                false

            escutando =
                false

            /*
             * Não usamos:
             *
             * removeCallbacksAndMessages(null)
             *
             * aqui.
             *
             * Isso poderia apagar callbacks importantes,
             * inclusive a retomada do Ouvido depois do TTS.
             */
            try {

                reconhecedor
                    ?.stopListening()

            } catch (
                erro: Exception
            ) {

                Log.e(
                    "GARUPA_OUVIDO",
                    "⚠️ Erro ao pausar reconhecimento",
                    erro
                )
            }

            liberarRotaComunicacao()

            Log.d(
                "GARUPA_OUVIDO",
                "🔇 Escuta pausada"
            )
        }
    }

    /*
     * =========================================================
     * ROTA BLUETOOTH / INTERCOM
     * =========================================================
     */

    private fun garantirRotaIntercom() {

        try {

            audioManager.mode =
                AudioManager.MODE_IN_COMMUNICATION

            /*
             * Android 12+
             *
             * API moderna para selecionar dispositivo
             * de comunicação.
             */
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S
            ) {

                if (
                    !temPermissaoBluetooth()
                ) {

                    Log.d(
                        "GARUPA_BLUETOOTH",
                        "⚠️ BLUETOOTH_CONNECT ainda não autorizado"
                    )

                    return
                }

                val dispositivos =
                    audioManager
                        .availableCommunicationDevices

                /*
                 * Intercomunicadores normalmente aparecem
                 * como Bluetooth SCO/HFP.
                 *
                 * BLE_HEADSET fica como segunda opção.
                 */
                val intercom =
                    dispositivos
                        .firstOrNull { dispositivo ->

                            dispositivo.type ==
                                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        }
                        ?: dispositivos
                            .firstOrNull { dispositivo ->

                                dispositivo.type ==
                                        AudioDeviceInfo.TYPE_BLE_HEADSET
                            }

                if (
                    intercom != null
                ) {

                    val atual =
                        audioManager.communicationDevice

                    /*
                     * Se o intercom já estiver selecionado,
                     * não precisamos forçar a rota novamente.
                     */
                    if (
                        atual?.id ==
                        intercom.id
                    ) {

                        Log.v(
                            "GARUPA_BLUETOOTH",
                            "🎧 Intercom já está selecionado"
                        )

                        return
                    }

                    val selecionado =
                        audioManager
                            .setCommunicationDevice(
                                intercom
                            )

                    if (
                        selecionado
                    ) {

                        Log.d(
                            "GARUPA_BLUETOOTH",
                            "✅ Intercom selecionado | " +
                                    "tipo=${intercom.type} | " +
                                    "id=${intercom.id}"
                        )

                    } else {

                        Log.d(
                            "GARUPA_BLUETOOTH",
                            "⚠️ Android recusou seleção do intercom"
                        )
                    }

                } else {

                    Log.d(
                        "GARUPA_BLUETOOTH",
                        "ℹ️ Nenhum intercom disponível; usando microfone padrão"
                    )
                }

                return
            }

            /*
             * Android 10 e 11.
             */
            @Suppress("DEPRECATION")
            audioManager.startBluetoothSco()

            @Suppress("DEPRECATION")
            run {

                audioManager.isBluetoothScoOn =
                    true
            }

            Log.d(
                "GARUPA_BLUETOOTH",
                "🎧 Bluetooth SCO solicitado"
            )

        } catch (
            erro: SecurityException
        ) {

            Log.e(
                "GARUPA_BLUETOOTH",
                "❌ Sem permissão para controlar Bluetooth",
                erro
            )

        } catch (
            erro: Exception
        ) {

            Log.e(
                "GARUPA_BLUETOOTH",
                "❌ Erro ao selecionar intercom",
                erro
            )
        }
    }

    private fun liberarRotaComunicacao() {

        try {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S
            ) {

                audioManager
                    .clearCommunicationDevice()

            } else {

                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()

                @Suppress("DEPRECATION")
                run {

                    audioManager.isBluetoothScoOn =
                        false
                }
            }

        } catch (
            erro: Exception
        ) {

            Log.e(
                "GARUPA_BLUETOOTH",
                "⚠️ Erro ao liberar rota de comunicação",
                erro
            )
        }
    }

    private fun temPermissaoBluetooth():
            Boolean {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.S
        ) {

            return true
        }

        return ContextCompat
            .checkSelfPermission(
                contextoAplicacao,
                Manifest.permission.BLUETOOTH_CONNECT
            ) ==
                PackageManager.PERMISSION_GRANTED
    }

    /*
     * =========================================================
     * MONITORAMENTO DOS DISPOSITIVOS
     * =========================================================
     */

    private fun registrarMonitorAudio() {

        if (
            callbackAudioRegistrado
        ) {
            return
        }

        try {

            audioManager
                .registerAudioDeviceCallback(
                    callbackAudio,
                    handler
                )

            callbackAudioRegistrado =
                true

            Log.d(
                "GARUPA_BLUETOOTH",
                "👀 Monitor de dispositivos de áudio ativo"
            )

        } catch (
            erro: Exception
        ) {

            Log.e(
                "GARUPA_BLUETOOTH",
                "❌ Falha ao registrar monitor de áudio",
                erro
            )
        }
    }

    private fun removerMonitorAudio() {

        if (
            !callbackAudioRegistrado
        ) {
            return
        }

        try {

            audioManager
                .unregisterAudioDeviceCallback(
                    callbackAudio
                )

        } catch (
            erro: Exception
        ) {

            Log.e(
                "GARUPA_BLUETOOTH",
                "⚠️ Erro ao remover monitor de áudio",
                erro
            )
        }

        callbackAudioRegistrado =
            false
    }

    /*
     * =========================================================
     * NOVA JANELA DE ESCUTA
     * =========================================================
     */

    private fun iniciarNovaEscuta() {

        /*
         * Segurança extra:
         * nunca manipulamos SpeechRecognizer fora da main.
         */
        if (
            Looper.myLooper() !=
            Looper.getMainLooper()
        ) {

            handler.post {
                iniciarNovaEscuta()
            }

            return
        }

        if (
            !deveContinuarEscutando ||
            escutando
        ) {

            return
        }

        /*
         * A cada nova janela de reconhecimento,
         * reforçamos a rota do intercom.
         */
        garantirRotaIntercom()

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "pt-BR"
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "pt-BR"
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )
            }

        try {

            escutando =
                true

            reconhecedor
                ?.startListening(
                    intent
                )

            Log.d(
                "GARUPA_OUVIDO",
                "👂 Escutando..."
            )

        } catch (
            erro: Exception
        ) {

            escutando =
                false

            Log.e(
                "GARUPA_OUVIDO",
                "❌ Erro ao iniciar escuta",
                erro
            )

            agendarNovaEscuta(
                1500L
            )
        }
    }

    /*
     * =========================================================
     * SPEECH RECOGNIZER
     * =========================================================
     */

    private fun criarListener():
            RecognitionListener {

        return object :
            RecognitionListener {

            override fun onReadyForSpeech(
                params: Bundle?
            ) {

                Log.d(
                    "GARUPA_OUVIDO",
                    "🎙️ Pode falar"
                )

                logarRotaAtual()
            }

            override fun onBeginningOfSpeech() {

                Log.d(
                    "GARUPA_OUVIDO",
                    "🗣️ Fala detectada"
                )
            }

            override fun onRmsChanged(
                rmsdB: Float
            ) {

                /*
                 * Não logamos continuamente para
                 * não poluir o Logcat.
                 */
            }

            override fun onBufferReceived(
                buffer: ByteArray?
            ) {
            }

            override fun onEndOfSpeech() {

                Log.d(
                    "GARUPA_OUVIDO",
                    "🤫 Fim da fala detectado"
                )
            }

            override fun onError(
                error: Int
            ) {

                escutando =
                    false

                /*
                 * Timeout e NO_MATCH são normais
                 * na escuta contínua.
                 */
                if (
                    error !=
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                    error !=
                    SpeechRecognizer.ERROR_NO_MATCH
                ) {

                    Log.d(
                        "GARUPA_OUVIDO",
                        "⚠️ Reconhecimento retornou código=$error"
                    )
                }

                if (
                    error ==
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                ) {

                    deveContinuarEscutando =
                        false

                    Log.e(
                        "GARUPA_OUVIDO",
                        "❌ Sem permissão para usar o microfone"
                    )

                    return
                }

                /*
                 * O Android pode ter alterado a rota Bluetooth.
                 * Reforçamos antes da próxima escuta.
                 */
                garantirRotaIntercom()

                agendarNovaEscuta(
                    700L
                )
            }

            override fun onResults(
                results: Bundle?
            ) {

                escutando =
                    false

                val textos =
                    results
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        .orEmpty()

                val frase =
                    textos
                        .firstOrNull()
                        ?.trim()

                if (
                    !frase.isNullOrBlank()
                ) {

                    Log.d(
                        "GARUPA_OUVIDO",
                        "🧠 Você disse: $frase"
                    )

                    aoReconhecerFala
                        ?.invoke(
                            frase
                        )
                }

                agendarNovaEscuta(
                    500L
                )
            }

            override fun onPartialResults(
                partialResults: Bundle?
            ) {

                val parcial =
                    partialResults
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        ?.firstOrNull()
                        ?.trim()

                if (
                    !parcial.isNullOrBlank()
                ) {

                    Log.v(
                        "GARUPA_OUVIDO_PARCIAL",
                        "… $parcial"
                    )
                }
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?
            ) {
            }
        }
    }

    /*
     * =========================================================
     * DIAGNÓSTICO DA ROTA
     * =========================================================
     */

    private fun logarRotaAtual() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.S
        ) {
            return
        }

        try {

            val atual =
                audioManager.communicationDevice

            if (
                atual != null
            ) {

                Log.d(
                    "GARUPA_BLUETOOTH",
                    "🎙️ Rota atual | " +
                            "tipo=${atual.type} | " +
                            "id=${atual.id}"
                )

            } else {

                Log.d(
                    "GARUPA_BLUETOOTH",
                    "🎙️ Nenhum dispositivo de comunicação explicitamente selecionado"
                )
            }

        } catch (
            erro: Exception
        ) {

            Log.e(
                "GARUPA_BLUETOOTH",
                "⚠️ Não foi possível consultar rota atual",
                erro
            )
        }
    }

    /*
     * =========================================================
     * REINÍCIO AUTOMÁTICO
     * =========================================================
     */

    private fun agendarNovaEscuta(
        atrasoMs: Long
    ) {

        if (
            !deveContinuarEscutando
        ) {
            return
        }

        handler.postDelayed(
            {

                if (
                    deveContinuarEscutando
                ) {

                    iniciarNovaEscuta()
                }

            },
            atrasoMs
        )
    }

    /*
     * =========================================================
     * ENCERRAMENTO
     * =========================================================
     */

    fun encerrar() {

        /*
         * cancel(), destroy() e mudanças de rota
         * também devem ocorrer na MAIN THREAD.
         */
        handler.post {

            deveContinuarEscutando =
                false

            escutando =
                false

            handler.removeCallbacksAndMessages(
                null
            )

            try {

                reconhecedor
                    ?.cancel()

            } catch (
                erro: Exception
            ) {

                Log.e(
                    "GARUPA_OUVIDO",
                    "⚠️ Erro ao cancelar reconhecimento",
                    erro
                )
            }

            try {

                reconhecedor
                    ?.destroy()

            } catch (
                erro: Exception
            ) {

                Log.e(
                    "GARUPA_OUVIDO",
                    "⚠️ Erro ao destruir reconhecimento",
                    erro
                )
            }

            reconhecedor =
                null

            removerMonitorAudio()

            liberarRotaComunicacao()

            audioManager.mode =
                AudioManager.MODE_NORMAL

            Log.d(
                "GARUPA_OUVIDO",
                "🎤 Ouvido encerrado"
            )
        }
    }
}