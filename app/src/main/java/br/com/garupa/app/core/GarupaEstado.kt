package br.com.garupa.app.core

object GarupaEstado {

    var analisandoPedidos = true

    fun pausarAnalise() {
        analisandoPedidos = false
    }

    fun continuarAnalise() {
        analisandoPedidos = true
    }

}