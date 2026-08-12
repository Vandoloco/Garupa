package br.com.garupa.app.core.leitura

import android.content.Context
import android.net.Uri
import android.util.Log
import br.com.garupa.app.core.decisao.AvaliadorOferta
import br.com.garupa.app.core.geocodificacao.CoordenadaEndereco
import br.com.garupa.app.core.geocodificacao.GeocodificadorEndereco
import br.com.garupa.app.core.localizacao.GerenciadorLocalizacao
import br.com.garupa.app.core.memoria.OfertaTemporaria
import br.com.garupa.app.core.parser.ParserKeeta
import br.com.garupa.app.core.rota.CalculadorRota
import br.com.garupa.app.core.rota.CoordenadaRota
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.text.Normalizer

class LeitorTela(
    private val contexto: Context
) {

    companion object {

        /*
         * Estas travas são compartilhadas por TODAS
         * as instâncias de LeitorTela.
         *
         * Isso evita que captura contínua + teste offline,
         * ou qualquer outra instância, analisem
         * a mesma oferta ao mesmo tempo.
         */
        private val travaGlobal =
            Any()

        private var assinaturaGlobalEmAnalise: String? =
            null

        private var ultimaAssinaturaGlobalConcluida: String? =
            null
    }

    private val reconhecedor =
        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

    private val parserKeeta =
        ParserKeeta()

    private val geocodificador =
        GeocodificadorEndereco(
            contexto
        )

    private val gerenciadorLocalizacao =
        GerenciadorLocalizacao(
            contexto
        )

    private val calculadorRota =
        CalculadorRota()

    private val avaliadorOferta =
        AvaliadorOferta()

    /*
     * Memória local da oferta apresentada
     * nesta sequência de telas do Keeta.
     */
    private var ofertaTemporaria =
        OfertaTemporaria()

    private var coordenadaB: CoordenadaEndereco? =
        null

    private var coordenadaC: CoordenadaEndereco? =
        null

    /*
     * Confirmação de possível nova oferta.
     */
    private var assinaturaCandidata: String? =
        null

    private var confirmacoesCandidata =
        0

    private val confirmacoesNecessarias =
        2

    /*
     * Evita disparar B/C várias vezes
     * dentro da mesma instância.
     */
    @Volatile
    private var geocodificacaoEmAndamento =
        false

    /*
     * Evita iniciar duas rotas pelos
     * callbacks B e C.
     */
    @Volatile
    private var rotaEmAndamento =
        false

    /*
     * Guarda qual assinatura ESTA instância
     * conseguiu reservar globalmente.
     */
    private var assinaturaReservada: String? =
        null

    fun lerImagem(
        caminhoImagem: String
    ) {

        val arquivo =
            File(
                caminhoImagem
            )

        if (!arquivo.exists()) {

            Log.d(
                "GARUPA",
                "🔎 Imagem não encontrada"
            )

            return
        }

        try {

            val imagem =
                InputImage.fromFilePath(
                    contexto,
                    Uri.fromFile(
                        arquivo
                    )
                )

            reconhecedor
                .process(
                    imagem
                )
                .addOnSuccessListener { resultado ->

                    if (
                        resultado.text.isBlank()
                    ) {

                        return@addOnSuccessListener
                    }

                    val linhasOcr =
                        resultado.textBlocks
                            .flatMap { bloco ->
                                bloco.lines
                            }
                            .mapNotNull { linha ->

                                val caixa =
                                    linha.boundingBox
                                        ?: return@mapNotNull null

                                LinhaOcr(
                                    texto =
                                        linha.text.trim(),

                                    x =
                                        caixa.left,

                                    y =
                                        caixa.top,

                                    largura =
                                        caixa.width(),

                                    altura =
                                        caixa.height()
                                )
                            }
                            .sortedBy { linha ->
                                linha.y
                            }

                    val resultadoKeeta =
                        parserKeeta.analisar(
                            linhasOcr
                        )

                    atualizarOfertaTemporaria(
                        valor =
                            resultadoKeeta.valor,

                        distancia =
                            resultadoKeeta.distancia,

                        nomeColeta =
                            resultadoKeeta.nomeColeta,

                        enderecoColeta =
                            resultadoKeeta.enderecoColeta,

                        enderecoEntrega =
                            resultadoKeeta.enderecoEntrega,

                        coletaVisivel =
                            resultadoKeeta.coletaVisivel,

                        entregaVisivel =
                            resultadoKeeta.entregaVisivel
                    )

                    tentarIniciarAnaliseCompleta()
                }
                .addOnFailureListener { erro ->

                    Log.e(
                        "GARUPA_OCR",
                        "👁️ Erro no reconhecimento de texto",
                        erro
                    )
                }

        } catch (erro: Exception) {

            Log.e(
                "GARUPA_OCR",
                "👁️ Erro ao preparar imagem",
                erro
            )
        }
    }

    private fun atualizarOfertaTemporaria(
        valor: Double?,
        distancia: Double?,
        nomeColeta: String?,
        enderecoColeta: String?,
        enderecoEntrega: String?,
        coletaVisivel: Boolean,
        entregaVisivel: Boolean
    ) {

        val framePareceOfertaValida =
            ehFrameDeOfertaValido(
                valor =
                    valor,

                distancia =
                    distancia,

                coletaVisivel =
                    coletaVisivel,

                enderecoColeta =
                    enderecoColeta
            )

        val novaAssinaturaBase =
            if (framePareceOfertaValida) {

                criarAssinaturaBase(
                    valor =
                        valor,

                    distancia =
                        distancia,

                    enderecoColeta =
                        enderecoColeta
                )

            } else {

                null
            }

        val assinaturaAtual =
            ofertaTemporaria.assinaturaBase

        /*
         * Primeira oferta válida desta instância.
         */
        if (
            assinaturaAtual == null &&
            novaAssinaturaBase != null
        ) {

            ofertaTemporaria.assinaturaBase =
                novaAssinaturaBase

            zerarCandidata()

            Log.d(
                "GARUPA_MEMORIA",
                "🆕 Primeira oferta válida identificada"
            )
        }

        /*
         * Possível nova oferta.
         */
        if (
            novaAssinaturaBase != null &&
            assinaturaAtual != null &&
            novaAssinaturaBase != assinaturaAtual
        ) {

            processarPossivelNovaOferta(
                novaAssinaturaBase
            )

            if (
                confirmacoesCandidata <
                confirmacoesNecessarias
            ) {

                Log.d(
                    "GARUPA_MEMORIA",
                    "⚠️ Frame diferente válido, aguardando confirmação"
                )

                return
            }

            Log.d(
                "GARUPA_MEMORIA",
                "✅ Nova oferta confirmada em " +
                        "$confirmacoesNecessarias frames válidos"
            )

            limparOfertaAtual()

            ofertaTemporaria.assinaturaBase =
                novaAssinaturaBase

            zerarCandidata()

        } else {

            /*
             * Se voltou para a oferta atual,
             * elimina a candidata.
             */
            if (
                novaAssinaturaBase != null &&
                novaAssinaturaBase ==
                ofertaTemporaria.assinaturaBase
            ) {

                zerarCandidata()
            }

            /*
             * Frame não tem estrutura suficiente
             * para representar uma nova oferta.
             *
             * Mas pode ser a tela subida contendo C.
             */
            if (
                assinaturaAtual != null &&
                novaAssinaturaBase == null &&
                !framePareceOfertaValida
            ) {

                if (
                    entregaVisivel &&
                    !enderecoEntrega.isNullOrBlank()
                ) {

                    ofertaTemporaria.enderecoEntrega =
                        enderecoEntrega

                    atualizarEstadoCompleto()

                } else {

                    Log.d(
                        "GARUPA_MEMORIA",
                        "🧹 Ruído OCR ignorado"
                    )
                }

                return
            }
        }

        /*
         * Frame aceito como pertencente
         * à oferta atual.
         */

        if (valor != null) {

            ofertaTemporaria.valor =
                valor
        }

        if (distancia != null) {

            ofertaTemporaria.distanciaTotal =
                distancia
        }

        if (!nomeColeta.isNullOrBlank()) {

            ofertaTemporaria.nomeColeta =
                nomeColeta
        }

        if (!enderecoColeta.isNullOrBlank()) {

            ofertaTemporaria.enderecoColeta =
                enderecoColeta
        }

        if (
            entregaVisivel &&
            !enderecoEntrega.isNullOrBlank()
        ) {

            ofertaTemporaria.enderecoEntrega =
                enderecoEntrega
        }

        if (
            ofertaTemporaria.assinaturaBase == null
        ) {

            ofertaTemporaria.assinaturaBase =
                criarAssinaturaBase(
                    valor =
                        ofertaTemporaria.valor,

                    distancia =
                        ofertaTemporaria.distanciaTotal,

                    enderecoColeta =
                        ofertaTemporaria.enderecoColeta
                )
        }

        atualizarEstadoCompleto()
    }

    private fun atualizarEstadoCompleto() {

        ofertaTemporaria.completa =
            ofertaTemporaria.valor != null &&
                    ofertaTemporaria.distanciaTotal != null &&
                    !ofertaTemporaria
                        .enderecoColeta
                        .isNullOrBlank() &&
                    !ofertaTemporaria
                        .enderecoEntrega
                        .isNullOrBlank()

        if (
            ofertaTemporaria.completa
        ) {

            Log.d(
                "GARUPA_MEMORIA",
                "✅ Oferta completa | " +
                        "Valor: ${ofertaTemporaria.valor} | " +
                        "B: ${ofertaTemporaria.enderecoColeta} | " +
                        "C: ${ofertaTemporaria.enderecoEntrega}"
            )

        } else {

            Log.d(
                "GARUPA_MEMORIA",
                "⏳ Oferta parcial | " +
                        "Valor: ${ofertaTemporaria.valor} | " +
                        "B: ${ofertaTemporaria.enderecoColeta} | " +
                        "C: aguardando"
            )
        }
    }

    private fun tentarIniciarAnaliseCompleta() {

        if (
            !ofertaTemporaria.completa
        ) {

            return
        }

        if (
            geocodificacaoEmAndamento ||
            rotaEmAndamento
        ) {

            return
        }

        val assinaturaFinal =
            criarAssinaturaFinal(
                ofertaTemporaria
            ) ?: return

        /*
         * ESTA É A TRAVA PRINCIPAL.
         *
         * Ela é global, então duas instâncias
         * diferentes de LeitorTela não conseguem
         * reservar a mesma oferta.
         */
        val conseguiuReservar =
            synchronized(
                travaGlobal
            ) {

                when {

                    assinaturaFinal ==
                            ultimaAssinaturaGlobalConcluida -> {

                        false
                    }

                    assinaturaFinal ==
                            assinaturaGlobalEmAnalise -> {

                        false
                    }

                    else -> {

                        assinaturaGlobalEmAnalise =
                            assinaturaFinal

                        true
                    }
                }
            }

        if (!conseguiuReservar) {

            Log.d(
                "GARUPA_DEDUP",
                "⏭️ Oferta já está sendo analisada ou já foi concluída"
            )

            return
        }

        assinaturaReservada =
            assinaturaFinal

        geocodificacaoEmAndamento =
            true

        coordenadaB =
            null

        coordenadaC =
            null

        Log.d(
            "GARUPA_DEDUP",
            "🔒 Oferta reservada globalmente"
        )

        geocodificarB()

        geocodificarC()
    }

    private fun geocodificarB() {

        val enderecoB =
            ofertaTemporaria.enderecoColeta

        if (enderecoB.isNullOrBlank()) {

            liberarAnaliseComErro()

            return
        }

        geocodificador.buscar(
            enderecoB
        ) { coordenada ->

            if (
                coordenada == null
            ) {

                Log.d(
                    "GARUPA_COORD_B",
                    "❌ Não foi possível localizar B"
                )

                liberarAnaliseComErro()

                return@buscar
            }

            coordenadaB =
                coordenada

            Log.d(
                "GARUPA_COORD_B",
                "📍 B = " +
                        "${coordenada.latitude}, " +
                        "${coordenada.longitude}"
            )

            tentarCalcularRota()
        }
    }

    private fun geocodificarC() {

        val enderecoC =
            ofertaTemporaria.enderecoEntrega

        if (enderecoC.isNullOrBlank()) {

            liberarAnaliseComErro()

            return
        }

        geocodificador.buscar(
            enderecoC
        ) { coordenada ->

            if (
                coordenada == null
            ) {

                Log.d(
                    "GARUPA_COORD_C",
                    "❌ Não foi possível localizar C"
                )

                liberarAnaliseComErro()

                return@buscar
            }

            coordenadaC =
                coordenada

            Log.d(
                "GARUPA_COORD_C",
                "🏠 C = " +
                        "${coordenada.latitude}, " +
                        "${coordenada.longitude}"
            )

            tentarCalcularRota()
        }
    }

    @Synchronized
    private fun tentarCalcularRota() {

        val pontoB =
            coordenadaB
                ?: return

        val pontoC =
            coordenadaC
                ?: return

        val valorOferta =
            ofertaTemporaria.valor
                ?: return liberarAnaliseComErro()

        if (
            rotaEmAndamento
        ) {

            return
        }

        rotaEmAndamento =
            true

        geocodificacaoEmAndamento =
            false

        Log.d(
            "GARUPA_ROTA",
            "🧭 B e C prontos. Buscando ponto A..."
        )

        gerenciadorLocalizacao
            .obterUltimaLocalizacao { localizacaoA ->

                if (
                    localizacaoA == null
                ) {

                    Log.d(
                        "GARUPA_ROTA",
                        "❌ Ponto A indisponível"
                    )

                    liberarAnaliseComErro()

                    return@obterUltimaLocalizacao
                }

                val pontoA =
                    CoordenadaRota(
                        latitude =
                            localizacaoA.latitude,

                        longitude =
                            localizacaoA.longitude
                    )

                val pontoBRota =
                    CoordenadaRota(
                        latitude =
                            pontoB.latitude,

                        longitude =
                            pontoB.longitude
                    )

                val pontoCRota =
                    CoordenadaRota(
                        latitude =
                            pontoC.latitude,

                        longitude =
                            pontoC.longitude
                    )

                calculadorRota.calcularABC(
                    pontoA =
                        pontoA,

                    pontoB =
                        pontoBRota,

                    pontoC =
                        pontoCRota
                ) { resultado ->

                    if (
                        resultado == null
                    ) {

                        Log.d(
                            "GARUPA_ROTA_FINAL",
                            "❌ Não foi possível calcular A → B → C"
                        )

                        liberarAnaliseComErro()

                        return@calcularABC
                    }

                    Log.d(
                        "GARUPA_ROTA_FINAL",
                        "✅ A → B = " +
                                "%.2f km | ".format(
                                    resultado.distanciaABKm
                                ) +
                                "B → C = " +
                                "%.2f km | ".format(
                                    resultado.distanciaBCKm
                                ) +
                                "TOTAL = " +
                                "%.2f km".format(
                                    resultado.distanciaTotalKm
                                )
                    )

                    val avaliacao =
                        avaliadorOferta.avaliar(
                            valorOferta =
                                valorOferta,

                            distanciaTotalKm =
                                resultado.distanciaTotalKm
                        )

                    /*
                     * Primeiro marcamos GLOBALMENTE
                     * como concluída.
                     *
                     * Só depois emitimos o resultado.
                     */
                    concluirReservaGlobal()

                    ofertaTemporaria.analisada =
                        true

                    rotaEmAndamento =
                        false

                    geocodificacaoEmAndamento =
                        false

                    Log.d(
                        "GARUPA_RESULTADO_FINAL",
                        "💰 R$ %.2f | ".format(
                            avaliacao.valorOferta
                        ) +
                                "%.2f km | ".format(
                                    avaliacao.distanciaTotalKm
                                ) +
                                "R$ %.2f/km | ".format(
                                    avaliacao.valorPorKm
                                ) +
                                avaliacao.sugestao
                    )

                    Log.d(
                        "GARUPA_MEMORIA",
                        "🏁 Oferta finalizada e marcada como analisada"
                    )
                }
            }
    }

    private fun concluirReservaGlobal() {

        val assinatura =
            assinaturaReservada
                ?: return

        synchronized(
            travaGlobal
        ) {

            ultimaAssinaturaGlobalConcluida =
                assinatura

            if (
                assinaturaGlobalEmAnalise ==
                assinatura
            ) {

                assinaturaGlobalEmAnalise =
                    null
            }
        }

        assinaturaReservada =
            null
    }

    /*
     * Em caso de falha liberamos a reserva
     * para permitir outra tentativa posterior.
     */
    private fun liberarReservaGlobal() {

        val assinatura =
            assinaturaReservada

        synchronized(
            travaGlobal
        ) {

            if (
                assinatura != null &&
                assinaturaGlobalEmAnalise ==
                assinatura
            ) {

                assinaturaGlobalEmAnalise =
                    null
            }
        }

        assinaturaReservada =
            null
    }

    private fun liberarAnaliseComErro() {

        geocodificacaoEmAndamento =
            false

        rotaEmAndamento =
            false

        coordenadaB =
            null

        coordenadaC =
            null

        liberarReservaGlobal()

        Log.d(
            "GARUPA_DEDUP",
            "🔄 Análise liberada para nova tentativa"
        )
    }

    private fun limparOfertaAtual() {

        /*
         * Só limpamos a memória local.
         * Uma análise global já iniciada não
         * deve ser apagada por um frame novo.
         */
        ofertaTemporaria =
            OfertaTemporaria()

        coordenadaB =
            null

        coordenadaC =
            null

        zerarCandidata()
    }

    private fun processarPossivelNovaOferta(
        novaAssinatura: String
    ) {

        if (
            assinaturaCandidata ==
            novaAssinatura
        ) {

            confirmacoesCandidata++

        } else {

            assinaturaCandidata =
                novaAssinatura

            confirmacoesCandidata =
                1
        }

        Log.d(
            "GARUPA_MEMORIA",
            "🧐 Possível nova oferta válida | " +
                    "confirmação " +
                    "$confirmacoesCandidata/" +
                    "$confirmacoesNecessarias"
        )
    }

    private fun zerarCandidata() {

        assinaturaCandidata =
            null

        confirmacoesCandidata =
            0
    }

    private fun ehFrameDeOfertaValido(
        valor: Double?,
        distancia: Double?,
        coletaVisivel: Boolean,
        enderecoColeta: String?
    ): Boolean {

        if (
            valor == null ||
            valor <= 0.0
        ) {

            return false
        }

        if (
            distancia == null ||
            distancia <= 0.0 ||
            distancia > 100.0
        ) {

            return false
        }

        if (!coletaVisivel) {

            return false
        }

        if (
            enderecoColeta.isNullOrBlank()
        ) {

            return false
        }

        val endereco =
            enderecoColeta.trim()

        if (
            !Regex("""\d""")
                .containsMatchIn(
                    endereco
                )
        ) {

            return false
        }

        val sinaisDeRuido =
            listOf(
                "r$",
                "de olho",
                "novo pedido",
                "ganhos",
                "aceitar",
                "recusar",
                "pegar",
                "entrega food",
                "pedido aguardando"
            )

        if (
            sinaisDeRuido.any { ruido ->

                endereco.contains(
                    ruido,
                    ignoreCase = true
                )
            }
        ) {

            return false
        }

        if (
            endereco.length > 180
        ) {

            return false
        }

        val pareceEndereco =
            Regex(
                """\b(rua|r\.|av\.?|avenida|alameda|travessa|estrada|rodovia|praça|praca)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(
                endereco
            )

        return pareceEndereco
    }

    private fun criarAssinaturaBase(
        valor: Double?,
        distancia: Double?,
        enderecoColeta: String?
    ): String? {

        if (
            valor == null ||
            distancia == null ||
            enderecoColeta.isNullOrBlank()
        ) {

            return null
        }

        return listOf(
            "%.2f".format(valor),
            "%.2f".format(distancia),
            normalizar(
                enderecoColeta
            )
        ).joinToString(
            "|"
        )
    }

    private fun criarAssinaturaFinal(
        oferta: OfertaTemporaria
    ): String? {

        val valor =
            oferta.valor
                ?: return null

        val distancia =
            oferta.distanciaTotal
                ?: return null

        val enderecoB =
            oferta.enderecoColeta
                ?: return null

        val enderecoC =
            oferta.enderecoEntrega
                ?: return null

        return listOf(
            "%.2f".format(valor),
            "%.2f".format(distancia),
            normalizar(
                enderecoB
            ),
            normalizar(
                enderecoC
            )
        ).joinToString(
            "|"
        )
    }

    private fun normalizar(
        texto: String
    ): String {

        val semAcentos =
            Normalizer.normalize(
                texto.lowercase(),
                Normalizer.Form.NFD
            ).replace(
                Regex(
                    "\\p{InCombiningDiacriticalMarks}+"
                ),
                ""
            )

        return semAcentos
            .replace(
                Regex("[^a-z0-9 ]"),
                ""
            )
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }
}