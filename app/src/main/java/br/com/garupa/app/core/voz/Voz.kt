package br.com.garupa.app.core.voz

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class Voz(
    private val contexto: Context
) {

    private var tts: TextToSpeech? =
        null

    private val audioManager: AudioManager =
        contexto.applicationContext
            .getSystemService(
                Context.AUDIO_SERVICE
            ) as AudioManager

    @Volatile
    private var pronta =
        false

    /*
     * =========================================================
     * ESTADO DE FALA
     * =========================================================
     */

    @Volatile
    private var idFalaAtual:
            String? =
        null

    @Volatile
    private var decisaoFalando =
        false

    /*
     * Guarda callbacks associados a cada fala.
     *
     * Serve tanto para conversa quanto para
     * decisões operacionais.
     */
    private val callbacksFim =
        ConcurrentHashMap<String, () -> Unit>()

    /*
     * =========================================================
     * INICIALIZAÇÃO
     * =========================================================
     */

    fun iniciar() {

        if (
            tts != null
        ) {
            return
        }

        tts =
            TextToSpeech(
                contexto.applicationContext
            ) { status ->

                if (
                    status ==
                    TextToSpeech.SUCCESS
                ) {

                    configurarListener()

                    val resultadoIdioma =
                        tts?.setLanguage(
                            Locale(
                                "pt",
                                "BR"
                            )
                        )

                    pronta =
                        resultadoIdioma !=
                                TextToSpeech.LANG_MISSING_DATA &&
                                resultadoIdioma !=
                                TextToSpeech.LANG_NOT_SUPPORTED

                    if (
                        pronta
                    ) {

                        Log.d(
                            "GARUPA_VOZ",
                            "🔊 Voz pronta"
                        )

                        registrarRotaAudio(
                            momento =
                                "INICIALIZACAO"
                        )

                        falar(
                            "Garupa pronto para rodar."
                        )

                    } else {

                        Log.e(
                            "GARUPA_VOZ",
                            "❌ Português do Brasil indisponível no TTS"
                        )
                    }

                } else {

                    Log.e(
                        "GARUPA_VOZ",
                        "❌ Falha ao iniciar TextToSpeech"
                    )
                }
            }
    }

    /*
     * =========================================================
     * DIAGNÓSTICO DA ROTA DE ÁUDIO
     * =========================================================
     *
     * Apenas observa.
     *
     * Não força speaker, earpiece ou Bluetooth.
     */

    private fun registrarRotaAudio(
        momento: String
    ) {

        try {

            val modo =
                nomeModoAudio(
                    audioManager.mode
                )

            @Suppress("DEPRECATION")
            val speakerLigado =
                audioManager.isSpeakerphoneOn

            @Suppress("DEPRECATION")
            val bluetoothScoLigado =
                audioManager.isBluetoothScoOn

            Log.d(
                "GARUPA_VOZ_ROTA",
                "🔎 Rota de áudio | " +
                        "momento=$momento | " +
                        "modo=$modo | " +
                        "speakerphone=$speakerLigado | " +
                        "bluetoothSco=$bluetoothScoLigado"
            )

            /*
             * =================================================
             * DISPOSITIVO DE COMUNICAÇÃO
             * =================================================
             */

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S
            ) {

                val dispositivoComunicacao =
                    audioManager.communicationDevice

                if (
                    dispositivoComunicacao != null
                ) {

                    Log.d(
                        "GARUPA_VOZ_ROTA",
                        "📞 CommunicationDevice | " +
                                descreverDispositivo(
                                    dispositivoComunicacao
                                )
                    )

                } else {

                    Log.d(
                        "GARUPA_VOZ_ROTA",
                        "📞 CommunicationDevice | nenhum"
                    )
                }
            }

            /*
             * =================================================
             * SAÍDAS DISPONÍVEIS
             * =================================================
             */

            val saidas =
                audioManager.getDevices(
                    AudioManager.GET_DEVICES_OUTPUTS
                )

            if (
                saidas.isEmpty()
            ) {

                Log.d(
                    "GARUPA_VOZ_ROTA",
                    "🔈 Saídas disponíveis | nenhuma"
                )

            } else {

                saidas.forEachIndexed { indice, dispositivo ->

                    Log.d(
                        "GARUPA_VOZ_ROTA",
                        "🔈 Saída ${indice + 1}/${saidas.size} | " +
                                descreverDispositivo(
                                    dispositivo
                                )
                    )
                }
            }

        } catch (
            erro: Exception
        ) {

            Log.e(
                "GARUPA_VOZ_ROTA",
                "❌ Falha ao inspecionar rota de áudio",
                erro
            )
        }
    }

    private fun nomeModoAudio(
        modo: Int
    ): String {

        return when (
            modo
        ) {

            AudioManager.MODE_NORMAL ->
                "MODE_NORMAL"

            AudioManager.MODE_RINGTONE ->
                "MODE_RINGTONE"

            AudioManager.MODE_IN_CALL ->
                "MODE_IN_CALL"

            AudioManager.MODE_IN_COMMUNICATION ->
                "MODE_IN_COMMUNICATION"

            else ->
                "DESCONHECIDO($modo)"
        }
    }

    private fun descreverDispositivo(
        dispositivo: AudioDeviceInfo
    ): String {

        val tipo =
            nomeTipoDispositivo(
                dispositivo.type
            )

        val nome =
            dispositivo.productName
                .toString()
                .takeIf {
                    it.isNotBlank()
                }
                ?: "sem nome"

        return "id=${dispositivo.id} | " +
                "tipo=$tipo | " +
                "nome=$nome"
    }

    private fun nomeTipoDispositivo(
        tipo: Int
    ): String {

        return when (
            tipo
        ) {

            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ->
                "EARPIECE"

            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ->
                "SPEAKER"

            AudioDeviceInfo.TYPE_WIRED_HEADSET ->
                "WIRED_HEADSET"

            AudioDeviceInfo.TYPE_WIRED_HEADPHONES ->
                "WIRED_HEADPHONES"

            AudioDeviceInfo.TYPE_BLUETOOTH_SCO ->
                "BLUETOOTH_SCO"

            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ->
                "BLUETOOTH_A2DP"

            AudioDeviceInfo.TYPE_USB_DEVICE ->
                "USB_DEVICE"

            AudioDeviceInfo.TYPE_USB_HEADSET ->
                "USB_HEADSET"

            AudioDeviceInfo.TYPE_HEARING_AID ->
                "HEARING_AID"

            else -> {

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S &&
                    tipo ==
                    AudioDeviceInfo.TYPE_BLE_HEADSET
                ) {

                    "BLE_HEADSET"

                } else if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S &&
                    tipo ==
                    AudioDeviceInfo.TYPE_BLE_SPEAKER
                ) {

                    "BLE_SPEAKER"

                } else {

                    "TIPO_$tipo"
                }
            }
        }
    }

    /*
     * =========================================================
     * CALLBACK REAL DO TTS
     * =========================================================
     */

    private fun configurarListener() {

        tts?.setOnUtteranceProgressListener(

            object :
                UtteranceProgressListener() {

                override fun onStart(
                    utteranceId: String?
                ) {

                    if (
                        utteranceId == null
                    ) {
                        return
                    }

                    idFalaAtual =
                        utteranceId

                    decisaoFalando =
                        utteranceId.startsWith(
                            "GARUPA_DECISAO_"
                        )

                    Log.d(
                        "GARUPA_VOZ",
                        "▶️ Fala iniciada | id=$utteranceId"
                    )

                    registrarRotaAudio(
                        momento =
                            "TTS_ON_START_$utteranceId"
                    )
                }

                override fun onDone(
                    utteranceId: String?
                ) {

                    if (
                        utteranceId == null
                    ) {
                        return
                    }

                    Log.d(
                        "GARUPA_VOZ",
                        "✅ Fala concluída | id=$utteranceId"
                    )

                    limparEstadoDaFala(
                        utteranceId
                    )

                    executarCallbackFim(
                        utteranceId
                    )
                }

                @Deprecated(
                    "Método legado exigido pela interface Android"
                )
                override fun onError(
                    utteranceId: String?
                ) {

                    if (
                        utteranceId == null
                    ) {
                        return
                    }

                    Log.e(
                        "GARUPA_VOZ",
                        "❌ Erro durante fala | id=$utteranceId"
                    )

                    limparEstadoDaFala(
                        utteranceId
                    )

                    executarCallbackFim(
                        utteranceId
                    )
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int
                ) {

                    if (
                        utteranceId == null
                    ) {
                        return
                    }

                    Log.e(
                        "GARUPA_VOZ",
                        "❌ Erro durante fala | " +
                                "id=$utteranceId | " +
                                "codigo=$errorCode"
                    )

                    limparEstadoDaFala(
                        utteranceId
                    )

                    executarCallbackFim(
                        utteranceId
                    )
                }
            }
        )
    }

    private fun limparEstadoDaFala(
        utteranceId: String
    ) {

        if (
            idFalaAtual ==
            utteranceId
        ) {

            idFalaAtual =
                null
        }

        if (
            utteranceId.startsWith(
                "GARUPA_DECISAO_"
            )
        ) {

            decisaoFalando =
                false
        }
    }

    private fun executarCallbackFim(
        utteranceId: String
    ) {

        callbacksFim
            .remove(
                utteranceId
            )
            ?.invoke()
    }

    /*
     * =========================================================
     * CANCELAMENTO CONTROLADO
     * =========================================================
     */

    private fun interromperFalaAtualParaDecisao() {

        val idAtual =
            idFalaAtual

        if (
            idAtual == null
        ) {

            return
        }

        if (
            idAtual.startsWith(
                "GARUPA_DECISAO_"
            )
        ) {

            return
        }

        Log.d(
            "GARUPA_VOZ",
            "⏹️ Interrompendo conversa para anunciar decisão | id=$idAtual"
        )

        try {

            tts?.stop()

        } catch (_: Exception) {
        }

        idFalaAtual =
            null

        /*
         * Uma conversa interrompida precisa liberar
         * o estado de quem estava aguardando seu término.
         */
        executarCallbackFim(
            idAtual
        )
    }

    /*
     * =========================================================
     * DECISÕES OPERACIONAIS
     * =========================================================
     */

    fun anunciarAceitar(
        aoTerminar: (() -> Unit)? = null
    ) {

        falarDecisao(
            mensagem =
                "Aceitar.",

            aoTerminar =
                aoTerminar
        )
    }

    fun anunciarDeixarPassar(
        aoTerminar: (() -> Unit)? = null
    ) {

        falarDecisao(
            mensagem =
                "Deixa passar.",

            aoTerminar =
                aoTerminar
        )
    }

    private fun falarDecisao(
        mensagem: String,
        aoTerminar: (() -> Unit)? = null
    ) {

        val mensagemLimpa =
            mensagem.trim()

        if (
            mensagemLimpa.isBlank()
        ) {

            aoTerminar
                ?.invoke()

            return
        }

        if (
            !pronta
        ) {

            Log.d(
                "GARUPA_VOZ",
                "⚠️ Voz ainda não está pronta"
            )

            aoTerminar
                ?.invoke()

            return
        }

        /*
         * Outra decisão operacional ainda está sendo falada.
         *
         * Não acumulamos decisões.
         */
        if (
            decisaoFalando
        ) {

            Log.d(
                "GARUPA_VOZ",
                "⏳ Decisão ignorada: outra decisão já está sendo falada"
            )

            aoTerminar
                ?.invoke()

            return
        }

        /*
         * Decisão operacional continua tendo prioridade
         * sobre conversa normal.
         */
        interromperFalaAtualParaDecisao()

        val id =
            "GARUPA_DECISAO_${System.currentTimeMillis()}"

        if (
            aoTerminar != null
        ) {

            callbacksFim[
                id
            ] =
                aoTerminar
        }

        decisaoFalando =
            true

        idFalaAtual =
            id

        Log.d(
            "GARUPA_VOZ",
            "🔊 Decisão: $mensagemLimpa"
        )

        registrarRotaAudio(
            momento =
                "ANTES_DECISAO"
        )

        val resultado =
            tts?.speak(
                mensagemLimpa,
                TextToSpeech.QUEUE_FLUSH,
                null,
                id
            )

        if (
            resultado ==
            TextToSpeech.ERROR
        ) {

            Log.e(
                "GARUPA_VOZ",
                "❌ TTS recusou a decisão"
            )

            limparEstadoDaFala(
                id
            )

            executarCallbackFim(
                id
            )
        }
    }

    /*
     * =========================================================
     * CONVERSA NORMAL
     * =========================================================
     */

    fun falar(
        mensagem: String,
        aoTerminar: (() -> Unit)? = null
    ) {

        val mensagemLimpa =
            mensagem
                .trim()

        if (
            mensagemLimpa.isBlank()
        ) {

            aoTerminar
                ?.invoke()

            return
        }

        if (
            !pronta
        ) {

            Log.d(
                "GARUPA_VOZ",
                "⚠️ Voz ainda não está pronta"
            )

            aoTerminar
                ?.invoke()

            return
        }

        val id =
            "GARUPA_CONVERSA_${System.currentTimeMillis()}"

        if (
            aoTerminar != null
        ) {

            callbacksFim[
                id
            ] =
                aoTerminar
        }

        Log.d(
            "GARUPA_VOZ",
            "🔊 Falando conversa: $mensagemLimpa"
        )

        registrarRotaAudio(
            momento =
                "ANTES_CONVERSA"
        )

        val resultado =
            tts?.speak(
                mensagemLimpa,
                TextToSpeech.QUEUE_ADD,
                null,
                id
            )

        if (
            resultado ==
            TextToSpeech.ERROR
        ) {

            Log.e(
                "GARUPA_VOZ",
                "❌ TTS recusou a fala"
            )

            executarCallbackFim(
                id
            )
        }
    }

    /*
     * =========================================================
     * ESTADO
     * =========================================================
     */

    fun estaFalandoDecisao():
            Boolean {

        return decisaoFalando
    }

    /*
     * =========================================================
     * CONTROLE
     * =========================================================
     */

    fun pararFala() {

        val idAtual =
            idFalaAtual

        try {

            tts?.stop()

        } catch (_: Exception) {
        }

        if (
            idAtual != null
        ) {

            limparEstadoDaFala(
                idAtual
            )

            executarCallbackFim(
                idAtual
            )
        }
    }

    fun encerrar() {

        pronta =
            false

        idFalaAtual =
            null

        decisaoFalando =
            false

        callbacksFim.clear()

        tts?.stop()
        tts?.shutdown()

        tts =
            null
    }
}