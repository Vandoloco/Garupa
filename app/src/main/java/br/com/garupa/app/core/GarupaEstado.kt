package br.com.garupa.app.core

import br.com.garupa.app.core.monitoramento.NivelRegistroGarupa
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EstadoOperacionalGarupa(
    val textoTela: String
) {

    INICIANDO(
        "Iniciando..."
    ),

    PRONTO(
        "Pronto — ouvindo e vendo"
    ),

    OUVINDO(
        "Ouvindo..."
    ),

    LENDO_TELA(
        "Vendo a tela..."
    ),

    OFERTA_DETECTADA(
        "Oferta detectada..."
    ),

    CALCULANDO_ROTA(
        "Calculando rota..."
    ),

    ANALISANDO_OFERTA(
        "Analisando oferta..."
    ),

    FALANDO(
        "Falando..."
    ),

    PAUSADO(
        "Pausado"
    ),

    SEM_ACESSIBILIDADE(
        "Acessibilidade desativada"
    ),

    SEM_CAPTURA(
        "Compartilhamento de tela indisponível"
    ),

    ERRO(
        "Verificando funcionamento..."
    )
}

object GarupaEstado {

    var analisandoPedidos =
        true

    var interacaoPausada =
        false
        private set

    /*
     * =========================================================
     * ESTADO OPERACIONAL
     * =========================================================
     *
     * Esta é a fonte central de verdade sobre o que
     * o Garupa está fazendo naquele momento.
     *
     * O mesmo estado alimenta:
     *
     * - tela principal;
     * - notificação persistente;
     * - caixa-preta de monitoramento.
     */
    private val _estadoOperacional =
        MutableStateFlow(
            EstadoOperacionalGarupa.INICIANDO
        )

    val estadoOperacional:
            StateFlow<EstadoOperacionalGarupa> =
        _estadoOperacional.asStateFlow()

    fun atualizarEstado(
        novoEstado: EstadoOperacionalGarupa
    ) {

        val estadoAnterior =
            _estadoOperacional.value

        /*
         * Não registra nem publica novamente
         * o mesmo estado.
         *
         * Isso evita poluir a caixa-preta quando,
         * por exemplo, vários frames seguidos
         * continuam detectando a mesma oferta.
         */
        if (
            estadoAnterior ==
            novoEstado
        ) {

            return
        }

        _estadoOperacional.value =
            novoEstado

        Garupa
            .obterMonitor()
            ?.registrar(
                nivel =
                    NivelRegistroGarupa.INFO,

                categoria =
                    "ESTADO",

                mensagem =
                    "${estadoAnterior.name} -> ${novoEstado.name}"
            )
    }

    fun pausarAnalise() {

        analisandoPedidos =
            false
    }

    fun continuarAnalise() {

        analisandoPedidos =
            true
    }

    fun pausarInteracao() {

        interacaoPausada =
            true

        atualizarEstado(
            EstadoOperacionalGarupa.PAUSADO
        )
    }

    fun continuarInteracao() {

        interacaoPausada =
            false

        atualizarEstado(
            EstadoOperacionalGarupa.PRONTO
        )
    }
}