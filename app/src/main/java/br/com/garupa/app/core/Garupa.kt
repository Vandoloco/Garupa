package br.com.garupa.app.core

import android.content.Context
import android.util.Log
import br.com.garupa.app.core.cerebro.GarupaCerebro
import br.com.garupa.app.core.memoria.Memoria
import br.com.garupa.app.core.olhos.Olhos
import br.com.garupa.app.core.ouvido.Ouvido
import br.com.garupa.app.core.voz.Voz

object Garupa {

    private val memoria =
        Memoria()

    private val olhos =
        Olhos()

    private val cerebro =
        GarupaCerebro()

    private var ouvido:
            Ouvido? =
        null

    private var voz:
            Voz? =
        null

    fun iniciar(
        contexto: Context
    ) {

        Log.d(
            "GARUPA",
            "🚀 Iniciando Garupa..."
        )

        memoria.carregar()

        olhos.iniciar()

        /*
         * A voz é criada apenas uma vez
         * durante a vida do processo.
         */
        if (
            voz == null
        ) {

            voz =
                Voz(
                    contexto.applicationContext
                ).also {
                    it.iniciar()
                }
        }

        /*
         * O Ouvido também usa uma única instância.
         *
         * Ele é inicializado aqui, mas só começa
         * efetivamente a escutar depois que a
         * MainActivity confirmar RECORD_AUDIO.
         */
        if (
            ouvido == null
        ) {

            ouvido =
                Ouvido(
                    contexto.applicationContext
                ).also { novoOuvido ->

                    novoOuvido.definirAoReconhecerFala { frase ->

                        /*
                         * PRIMEIRA ETAPA DA CONVERSA.
                         *
                         * Por enquanto apenas comprovamos que
                         * a fala chegou ao núcleo do Garupa.
                         *
                         * Não criamos respostas programadas aqui.
                         * Depois conectaremos esta entrada ao
                         * cérebro contextual e à memória.
                         */
                        Log.d(
                            "GARUPA_CEREBRO_FALA",
                            "🧠 Fala recebida pelo Garupa: $frase"
                        )
                    }

                    novoOuvido.iniciar()
                }
        }

        val resposta =
            cerebro.iniciar()

        Log.d(
            "GARUPA",
            resposta
        )

        Log.d(
            "GARUPA",
            "✅ Garupa pronto para rodar!"
        )
    }

    fun iniciarEscuta() {

        Log.d(
            "GARUPA_OUVIDO",
            "🎤 Ativando escuta contínua"
        )

        ouvido?.comecarEscutaContinua()
    }

    fun pararEscuta() {

        ouvido?.pararEscuta()
    }

    fun obterVoz():
            Voz? {

        return voz
    }
}