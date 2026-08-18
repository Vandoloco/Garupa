package br.com.garupa.app.core.olhos

data class ContextoVisualGarupa(

    val descricao: String,

    val atualizadoEm: Long =
        System.currentTimeMillis()
) {

    /*
     * =========================================================
     * VALIDADE DO CONTEXTO VISUAL
     * =========================================================
     *
     * Uma informação visual não deve permanecer válida
     * indefinidamente.
     *
     * Se a tela mudou e nenhum novo contexto chegou,
     * o Garupa não deve continuar acreditando que ainda
     * está vendo aquela oferta.
     */

    fun estaAtual(
        validadeMs: Long
    ): Boolean {

        if (
            validadeMs <= 0L
        ) {

            return false
        }

        val idade =
            System.currentTimeMillis() -
                    atualizadoEm

        return idade in
                0L..validadeMs
    }

    fun idadeMs():
            Long {

        return (
                System.currentTimeMillis() -
                        atualizadoEm
                )
            .coerceAtLeast(
                0L
            )
    }
}