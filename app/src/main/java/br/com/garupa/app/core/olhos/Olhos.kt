package br.com.garupa.app.core.olhos

import android.util.Log
import br.com.garupa.app.core.Garupa
import br.com.garupa.app.core.monitoramento.NivelRegistroGarupa
import br.com.garupa.app.core.analise.Pedido
import br.com.garupa.app.core.memoria.OfertaTemporaria
import br.com.garupa.app.core.parser.TipoParadaKeeta

class Olhos {

    companion object {

        /*
         * =====================================================
         * VALIDADE DA VISÃO
         * =====================================================
         *
         * Enquanto uma informação visual continua aparecendo
         * na tela, o contexto é renovado constantemente.
         *
         * Se nenhuma atualização visual válida chegar durante
         * este período, consideramos que aquilo já não está
         * mais sendo visto pelo Garupa.
         */
        private const val VALIDADE_CONTEXTO_VISUAL_MS =
            10_000L

        private const val PACOTE_IFOOD =
            "br.com.ifood.driver.app"

        private const val PACOTE_99FOOD =
            "com.app99.driver"

        private const val PACOTE_KEETA =
            "com.sankuai.sailor.courier"

        private const val PACOTE_WAZE =
            "com.waze"

        private const val PACOTE_MAPS =
            "com.google.android.apps.maps"

        /*
         * Pacotes que podem aparecer por cima do aplicativo atual
         * sem significar que o piloto realmente trocou de app.
         */
        private val PACOTES_TRANSITORIOS =
            setOf(
                "com.android.systemui",
                "com.google.android.inputmethod.latin",
                "com.android.permissioncontroller",
                "com.google.android.permissioncontroller",
                "br.com.garupa.app"
            )
    }

    @Volatile
    private var contextoVisualAtual:
            ContextoVisualGarupa? =
        null

    @Volatile
    private var pacoteAplicativoAtual:
            String? =
        null

    @Volatile
    private var nomeAplicativoAtual:
            String? =
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

    fun iniciar() {

        Log.d(
            "GARUPA",
            "👀 Olhos prontos"
        )

        registrarMonitor(
            nivel = NivelRegistroGarupa.INFO,
            categoria = "OLHOS",
            mensagem = "Olhos prontos"
        )
    }

    fun criarPedido(
        valorBase: Double,
        distanciaAteRetirada: Double,
        distanciaRetiradaAteEntrega: Double
    ): Pedido {

        return Pedido(
            valorBase = valorBase,
            taxaExtra = 0.0,
            distanciaAteRetirada = distanciaAteRetirada,
            distanciaRetiradaAteEntrega = distanciaRetiradaAteEntrega
        )
    }

    /*
     * =========================================================
     * APLICATIVO EM PRIMEIRO PLANO
     * =========================================================
     *
     * O AccessibilityService informa o packageName real.
     * Assim o Garupa não precisa adivinhar o aplicativo
     * apenas pelos textos que o OCR encontrou.
     *
     * A visão geral fica restrita aos cinco aplicativos
     * de trabalho definidos nesta versão.
     */
    fun observarAplicativo(
        pacote: String
    ) {

        val pacoteLimpo =
            pacote.trim()

        if (
            pacoteLimpo.isBlank()
        ) {

            return
        }

        if (
            PACOTES_TRANSITORIOS.contains(
                pacoteLimpo
            )
        ) {

            return
        }

        val nomeReconhecido =
            identificarAplicativoTrabalho(
                pacoteLimpo
            )

        if (
            nomeReconhecido == null
        ) {

            val tinhaAplicativo =
                pacoteAplicativoAtual != null

            pacoteAplicativoAtual =
                null

            nomeAplicativoAtual =
                null

            if (
                tinhaAplicativo
            ) {

                contextoVisualAtual =
                    null

                Log.d(
                    "GARUPA_OLHOS_APP",
                    "🚪 Saiu dos apps de trabalho | " +
                            "contexto visual limpo"
                )

                registrarMonitor(
                    nivel = NivelRegistroGarupa.INFO,
                    categoria = "OLHOS_APP",
                    mensagem = "Saiu dos apps de trabalho | contexto visual limpo"
                )
            }

            return
        }

        val pacoteAnterior =
            pacoteAplicativoAtual

        if (
            pacoteAnterior != null &&
            pacoteAnterior != pacoteLimpo
        ) {

            contextoVisualAtual =
                null

            Log.d(
                "GARUPA_OLHOS_APP",
                "🔄 Troca de app | " +
                        "$pacoteAnterior -> $pacoteLimpo | " +
                        "contexto visual anterior limpo"
            )

            registrarMonitor(
                nivel = NivelRegistroGarupa.INFO,
                categoria = "OLHOS_APP",
                mensagem = "Troca de app | $pacoteAnterior -> $pacoteLimpo | contexto visual anterior limpo"
            )
        }

        val mudouAplicativo =
            pacoteAnterior !=
                    pacoteLimpo

        pacoteAplicativoAtual =
            pacoteLimpo

        nomeAplicativoAtual =
            nomeReconhecido

        if (
            mudouAplicativo
        ) {

            Log.d(
                "GARUPA_OLHOS_APP",
                "📱 App de trabalho atual: " +
                        "$nomeReconhecido | " +
                        "pacote=$pacoteLimpo"
            )

            registrarMonitor(
                nivel = NivelRegistroGarupa.INFO,
                categoria = "OLHOS_APP",
                mensagem = "App de trabalho atual=$nomeReconhecido | pacote=$pacoteLimpo"
            )
        }
    }

    /*
     * =========================================================
     * VISÃO GERAL DA TELA
     * =========================================================
     *
     * Esta função representa os "olhos abertos" do Garupa.
     *
     * Ela recebe os textos que o OCR conseguiu enxergar na
     * tela atual, mesmo quando a tela NÃO representa uma oferta.
     *
     * Isso permite que o Garupa tenha contexto visual geral
     * de iFood, Keeta, 99Food, Maps, Waze etc.
     *
     * A análise especializada de oferta continua separada
     * através de observarOferta().
     */
    fun observarTela(
        linhas: List<String>
    ) {

        val linhasValidas =
            linhas
                .map { linha ->
                    linha.trim()
                }
                .filter { linha ->
                    linha.isNotBlank()
                }
                .distinct()
                .take(40)

        if (
            linhasValidas.isEmpty()
        ) {

            return
        }

        val nomeAplicativo =
            nomeAplicativoAtual
                ?: return

        val descricao =
            buildString {

                appendLine(
                    "O Garupa está vendo a tela atual do celular."
                )

                appendLine(
                    "Aplicativo em primeiro plano: $nomeAplicativo."
                )

                appendLine(
                    "Textos visíveis na tela:"
                )

                linhasValidas
                    .forEach { linha ->

                        appendLine(
                            "- $linha"
                        )
                    }
            }
                .trim()

        contextoVisualAtual =
            ContextoVisualGarupa(
                descricao = descricao
            )

        Log.d(
            "GARUPA_OLHOS_TELA",
            "👀 Tela geral atualizada | " +
                    "linhas=${linhasValidas.size}"
        )

        registrarMonitor(
            nivel = NivelRegistroGarupa.INFO,
            categoria = "OLHOS_TELA",
            mensagem = "Tela geral atualizada | app=$nomeAplicativo | linhas=${linhasValidas.size}"
        )
    }

    /*
     * =========================================================
     * CONTEXTO VISUAL DE OFERTA
     * =========================================================
     */

    fun observarOferta(
        oferta: OfertaTemporaria
    ) {

        val descricao =
            construirDescricaoOferta(
                oferta
            )

        if (
            descricao.isBlank()
        ) {

            return
        }

        contextoVisualAtual =
            ContextoVisualGarupa(
                descricao = descricao
            )

        Log.d(
            "GARUPA_OLHOS_CONTEXTO",
            "👀 Contexto visual atualizado:\n$descricao"
        )

        registrarMonitor(
            nivel = NivelRegistroGarupa.INFO,
            categoria = "OLHOS_OFERTA",
            mensagem = "Contexto de oferta atualizado | valor=${oferta.valor} | distanciaTela=${oferta.distanciaTotal} | paradas=${oferta.paradas.size} | completa=${oferta.completa} | analisada=${oferta.analisada}"
        )
    }

    /*
     * =========================================================
     * CONTEXTO PARA O CÉREBRO
     * =========================================================
     */

    fun construirContexto():
            String {

        val contexto =
            contextoVisualAtual
                ?: return ""

        /*
         * O contexto existe, mas precisamos descobrir
         * se ele ainda representa aquilo que está
         * realmente na tela.
         */
        if (
            !contexto.estaAtual(
                VALIDADE_CONTEXTO_VISUAL_MS
            )
        ) {

            Log.d(
                "GARUPA_OLHOS_CONTEXTO",
                "⌛ Contexto visual expirou | " +
                        "idade=${contexto.idadeMs()}ms"
            )

            registrarMonitor(
                nivel = NivelRegistroGarupa.AVISO,
                categoria = "OLHOS_CONTEXTO",
                mensagem = "Contexto visual expirou | idade=${contexto.idadeMs()}ms"
            )

            contextoVisualAtual =
                null

            return ""
        }

        return contexto.descricao
    }

    /*
     * =========================================================
     * ESTADO VISUAL
     * =========================================================
     */

    fun possuiContextoAtual():
            Boolean {

        val contexto =
            contextoVisualAtual
                ?: return false

        if (
            !contexto.estaAtual(
                VALIDADE_CONTEXTO_VISUAL_MS
            )
        ) {

            contextoVisualAtual =
                null

            return false
        }

        return true
    }

    fun limparContexto() {

        contextoVisualAtual =
            null

        Log.d(
            "GARUPA_OLHOS_CONTEXTO",
            "🧹 Contexto visual limpo"
        )

        registrarMonitor(
            nivel = NivelRegistroGarupa.INFO,
            categoria = "OLHOS_CONTEXTO",
            mensagem = "Contexto visual limpo"
        )
    }

    /*
     * =========================================================
     * DESCRIÇÃO DA OFERTA
     * =========================================================
     */

    private fun identificarAplicativoTrabalho(
        pacote: String
    ): String? {

        return when (
            pacote
        ) {

            PACOTE_IFOOD ->
                "iFood"

            PACOTE_99FOOD ->
                "99Food"

            PACOTE_KEETA ->
                "Keeta"

            PACOTE_WAZE ->
                "Waze"

            PACOTE_MAPS ->
                "Google Maps"

            else ->
                null
        }
    }

    private fun construirDescricaoOferta(
        oferta: OfertaTemporaria
    ): String {

        val possuiInformacao =
            oferta.valor != null ||
                    oferta.distanciaTotal != null ||
                    oferta.paradas.isNotEmpty() ||
                    !oferta.nomeColeta.isNullOrBlank() ||
                    !oferta.enderecoColeta.isNullOrBlank() ||
                    !oferta.enderecoEntrega.isNullOrBlank()

        if (
            !possuiInformacao
        ) {

            return ""
        }

        return buildString {

            appendLine(
                "O Garupa está observando uma oferta de entrega na tela."
            )

            nomeAplicativoAtual
                ?.let { nomeAplicativo ->

                    appendLine(
                        "Aplicativo em primeiro plano: $nomeAplicativo."
                    )
                }

            /*
             * =================================================
             * VALOR
             * =================================================
             */

            oferta.valor
                ?.let { valor ->

                    appendLine(
                        "Valor exibido: R$ %.2f.".format(
                            valor
                        )
                    )
                }

            /*
             * =================================================
             * DISTÂNCIA
             * =================================================
             */

            oferta.distanciaTotal
                ?.let { distancia ->

                    appendLine(
                        "Distância exibida: %.2f km.".format(
                            distancia
                        )
                    )
                }

            /*
             * =================================================
             * QUANTIDADE DE PEDIDOS
             * =================================================
             */

            oferta.quantidadePedidos
                ?.let { quantidade ->

                    appendLine(
                        "Quantidade de pedidos: $quantidade."
                    )
                }

            /*
             * =================================================
             * PARADAS
             * =================================================
             */

            if (
                oferta.paradas.isNotEmpty()
            ) {

                appendLine(
                    "Paradas reconhecidas:"
                )

                oferta.paradas
                    .forEachIndexed { indice, parada ->

                        val tipo =
                            when (
                                parada.tipo
                            ) {

                                TipoParadaKeeta.COLETA ->
                                    "coleta"

                                TipoParadaKeeta.ENTREGA ->
                                    "entrega"
                            }

                        val nome =
                            parada.nome
                                ?.takeIf {
                                    it.isNotBlank()
                                }

                        val endereco =
                            parada.endereco
                                .takeIf {
                                    it.isNotBlank()
                                }

                        val local =
                            when {

                                nome != null &&
                                        endereco != null &&
                                        !nome.equals(
                                            endereco,
                                            ignoreCase = true
                                        ) -> {

                                    "$nome, $endereco"
                                }

                                endereco != null ->
                                    endereco

                                nome != null ->
                                    nome

                                else ->
                                    "local não identificado"
                            }

                        appendLine(
                            "${indice + 1}. $tipo: $local."
                        )
                    }

            } else {

                /*
                 * =================================================
                 * COMPATIBILIDADE COM CAMPOS ANTIGOS
                 * =================================================
                 */

                oferta.nomeColeta
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let { nome ->

                        appendLine(
                            "Local de coleta: $nome."
                        )
                    }

                oferta.enderecoColeta
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let { endereco ->

                        appendLine(
                            "Endereço de coleta: $endereco."
                        )
                    }

                oferta.enderecoEntrega
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let { endereco ->

                        appendLine(
                            "Endereço de entrega: $endereco."
                        )
                    }
            }

            /*
             * =================================================
             * ESTADO DA LEITURA
             * =================================================
             */

            if (
                oferta.completa
            ) {

                appendLine(
                    "A leitura da oferta está completa."
                )

            } else {

                appendLine(
                    "A leitura da oferta ainda está parcial."
                )
            }

            /*
             * =================================================
             * ESTADO DA ANÁLISE
             * =================================================
             */

            if (
                oferta.analisada
            ) {

                appendLine(
                    "Essa oferta já foi analisada pelo Garupa."
                )
            }
        }
            .trim()
    }
}