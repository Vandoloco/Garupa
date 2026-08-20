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
import br.com.garupa.app.core.Garupa
import br.com.garupa.app.core.GarupaEstado
import br.com.garupa.app.core.monitoramento.NivelRegistroGarupa

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

    private fun registrarMonitor(
        nivel: NivelRegistroGarupa,
        categoria: String,
        mensagem: String
    ) {

        Garupa
            .obterMonitor()
            ?.registrar(
                nivel = nivel,
                categoria = categoria,
                mensagem = mensagem
            )
    }

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
                        if (
                            deveContinuarEscutando
                        ) {

                            garantirRotaIntercom()
                        }
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
                        if (
                            deveContinuarEscutando
                        ) {

                            garantirRotaIntercom()
                        }
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

            registrarMonitor(
                nivel = NivelRegistroGarupa.ERRO,
                categoria = "OUVIDO",
                mensagem = "Reconhecimento de voz indisponível"
            )

            return
        }

        /*
         * O modo de comunicação e a rota do intercom
         * só serão ativados quando a escuta realmente
         * começar. Assim o Ouvido não mantém o TTS
         * preso ao perfil de chamada enquanto está pausado.
         */
        registrarMonitorAudio()

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

        registrarMonitor(
            nivel = NivelRegistroGarupa.INFO,
            categoria = "OUVIDO",
            mensagem = "Ouvido inicializado"
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

                registrarMonitor(
                    nivel = NivelRegistroGarupa.ERRO,
                    categoria = "OUVIDO",
                    mensagem = "Não foi possível iniciar reconhecimento"
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

            registrarMonitor(
                nivel = NivelRegistroGarupa.INFO,
                categoria = "OUVIDO",
                mensagem = "Escuta pausada"
            )
        }
    }

    /*
     * =========================================================
     * ROTA BLUETOOTH / INTERCOM
     * =========================================================
     */

    private fun garantirRotaIntercom() {

        /*
         * Nunca reativa o perfil de comunicação se o
         * Ouvido estiver pausado. Isso evita uma corrida
         * com callbacks tardios do SpeechRecognizer e do
         * monitor de dispositivos enquanto o Garupa fala.
         */
        if (
            !deveContinuarEscutando
        ) {

            return
        }

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

                    registrarMonitor(
                        nivel = NivelRegistroGarupa.AVISO,
                        categoria = "BLUETOOTH",
                        mensagem = "BLUETOOTH_CONNECT não autorizado"
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

                        registrarMonitor(
                            nivel = NivelRegistroGarupa.INFO,
                            categoria = "BLUETOOTH",
                            mensagem = "Intercom selecionado | tipo=${intercom.type} | id=${intercom.id}"
                        )

                    } else {

                        Log.d(
                            "GARUPA_BLUETOOTH",
                            "⚠️ Android recusou seleção do intercom"
                        )

                        registrarMonitor(
                            nivel = NivelRegistroGarupa.AVISO,
                            categoria = "BLUETOOTH",
                            mensagem = "Android recusou seleção do intercom"
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

            registrarMonitor(
                nivel = NivelRegistroGarupa.ERRO,
                categoria = "BLUETOOTH",
                mensagem = "Sem permissão para controlar Bluetooth | ${erro.message.orEmpty()}"
            )

        } catch (
            erro: Exception
        ) {

            Log.e(
                "GARUPA_BLUETOOTH",
                "❌ Erro ao selecionar intercom",
                erro
            )

            registrarMonitor(
                nivel = NivelRegistroGarupa.ERRO,
                categoria = "BLUETOOTH",
                mensagem = "Erro ao selecionar intercom | ${erro.message.orEmpty()}"
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

            /*
             * O TTS deve voltar ao perfil normal quando
             * o Ouvido não está capturando voz. Sem isso,
             * o Android pode manter EARPIECE/volume de chamada.
             */
            audioManager.mode =
                AudioManager.MODE_NORMAL

            Log.d(
                "GARUPA_BLUETOOTH",
                "🔈 Rota de comunicação liberada | modo=MODE_NORMAL"
            )

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

            registrarMonitor(
                nivel = NivelRegistroGarupa.ERRO,
                categoria = "OUVIDO",
                mensagem = "Erro ao iniciar escuta | ${erro.message.orEmpty()}"
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

                    registrarMonitor(
                        nivel = NivelRegistroGarupa.AVISO,
                        categoria = "OUVIDO",
                        mensagem = "Reconhecimento retornou código=$error"
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

                    registrarMonitor(
                        nivel = NivelRegistroGarupa.CRITICO,
                        categoria = "OUVIDO",
                        mensagem = "Sem permissão para usar o microfone"
                    )

                    return
                }

                /*
                 * Um erro pode chegar depois de pararEscuta().
                 * Nesse caso NÃO podemos reativar MODE_IN_COMMUNICATION,
                 * porque o Garupa pode já estar falando pelo TTS.
                 */
                if (
                    deveContinuarEscutando
                ) {

                    garantirRotaIntercom()

                    agendarNovaEscuta(
                        700L
                    )

                } else {

                    Log.d(
                        "GARUPA_OUVIDO",
                        "🔇 Callback de erro ignorado: escuta está pausada"
                    )
                }
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

                val confiancas =
                    results
                        ?.getFloatArray(
                            SpeechRecognizer.CONFIDENCE_SCORES
                        )

                val frase =
                    textos
                        .firstOrNull()
                        ?.trim()

                val confiancaPrincipal =
                    confiancas
                        ?.firstOrNull()

                if (
                    !frase.isNullOrBlank()
                ) {

                    Log.d(
                        "GARUPA_OUVIDO",
                        "🧠 Você disse: $frase"
                    )

                    val confiancaFormatada =
                        when {

                            confiancaPrincipal == null ->
                                "indisponível"

                            confiancaPrincipal < 0f ->
                                "indisponível"

                            else ->
                                "%.2f".format(
                                    confiancaPrincipal
                                )
                        }

                    Log.d(
                        "GARUPA_CONFIANCA",
                        "🎯 Frase=\"$frase\" | " +
                                "confianca=$confiancaFormatada | " +
                                "alternativas=${textos.size}"
                    )

                    registrarMonitor(
                        nivel = NivelRegistroGarupa.INFO,
                        categoria = "VOZ_RECONHECIDA",
                        mensagem = "frase=\"$frase\" | confianca=$confiancaFormatada | alternativas=${textos.size}"
                    )

                    processarFalaReconhecida(
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
     * MODO PAUSA DA INTERAÇÃO
     * =========================================================
     *
     * "Garupa, pausa" coloca a interação em silêncio sem
     * desligar o SpeechRecognizer.
     *
     * Enquanto pausado, frases comuns NÃO são encaminhadas
     * ao cérebro/Gemini. O Ouvido continua ativo apenas para
     * reconhecer um comando explícito de retorno.
     *
     * Para evitar que uma conversa com cliente ou restaurante
     * reative o Garupa por acidente, os comandos exigem a
     * palavra "Garupa".
     */
    private fun processarFalaReconhecida(
        frase: String
    ) {

        val fraseNormalizada =
            normalizarComando(
                frase
            )

        if (
            GarupaEstado.interacaoPausada
        ) {

            if (
                ehComandoRetomar(
                    fraseNormalizada
                )
            ) {

                GarupaEstado.continuarInteracao()

                Log.d(
                    "GARUPA_MODO_PAUSA",
                    "▶️ Interação retomada por comando de voz"
                )

                registrarMonitor(
                    nivel = NivelRegistroGarupa.INFO,
                    categoria = "MODO_PAUSA",
                    mensagem = "Interação retomada por comando de voz | frase=\"$frase\""
                )

                /*
                 * O comando é encaminhado ao cérebro somente
                 * depois de sair da pausa. Assim o Garupa pode
                 * responder naturalmente que voltou.
                 */
                aoReconhecerFala
                    ?.invoke(
                        frase
                    )

            } else {

                Log.d(
                    "GARUPA_MODO_PAUSA",
                    "🤫 Fala ignorada durante pausa"
                )
            }

            return
        }

        if (
            ehComandoPausar(
                fraseNormalizada
            )
        ) {

            GarupaEstado.pausarInteracao()

            Log.d(
                "GARUPA_MODO_PAUSA",
                "⏸️ Interação pausada por comando de voz"
            )

            registrarMonitor(
                nivel = NivelRegistroGarupa.INFO,
                categoria = "MODO_PAUSA",
                mensagem = "Interação pausada por comando de voz | frase=\"$frase\""
            )

            /*
             * Não enviamos "Garupa, pausa" ao cérebro.
             * O silêncio começa imediatamente e evita uma
             * resposta desnecessária do Gemini.
             */
            return
        }

        aoReconhecerFala
            ?.invoke(
                frase
            )
    }

    private fun ehComandoPausar(
        fraseNormalizada: String
    ): Boolean {

        if (
            !fraseNormalizada.contains(
                "garupa"
            )
        ) {

            return false
        }

        val comandos =
            listOf(
                "pausa",
                "fica quieto",
                "fica quieta",
                "silencio",
                "segura ai"
            )

        return comandos.any { comando ->
            fraseNormalizada.contains(
                comando
            )
        }
    }

    private fun ehComandoRetomar(
        fraseNormalizada: String
    ): Boolean {

        /*
         * Enquanto o Garupa está pausado, aceitamos comandos
         * curtos de retorno mesmo sem a palavra "Garupa".
         *
         * Motivo prático:
         * o SpeechRecognizer pode encerrar a frase depois de
         * reconhecer apenas "Garupa", cortando "Garupa, volta".
         *
         * Fora do modo pausa, estas palavras continuam seguindo
         * o fluxo normal de conversa e não alteram o estado.
         */
        val comandos =
            listOf(
                "volta",
                "retorna",
                "pode falar",
                "pode voltar",
                "continua",
                "continuar"
            )

        return comandos.any { comando ->
            fraseNormalizada.contains(
                comando
            )
        }
    }

    private fun normalizarComando(
        texto: String
    ): String {

        return java.text.Normalizer
            .normalize(
                texto.lowercase(),
                java.text.Normalizer.Form.NFD
            )
            .replace(
                Regex(
                    "\\p{InCombiningDiacriticalMarks}+"
                ),
                ""
            )
            .replace(
                Regex(
                    "[^a-z0-9 ]"
                ),
                " "
            )
            .replace(
                Regex(
                    "\\s+"
                ),
                " "
            )
            .trim()
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

            registrarMonitor(
                nivel = NivelRegistroGarupa.INFO,
                categoria = "OUVIDO",
                mensagem = "Ouvido encerrado"
            )
        }
    }
}