package br.com.garupa.app.core.leitura

data class LinhaOcr(
    val texto: String,
    val x: Int,
    val y: Int,
    val largura: Int,
    val altura: Int
)