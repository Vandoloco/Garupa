package br.com.garupa.app.core

object Personalidade {

    val nome = "Garupa"

    val estilo = "companheiro"

    fun saudacao(): String {
        return "Garupa pronto para rodar."
    }

    fun sugestao(mensagem: String): String {
        return "Sugiro: $mensagem"
    }

}