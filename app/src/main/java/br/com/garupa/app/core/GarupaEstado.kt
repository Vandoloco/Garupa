package br.com.garupa.app.core

object GarupaEstado {

    var analisandoPedidos = true

    var interacaoPausada = false

    fun pausarAnalise() {
        analisandoPedidos = false
    }

    fun continuarAnalise() {
        analisandoPedidos = true
    }

    fun pausarInteracao() {
        interacaoPausada = true
    }

    fun continuarInteracao() {
        interacaoPausada = false
    }
}