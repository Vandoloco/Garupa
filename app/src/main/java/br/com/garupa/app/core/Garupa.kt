package br.com.garupa.app.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import br.com.garupa.app.core.analise.AnalisadorCorrida
import br.com.garupa.app.core.analise.Pedido
import br.com.garupa.app.core.cerebro.GarupaCerebro
import br.com.garupa.app.core.memoria.Memoria
import br.com.garupa.app.core.olhos.Olhos
import br.com.garupa.app.core.ouvido.Ouvido
import br.com.garupa.app.core.voz.Voz

object Garupa {

    private val memoria = Memoria()
    private val ouvido = Ouvido()
    private val olhos = Olhos()
    private val cerebro = GarupaCerebro()
    private val analisador = AnalisadorCorrida()

    private lateinit var voz: Voz

    fun iniciar(contexto: Context) {

        Log.d("GARUPA", "🚀 Iniciando Garupa...")

        voz = Voz(contexto)

        memoria.carregar()
        ouvido.iniciar()
        olhos.iniciar()

        val resposta = cerebro.iniciar()

        voz.iniciar()

        Log.d("GARUPA", resposta)

        val pedidoTeste = Pedido(
            valorBase = 9.64,
            taxaExtra = 0.0,
            distanciaAteRetirada = 3.5,
            distanciaRetiradaAteEntrega = 5.8
        )

        val resultadoAnalise = analisador.analisar(pedidoTeste)

        Log.d("GARUPA", "📦 $resultadoAnalise")

        Handler(Looper.getMainLooper()).postDelayed({

            if (resultadoAnalise.contains("Sugiro aceitar.")) {
                voz.falar("Sugiro aceitar.")
            } else {
                voz.falar("Sugiro deixar passar.")
            }

        }, 1500)

        Log.d("GARUPA", "✅ Garupa pronto para rodar!")
    }
}