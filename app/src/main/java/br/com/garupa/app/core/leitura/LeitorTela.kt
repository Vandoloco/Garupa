package br.com.garupa.app.core.leitura

import android.content.Context
import android.net.Uri
import android.util.Log
import br.com.garupa.app.core.Garupa
import br.com.garupa.app.core.GarupaEstado
import br.com.garupa.app.core.EstadoOperacionalGarupa
import br.com.garupa.app.core.decisao.AvaliadorOferta
import br.com.garupa.app.core.geocodificacao.CoordenadaEndereco
import br.com.garupa.app.core.geocodificacao.GeocodificadorEndereco
import br.com.garupa.app.core.localizacao.GerenciadorLocalizacao
import br.com.garupa.app.core.memoria.OfertaTemporaria
import br.com.garupa.app.core.monitoramento.NivelRegistroGarupa
import br.com.garupa.app.core.oferta.DetectorOferta
import br.com.garupa.app.core.oferta.ExtratorOferta
import br.com.garupa.app.core.oferta.ParadaOferta
import br.com.garupa.app.core.oferta.TipoParadaOferta
import br.com.garupa.app.core.parser.ParserKeeta
import br.com.garupa.app.core.parser.ParadaKeeta
import br.com.garupa.app.core.parser.TipoParadaKeeta
import br.com.garupa.app.core.rota.CalculadorRota
import br.com.garupa.app.core.rota.CoordenadaRota
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicLong

private data class IdentidadeOfertaGlobal(
    val valor: Double,
    val distanciaTela: Double,
    val quantidadePedidos: Int?,
    val paradas: List<ParadaKeeta>
)

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

        private var identidadeGlobalEmAnalise: IdentidadeOfertaGlobal? =
            null

        private var ultimaIdentidadeGlobalConcluida: IdentidadeOfertaGlobal? =
            null

        private var horarioUltimaConclusaoGlobal: Long =
            0L

        /*
         * A mesma oferta do Keeta costuma permanecer na tela
         * por dezenas de segundos. Durante esta janela, pequenas
         * variações do OCR não podem disparar uma nova análise.
         *
         * Depois da janela, uma oferta realmente nova com dados
         * coincidentemente iguais volta a poder ser analisada.
         */
        private const val JANELA_DEDUP_GLOBAL_MS =
            120_000L
    }

    private val reconhecedor =
        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

    private val detectorOferta =
        DetectorOferta()

    private val extratorOferta =
        ExtratorOferta()

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
    private var identidadeReservada: IdentidadeOfertaGlobal? =
        null

    /*
     * Evita que a MESMA oferta, depois de concluída,
     * volte a alternar o estado visual entre:
     *
     * PRONTO -> OFERTA_DETECTADA -> CALCULANDO_ROTA -> PRONTO
     *
     * enquanto continua parada na tela.
     */
    private var ofertaAtualJaConcluidaNoEstado =
        false

    /*
     * O OCR do ML Kit é assíncrono.
     *
     * TESTE e CAPTURA_CONTINUA usam gerações independentes.
     * Assim, um frame automático da captura não invalida a print
     * escolhida manualmente, e uma print de teste não interfere
     * na captura contínua.
     *
     * Dentro da MESMA origem, somente o resultado mais recente
     * continua válido.
     */
    private val geracaoTeste =
        AtomicLong(0L)

    private val geracaoCapturaContinua =
        AtomicLong(0L)

    fun lerImagem(
        caminhoImagem: String,
        origem: OrigemLeitura = OrigemLeitura.CAPTURA_CONTINUA
    ) {

        val contadorGeracao =
            when (origem) {

                OrigemLeitura.TESTE ->
                    geracaoTeste

                OrigemLeitura.CAPTURA_CONTINUA ->
                    geracaoCapturaContinua
            }

        val geracaoDestaImagem =
            contadorGeracao.incrementAndGet()

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
                        geracaoDestaImagem !=
                        contadorGeracao.get()
                    ) {

                        Log.d(
                            "GARUPA_FRAME",
                            "⏭️ OCR antigo descartado | " +
                                    "origem=$origem | " +
                                    "geracao=$geracaoDestaImagem | " +
                                    "atual=${contadorGeracao.get()}"
                        )

                        return@addOnSuccessListener
                    }

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

                    /*
                     * Primeiro perguntamos ao detector genérico
                     * se a tela realmente parece uma oferta.
                     *
                     * Nesta etapa, R$ é obrigatório e precisa
                     * vir acompanhado de outros sinais como km,
                     * endereço, ação de pedido ou vocabulário
                     * de delivery.
                     */
                    /*
                     * Uma nova imagem pode ter chegado enquanto
                     * transformávamos o OCR em LinhaOcr.
                     */
                    if (
                        geracaoDestaImagem !=
                        contadorGeracao.get()
                    ) {

                        Log.d(
                            "GARUPA_FRAME",
                            "⏭️ Frame ultrapassado descartado antes do detector | " +
                                    "origem=$origem | " +
                                    "geracao=$geracaoDestaImagem | " +
                                    "atual=${contadorGeracao.get()}"
                        )

                        return@addOnSuccessListener
                    }

                    /*
                     * =====================================================
                     * VISÃO GERAL DA TELA
                     * =====================================================
                     *
                     * Antes de decidir se a tela representa uma oferta,
                     * publicamos nos Olhos o texto que o OCR está vendo.
                     *
                     * Assim, telas normais de iFood, Keeta, 99Food,
                     * Maps e Waze continuam disponíveis como contexto
                     * visual, sem disparar a análise de corrida.
                     */
                    Garupa
                        .obterOlhos()
                        .observarTela(
                            linhasOcr.map { linha ->
                                linha.texto
                            }
                        )

                    val deteccaoOferta =
                        detectorOferta.analisar(
                            linhasOcr
                        )

                    if (!deteccaoOferta.pareceOferta) {

                        ofertaAtualJaConcluidaNoEstado =
                            false

                        if (
                            !geocodificacaoEmAndamento &&
                            !rotaEmAndamento &&
                            !GarupaEstado.interacaoPausada
                        ) {

                            GarupaEstado.atualizarEstado(
                                EstadoOperacionalGarupa.PRONTO
                            )
                        }

                        Log.d(
                            "GARUPA_LEITOR",
                            "🛑 Tela ignorada: não atingiu confiança de oferta"
                        )

                        return@addOnSuccessListener
                    }

                    if (
                        !geocodificacaoEmAndamento &&
                        !rotaEmAndamento &&
                        !GarupaEstado.interacaoPausada &&
                        !ofertaAtualJaConcluidaNoEstado
                    ) {

                        GarupaEstado.atualizarEstado(
                            EstadoOperacionalGarupa.OFERTA_DETECTADA
                        )
                    }

                    /*
                     * =====================================================
                     * EXTRAÇÃO OPERACIONAL GENÉRICA
                     * =====================================================
                     *
                     * A partir daqui o ExtratorOferta passa a ser a fonte
                     * operacional da memória.
                     *
                     * O ParserKeeta continua sendo executado somente como
                     * diagnóstico/fallback visual durante esta migração,
                     * mas seu resultado NÃO alimenta mais a memória.
                     */
                    val resultadoGenerico =
                        extratorOferta.extrair(
                            linhasOcr
                        )

                    /*
                     * Mantemos o parser antigo rodando para comparação
                     * de logs enquanto validamos Keeta + 99Food.
                     */
                    parserKeeta.analisar(
                        linhasOcr
                    )

                    /*
                     * Última proteção antes de alterar a memória.
                     * Se outro frame entrou enquanto detector/extrator/
                     * parser trabalhavam, este resultado já não representa
                     * mais a tela mais recente.
                     */
                    if (
                        geracaoDestaImagem !=
                        contadorGeracao.get()
                    ) {

                        Log.d(
                            "GARUPA_FRAME",
                            "⏭️ Frame ultrapassado descartado antes da memória | " +
                                    "origem=$origem | " +
                                    "geracao=$geracaoDestaImagem | " +
                                    "atual=${contadorGeracao.get()}"
                        )

                        return@addOnSuccessListener
                    }

                    val paradasOperacionais =
                        converterParadasGenericas(
                            resultadoGenerico.paradasObservadas
                        )

                    val coletaGenerica =
                        resultadoGenerico.paradasObservadas
                            .firstOrNull {
                                it.tipo ==
                                        TipoParadaOferta.COLETA
                            }

                    val entregaGenerica =
                        resultadoGenerico.paradasObservadas
                            .firstOrNull {
                                it.tipo ==
                                        TipoParadaOferta.ENTREGA
                            }

                    /*
                     * Para compatibilidade temporária com OfertaTemporaria,
                     * usamos o nome da coleta como localizador quando a
                     * plataforma não exibe o endereço completo.
                     *
                     * Exemplo observado na 99Food:
                     * Barceloneta
                     */
                    val localizadorColeta =
                        coletaGenerica
                            ?.endereco
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: coletaGenerica
                                ?.nome
                                ?.takeIf {
                                    it.isNotBlank()
                                }

                    val localizadorEntrega =
                        entregaGenerica
                            ?.endereco
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: entregaGenerica
                                ?.nome
                                ?.takeIf {
                                    it.isNotBlank()
                                }

                    val quantidadePedidosGenerica =
                        extrairQuantidadePedidosGenerica(
                            linhasOcr
                        )
                            ?: resultadoGenerico
                                .paradasObservadas
                                .count {
                                    it.tipo ==
                                            TipoParadaOferta.ENTREGA
                                }
                                .takeIf {
                                    it > 0
                                }

                    Log.d(
                        "GARUPA_FONTE_OPERACIONAL",
                        "🧠 Extrator genérico → memória | " +
                                "valor=${resultadoGenerico.valor} | " +
                                "distancia=${resultadoGenerico.distanciaKm} | " +
                                "paradas=${paradasOperacionais.size} | " +
                                "pedidos=$quantidadePedidosGenerica"
                    )

                    atualizarOfertaTemporaria(
                        valor =
                            resultadoGenerico.valor,

                        distancia =
                            resultadoGenerico.distanciaKm,

                        nomeColeta =
                            coletaGenerica?.nome,

                        enderecoColeta =
                            localizadorColeta,

                        enderecoEntrega =
                            localizadorEntrega,

                        coletaVisivel =
                            coletaGenerica != null,

                        entregaVisivel =
                            entregaGenerica != null,

                        quantidadePedidos =
                            quantidadePedidosGenerica,

                        paradas =
                            paradasOperacionais
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

    /*
     * =========================================================
     * PONTE TEMPORÁRIA: ParadaOferta -> ParadaKeeta
     * =========================================================
     *
     * A memória/rota ainda usa ParadaKeeta internamente.
     * Esta ponte permite que o ExtratorOferta genérico assuma
     * a leitura operacional sem reescrever a rota inteira agora.
     */
    private fun converterParadasGenericas(
        paradas: List<ParadaOferta>
    ): List<ParadaKeeta> {

        return paradas
            .mapNotNull { parada ->

                val tipoKeeta =
                    when (
                        parada.tipo
                    ) {

                        TipoParadaOferta.COLETA ->
                            TipoParadaKeeta.COLETA

                        TipoParadaOferta.ENTREGA ->
                            TipoParadaKeeta.ENTREGA

                        TipoParadaOferta.DESCONHECIDA ->
                            null
                    }
                        ?: return@mapNotNull null

                /*
                 * Preferimos endereço.
                 * Se a plataforma mostrar somente o nome/local,
                 * preservamos esse texto como consulta para o
                 * geocodificador.
                 */
                val localizador =
                    parada.endereco
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: parada.nome
                            ?.takeIf {
                                it.isNotBlank()
                            }
                        ?: return@mapNotNull null

                ParadaKeeta(
                    tipo =
                        tipoKeeta,

                    nome =
                        parada.nome,

                    endereco =
                        localizador
                )
            }
    }

    /*
     * =========================================================
     * QUANTIDADE EXPLÍCITA DE PEDIDOS
     * =========================================================
     *
     * Exemplos:
     * 2 pedidos para coletar
     * 3 pedidos para coletar
     */
    private fun extrairQuantidadePedidosGenerica(
        linhas: List<LinhaOcr>
    ): Int? {

        val regex =
            Regex(
                """\b(\d+)\s+pedidos?\s+(?:para\s+)?coletar\b""",
                RegexOption.IGNORE_CASE
            )

        return linhas
            .asSequence()
            .mapNotNull { linha ->

                regex
                    .find(
                        linha.texto
                    )
                    ?.groupValues
                    ?.getOrNull(
                        1
                    )
                    ?.toIntOrNull()
            }
            .firstOrNull {
                it in 1..10
            }
    }

    private fun atualizarOfertaTemporaria(
        valor: Double?,
        distancia: Double?,
        nomeColeta: String?,
        enderecoColeta: String?,
        enderecoEntrega: String?,
        coletaVisivel: Boolean,
        entregaVisivel: Boolean,
        quantidadePedidos: Int?,
        paradas: List<ParadaKeeta>
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

            ofertaAtualJaConcluidaNoEstado =
                false

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

            ofertaAtualJaConcluidaNoEstado =
                false

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

                    if (quantidadePedidos != null) {
                        ofertaTemporaria.quantidadePedidos =
                            quantidadePedidos
                    }

                    acumularParadas(
                        paradas
                    )

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

        if (quantidadePedidos != null) {

            ofertaTemporaria.quantidadePedidos =
                quantidadePedidos
        }

        /*
         * Acumula as paradas válidas encontradas
         * nos diferentes frames da mesma oferta.
         */
        acumularParadas(
            paradas
        )

        atualizarEstadoCompleto()
    }

    private fun acumularParadas(
        novasParadas: List<ParadaKeeta>
    ) {

        if (
            novasParadas.isEmpty()
        ) {
            return
        }

        /*
         * =========================================================
         * FILTRO DO FRAME
         * =========================================================
         *
         * Antes de reconciliar com a memória, removemos localizadores
         * que claramente pertencem à interface/overlay e não à oferta.
         *
         * Exemplo observado:
         * "+ Responder ao ChatGPT"
         */
        val paradasValidas =
            novasParadas
                .filter { parada ->

                    val valida =
                        localizadorParadaValido(
                            parada.endereco
                        )

                    if (!valida) {

                        Log.d(
                            "GARUPA_MEMORIA_PARADAS",
                            "🧹 Parada ignorada por ruído | " +
                                    "${parada.tipo} | " +
                                    parada.endereco
                        )
                    }

                    valida
                }

        if (
            paradasValidas.isEmpty()
        ) {

            return
        }

        /*
         * Quantas ocorrências válidas de cada tipo vieram NESTE frame.
         *
         * Isso é importante para distinguir:
         *
         * - a mesma coleta reaparecendo em vários frames;
         * - duas coletas realmente visíveis no mesmo frame;
         * - duas entregas legítimas no mesmo endereço.
         */
        val coletasNesteFrame =
            paradasValidas.count {
                it.tipo ==
                        TipoParadaKeeta.COLETA
            }

        val indicesUsadosNesteFrame =
            mutableSetOf<Int>()

        paradasValidas.forEach { novaParada ->

            val enderecoNovo =
                normalizar(
                    novaParada.endereco
                )

            if (
                enderecoNovo.isBlank()
            ) {
                return@forEach
            }

            /*
             * =====================================================
             * 1. CASAMENTO NORMAL POR SIMILARIDADE
             * =====================================================
             *
             * Uma parada existente só pode ser usada uma vez no mesmo
             * frame. Isso preserva duas entregas reais no mesmo endereço.
             */
            val indiceCompativel =
                ofertaTemporaria.paradas
                    .indices
                    .firstOrNull { indice ->

                        if (
                            indice in
                            indicesUsadosNesteFrame
                        ) {
                            return@firstOrNull false
                        }

                        val existente =
                            ofertaTemporaria.paradas[
                                indice
                            ]

                        existente.tipo ==
                                novaParada.tipo &&
                                paradasSaoMesmaParada(
                                    existente,
                                    novaParada
                                )
                    }

            if (
                indiceCompativel != null
            ) {

                indicesUsadosNesteFrame.add(
                    indiceCompativel
                )

                val existente =
                    ofertaTemporaria.paradas[
                        indiceCompativel
                    ]

                ofertaTemporaria.paradas[
                    indiceCompativel
                ] =
                    escolherMelhorParada(
                        existente,
                        novaParada
                    )

                Log.d(
                    "GARUPA_MEMORIA_PARADAS",
                    "♻️ ${novaParada.tipo} já conhecida | " +
                            novaParada.endereco
                )

                return@forEach
            }

            /*
             * =====================================================
             * 2. CONSOLIDAÇÃO DE COLETA ENTRE FRAMES
             * =====================================================
             *
             * Se este frame contém UMA única coleta válida e a memória
             * também possui UMA única coleta, tratamos uma leitura muito
             * diferente do OCR como atualização da mesma parada.
             *
             * Isso resolve casos em que a mesma coleta aparece como:
             *
             * frame A: "Alameda Araguaia, 762, Alphaville"
             * frame B: uma versão truncada/alterada pelo OCR
             *
             * sem impedir múltiplas coletas reais: se o próprio frame
             * trouxer 2+ coletas, cada ocorrência continua independente.
             */
            if (
                novaParada.tipo ==
                TipoParadaKeeta.COLETA &&
                coletasNesteFrame == 1
            ) {

                val indicesColetasExistentes =
                    ofertaTemporaria.paradas
                        .indices
                        .filter { indice ->

                            ofertaTemporaria.paradas[
                                indice
                            ].tipo ==
                                    TipoParadaKeeta.COLETA
                        }

                if (
                    indicesColetasExistentes.size == 1
                ) {

                    val indiceColeta =
                        indicesColetasExistentes.first()

                    if (
                        indiceColeta !in
                        indicesUsadosNesteFrame
                    ) {

                        val coletaExistente =
                            ofertaTemporaria.paradas[
                                indiceColeta
                            ]

                        ofertaTemporaria.paradas[
                            indiceColeta
                        ] =
                            escolherMelhorParada(
                                coletaExistente,
                                novaParada
                            )

                        indicesUsadosNesteFrame.add(
                            indiceColeta
                        )

                        Log.d(
                            "GARUPA_MEMORIA_PARADAS",
                            "♻️ COLETA consolidada entre frames | " +
                                    "memoria=${coletaExistente.endereco} | " +
                                    "frame=${novaParada.endereco}"
                        )

                        return@forEach
                    }
                }
            }

            /*
             * =====================================================
             * 3. LIMITE DE ENTREGAS
             * =====================================================
             *
             * "quantidadePedidos" limita ENTREGAS, não coletas.
             *
             * Exemplo:
             * 2 pedidos para coletar
             * -> 1 coleta
             * -> 2 entregas
             */
            if (
                novaParada.tipo ==
                TipoParadaKeeta.ENTREGA
            ) {

                val limiteEntregas =
                    ofertaTemporaria.quantidadePedidos
                        ?.takeIf {
                            it > 0
                        }

                val quantidadeEntregas =
                    ofertaTemporaria.paradas
                        .count {
                            it.tipo ==
                                    TipoParadaKeeta.ENTREGA
                        }

                if (
                    limiteEntregas != null &&
                    quantidadeEntregas >=
                    limiteEntregas
                ) {

                    Log.d(
                        "GARUPA_MEMORIA_PARADAS",
                        "🧹 ENTREGA extra ignorada " +
                                "(limite=$limiteEntregas) | " +
                                novaParada.endereco
                    )

                    return@forEach
                }
            }

            /*
             * =====================================================
             * 4. NOVA PARADA REAL
             * =====================================================
             */
            ofertaTemporaria.paradas.add(
                novaParada
            )

            val novoIndice =
                ofertaTemporaria.paradas
                    .lastIndex

            indicesUsadosNesteFrame.add(
                novoIndice
            )

            Log.d(
                "GARUPA_MEMORIA_PARADAS",
                "➕ ${novaParada.tipo} adicionada | " +
                        novaParada.endereco
            )
        }

        val coletas =
            ofertaTemporaria.paradas
                .count {
                    it.tipo ==
                            TipoParadaKeeta.COLETA
                }

        val entregas =
            ofertaTemporaria.paradas
                .count {
                    it.tipo ==
                            TipoParadaKeeta.ENTREGA
                }

        Log.d(
            "GARUPA_MEMORIA_PARADAS",
            "📚 Memória | " +
                    "coletas=$coletas | " +
                    "entregas=$entregas | " +
                    "pedidos=${ofertaTemporaria.quantidadePedidos}"
        )
    }

    /*
     * =========================================================
     * VALIDAÇÃO DE LOCALIZADOR DE PARADA
     * =========================================================
     *
     * A captura contínua pode enxergar overlays do sistema,
     * do próprio ChatGPT ou controles auxiliares sobre a print.
     * Esses textos nunca devem entrar na memória como parada.
     */
    private fun localizadorParadaValido(
        texto: String
    ): Boolean {

        val normalizado =
            normalizar(
                texto
            )

        if (
            normalizado.length < 4
        ) {
            return false
        }

        val ruidos =
            listOf(
                "responder ao chatgpt",
                "responder",
                "chatgpt",
                "google lens",
                "compartilhar",
                "favorito",
                "editar",
                "excluir",
                "visualizar",
                "aceitar",
                "recusar",
                "rejeitar",
                "pegar",
                "novo pedido",
                "pedido aguardando",
                "ganhos nessa entrega",
                "entrega food"
            )

        if (
            ruidos.any { ruido ->

                normalizado.contains(
                    normalizar(
                        ruido
                    )
                )
            }
        ) {

            return false
        }

        /*
         * Rejeita símbolos, códigos soltos e lixo OCR sem conteúdo
         * textual suficiente. Nomes como "Barceloneta" continuam
         * válidos para plataformas que não mostram o endereço da coleta.
         */
        val quantidadeLetras =
            normalizado.count {
                it.isLetter()
            }

        if (
            quantidadeLetras < 3
        ) {

            return false
        }

        val quantidadeDigitos =
            normalizado.count {
                it.isDigit()
            }

        if (
            quantidadeDigitos >= 5 &&
            quantidadeLetras <= 2
        ) {

            return false
        }

        return true
    }

    private fun paradasSaoMesmaParada(
        existente: ParadaKeeta,
        nova: ParadaKeeta
    ): Boolean {

        if (
            existente.tipo !=
            nova.tipo
        ) {
            return false
        }

        val enderecoExistente =
            normalizar(
                existente.endereco
            )

        val enderecoNovo =
            normalizar(
                nova.endereco
            )

        if (
            enderecoExistente.isBlank() ||
            enderecoNovo.isBlank()
        ) {
            return false
        }

        if (
            enderecoExistente ==
            enderecoNovo
        ) {
            return true
        }

        /*
         * Se ambos possuem número de imóvel e esses números
         * são diferentes, tratamos como paradas diferentes.
         * Isso evita juntar, por exemplo:
         * Avenida X, 500
         * Avenida X, 900
         */
        val numeroExistente =
            extrairPrimeiroNumero(
                enderecoExistente
            )

        val numeroNovo =
            extrairPrimeiroNumero(
                enderecoNovo
            )

        if (
            numeroExistente != null &&
            numeroNovo != null &&
            numeroExistente != numeroNovo
        ) {
            return false
        }

        /*
         * Se uma leitura contém praticamente toda a outra,
         * consideramos a mesma parada. Isso resolve casos em
         * que um frame perde "Brasil", "SP" ou parte do CEP.
         */
        val menorTamanho =
            minOf(
                enderecoExistente.length,
                enderecoNovo.length
            )

        val maiorTamanho =
            maxOf(
                enderecoExistente.length,
                enderecoNovo.length
            )

        if (
            menorTamanho >= 12 &&
            maiorTamanho > 0 &&
            menorTamanho.toDouble() /
            maiorTamanho.toDouble() >= 0.68 &&
            (
                    enderecoExistente.contains(
                        enderecoNovo
                    ) ||
                            enderecoNovo.contains(
                                enderecoExistente
                            )
                    )
        ) {
            return true
        }

        val similaridadeCaracteres =
            similaridadeLevenshtein(
                enderecoExistente,
                enderecoNovo
            )

        val similaridadePalavras =
            similaridadePorPalavras(
                enderecoExistente,
                enderecoNovo
            )

        return similaridadeCaracteres >= 0.82 ||
                similaridadePalavras >= 0.80
    }

    private fun escolherMelhorParada(
        existente: ParadaKeeta,
        nova: ParadaKeeta
    ): ParadaKeeta {

        val pontuacaoExistente =
            pontuarParada(
                existente
            )

        val pontuacaoNova =
            pontuarParada(
                nova
            )

        return if (
            pontuacaoNova >
            pontuacaoExistente
        ) {
            nova
        } else {
            existente
        }
    }

    private fun pontuarParada(
        parada: ParadaKeeta
    ): Int {

        var pontos =
            normalizar(
                parada.endereco
            ).length

        if (
            !parada.nome
                .isNullOrBlank()
        ) {
            pontos += 20
        }

        return pontos
    }

    private fun extrairPrimeiroNumero(
        texto: String
    ): String? {

        return Regex(
            """\b\d{1,6}\b"""
        )
            .find(
                texto
            )
            ?.value
    }

    private fun similaridadePorPalavras(
        textoA: String,
        textoB: String
    ): Double {

        val palavrasA =
            textoA
                .split(
                    Regex("\\s+")
                )
                .filter {
                    it.length >= 2
                }
                .toSet()

        val palavrasB =
            textoB
                .split(
                    Regex("\\s+")
                )
                .filter {
                    it.length >= 2
                }
                .toSet()

        if (
            palavrasA.isEmpty() ||
            palavrasB.isEmpty()
        ) {
            return 0.0
        }

        val comuns =
            palavrasA
                .intersect(
                    palavrasB
                )
                .size

        val maiorQuantidade =
            maxOf(
                palavrasA.size,
                palavrasB.size
            )

        return comuns.toDouble() /
                maiorQuantidade.toDouble()
    }

    private fun similaridadeLevenshtein(
        textoA: String,
        textoB: String
    ): Double {

        if (
            textoA == textoB
        ) {
            return 1.0
        }

        if (
            textoA.isEmpty() ||
            textoB.isEmpty()
        ) {
            return 0.0
        }

        val distancia =
            distanciaLevenshtein(
                textoA,
                textoB
            )

        val tamanhoMaior =
            maxOf(
                textoA.length,
                textoB.length
            )

        return 1.0 -
                distancia.toDouble() /
                tamanhoMaior.toDouble()
    }

    private fun distanciaLevenshtein(
        textoA: String,
        textoB: String
    ): Int {

        var anterior =
            IntArray(
                textoB.length + 1
            ) { indice ->
                indice
            }

        var atual =
            IntArray(
                textoB.length + 1
            )

        for (
        i in 1..textoA.length
        ) {

            atual[0] =
                i

            for (
            j in 1..textoB.length
            ) {

                val custo =
                    if (
                        textoA[i - 1] ==
                        textoB[j - 1]
                    ) {
                        0
                    } else {
                        1
                    }

                atual[j] =
                    minOf(
                        atual[j - 1] + 1,
                        anterior[j] + 1,
                        anterior[j - 1] + custo
                    )
            }

            val temporario =
                anterior

            anterior =
                atual

            atual =
                temporario
        }

        return anterior[
            textoB.length
        ]
    }

    private fun atualizarEstadoCompleto() {

        val coletas =
            ofertaTemporaria.paradas
                .count {
                    it.tipo ==
                            TipoParadaKeeta.COLETA
                }

        val entregas =
            ofertaTemporaria.paradas
                .count {
                    it.tipo ==
                            TipoParadaKeeta.ENTREGA
                }

        val quantidadeEsperada =
            ofertaTemporaria.quantidadePedidos

        val possuiParadasSuficientes =
            coletas >= 1 &&
                    if (
                        quantidadeEsperada != null &&
                        quantidadeEsperada > 0
                    ) {
                        entregas >=
                                quantidadeEsperada
                    } else {
                        entregas >= 1
                    }

        ofertaTemporaria.completa =
            ofertaTemporaria.valor != null &&
                    ofertaTemporaria.distanciaTotal != null &&
                    possuiParadasSuficientes

        /*
         * Publica uma CÓPIA do estado visual atual para os Olhos
         * pertencentes ao próprio Garupa.
         *
         * O LeitorTela continua dono da lógica operacional da oferta;
         * os Olhos recebem apenas um retrato do estado para contexto
         * cognitivo, sem compartilhar a lista mutável original.
         */
        Garupa
            .obterOlhos()
            .observarOferta(
                ofertaTemporaria.copy(
                    paradas =
                        ofertaTemporaria.paradas
                            .map { parada ->
                                parada.copy()
                            }
                            .toMutableList()
                )
            )

        if (
            ofertaTemporaria.completa
        ) {

            Log.d(
                "GARUPA_MEMORIA",
                "✅ Oferta completa | " +
                        "Valor: ${ofertaTemporaria.valor} | " +
                        "coletas=$coletas | " +
                        "entregas=$entregas | " +
                        "pedidos=${ofertaTemporaria.quantidadePedidos}"
            )

        } else {

            Log.d(
                "GARUPA_MEMORIA",
                "⏳ Oferta parcial | " +
                        "Valor: ${ofertaTemporaria.valor} | " +
                        "coletas=$coletas | " +
                        "entregas=$entregas | " +
                        "pedidos=${ofertaTemporaria.quantidadePedidos}"
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

        val identidadeAtual =
            criarIdentidadeOferta(
                ofertaTemporaria
            ) ?: return

        val conseguiuReservar =
            synchronized(
                travaGlobal
            ) {

                val agora =
                    System.currentTimeMillis()

                val ultimaConcluidaRecente =
                    ultimaIdentidadeGlobalConcluida
                        ?.takeIf {
                            agora - horarioUltimaConclusaoGlobal <=
                                    JANELA_DEDUP_GLOBAL_MS
                        }

                when {

                    ultimaConcluidaRecente != null &&
                            identidadesSaoMesmaOferta(
                                identidadeAtual,
                                ultimaConcluidaRecente
                            ) -> {
                        false
                    }

                    identidadeGlobalEmAnalise != null &&
                            identidadesSaoMesmaOferta(
                                identidadeAtual,
                                identidadeGlobalEmAnalise!!
                            ) -> {
                        false
                    }

                    else -> {

                        identidadeGlobalEmAnalise =
                            identidadeAtual

                        true
                    }
                }
            }

        if (!conseguiuReservar) {

            Log.d(
                "GARUPA_DEDUP",
                "⏭️ Mesma oferta ignorada pela deduplicação global"
            )

            return
        }

        identidadeReservada =
            identidadeAtual

        geocodificacaoEmAndamento =
            true

        if (
            !GarupaEstado.interacaoPausada &&
            !ofertaAtualJaConcluidaNoEstado
        ) {

            GarupaEstado.atualizarEstado(
                EstadoOperacionalGarupa.CALCULANDO_ROTA
            )
        }

        Log.d(
            "GARUPA_DEDUP",
            "🔒 Oferta multiparada reservada globalmente"
        )

        geocodificarTodasParadas()
    }

    private fun geocodificarTodasParadas() {

        val paradas =
            ofertaTemporaria.paradas
                .toList()

        if (paradas.size < 2) {

            Log.d(
                "GARUPA_ROTA_MULTI",
                "❌ Paradas insuficientes para calcular rota"
            )

            liberarAnaliseComErro()
            return
        }

        val coordenadas =
            mutableListOf<CoordenadaRota>()

        geocodificarParadaSequencialmente(
            paradas = paradas,
            indice = 0,
            coordenadas = coordenadas
        )
    }

    private fun geocodificarParadaSequencialmente(
        paradas: List<ParadaKeeta>,
        indice: Int,
        coordenadas: MutableList<CoordenadaRota>
    ) {

        if (indice >= paradas.size) {

            geocodificacaoEmAndamento =
                false

            calcularRotaMultiparada(
                paradas = paradas,
                coordenadas = coordenadas
            )

            return
        }

        val parada =
            paradas[indice]

        Log.d(
            "GARUPA_GEOCODER_MULTI",
            "📍 Geocodificando ${indice + 1}/${paradas.size} | " +
                    "${parada.tipo} | ${parada.endereco}"
        )

        geocodificador.buscar(
            parada.endereco
        ) { coordenada ->

            if (coordenada == null) {

                Log.d(
                    "GARUPA_GEOCODER_MULTI",
                    "❌ Falha na parada ${indice + 1} | ${parada.endereco}"
                )

                liberarAnaliseComErro()
                return@buscar
            }

            coordenadas.add(
                CoordenadaRota(
                    latitude =
                        coordenada.latitude,

                    longitude =
                        coordenada.longitude
                )
            )

            Log.d(
                "GARUPA_GEOCODER_MULTI",
                "✅ ${indice + 1}/${paradas.size} | " +
                        "${parada.tipo} = " +
                        "${coordenada.latitude}, ${coordenada.longitude}"
            )

            geocodificarParadaSequencialmente(
                paradas = paradas,
                indice = indice + 1,
                coordenadas = coordenadas
            )
        }
    }

    private fun calcularRotaMultiparada(
        paradas: List<ParadaKeeta>,
        coordenadas: List<CoordenadaRota>
    ) {

        if (rotaEmAndamento) {
            return
        }

        val valorOferta =
            ofertaTemporaria.valor
                ?: return liberarAnaliseComErro()

        if (
            coordenadas.size !=
            paradas.size
        ) {

            liberarAnaliseComErro()
            return
        }

        rotaEmAndamento =
            true

        Log.d(
            "GARUPA_ROTA_MULTI",
            "🧭 ${paradas.size} paradas geocodificadas. Buscando ponto A..."
        )

        gerenciadorLocalizacao
            .obterUltimaLocalizacao { localizacaoA ->

                if (localizacaoA == null) {

                    Log.d(
                        "GARUPA_ROTA_MULTI",
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

                calculadorRota.calcularMultiplaParada(
                    pontoInicial =
                        pontoA,

                    paradas =
                        coordenadas
                ) { resultado ->

                    if (resultado == null) {

                        Log.d(
                            "GARUPA_ROTA_MULTI",
                            "❌ Não foi possível calcular rota multiparada"
                        )

                        /*
                         * O CalculadorRota já fez as tentativas
                         * controladas de rede.
                         *
                         * Se ainda falhou, NÃO liberamos imediatamente
                         * esta mesma oferta para uma nova análise.
                         *
                         * Caso contrário, enquanto a oferta continuar
                         * parada na tela, cada novo frame iniciaria:
                         *
                         * OFERTA_DETECTADA
                         * -> CALCULANDO_ROTA
                         * -> falha
                         * -> PRONTO
                         * -> OFERTA_DETECTADA...
                         *
                         * Marcamos esta identidade como terminal para a
                         * deduplicação global. Uma oferta diferente
                         * continua podendo ser analisada normalmente.
                         */
                        bloquearOfertaAposFalhaRota()

                        return@calcularMultiplaParada
                    }

                    resultado.trechos
                        .forEachIndexed { indice, trecho ->

                            val destino =
                                paradas.getOrNull(
                                    indice
                                )

                            Log.d(
                                "GARUPA_ROTA_MULTI",
                                "🛣️ Trecho ${indice + 1} | " +
                                        "destino=${destino?.tipo} | " +
                                        "%.2f km".format(
                                            trecho.distanciaKm
                                        )
                            )
                        }

                    Log.d(
                        "GARUPA_ROTA_FINAL",
                        "✅ MULTIPARADA | " +
                                "paradas=${paradas.size} | " +
                                "TOTAL = %.2f km".format(
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

                    val decisaoMonitor =
                        if (
                            avaliacao.sugestao.contains(
                                "deixar passar",
                                ignoreCase = true
                            )
                        ) {
                            "DEIXAR_PASSAR"
                        } else {
                            "ACEITAR"
                        }

                    Garupa
                        .obterMonitor()
                        ?.registrar(
                            nivel =
                                NivelRegistroGarupa.INFO,

                            categoria =
                                "OFERTA_DECISAO",

                            mensagem =
                                "valor=%.2f | ".format(avaliacao.valorOferta) +
                                        "distanciaReal=%.2f | ".format(avaliacao.distanciaTotalKm) +
                                        "valorKm=%.2f | ".format(avaliacao.valorPorKm) +
                                        "decisao=$decisaoMonitor | " +
                                        "paradas=${paradas.size} | " +
                                        "pedidos=${ofertaTemporaria.quantidadePedidos}"
                        )

                    anunciarDecisaoPorVoz(
                        avaliacao.sugestao
                    )

                    ofertaAtualJaConcluidaNoEstado =
                        true

                    if (
                        !GarupaEstado.interacaoPausada
                    ) {

                        GarupaEstado.atualizarEstado(
                            EstadoOperacionalGarupa.PRONTO
                        )
                    }

                    Log.d(
                        "GARUPA_MEMORIA",
                        "🏁 Oferta multiparada finalizada e marcada como analisada"
                    )
                }
            }
    }


    private fun anunciarDecisaoPorVoz(
        sugestao: String
    ) {

        when {

            sugestao.contains(
                "deixar passar",
                ignoreCase = true
            ) -> {

                Garupa.anunciarDeixarPassar()
            }

            sugestao.contains(
                "aceitar",
                ignoreCase = true
            ) -> {

                Garupa.anunciarAceitar()
            }

            else -> {

                Log.d(
                    "GARUPA_VOZ",
                    "⚠️ Sugestão sem comando de voz conhecido: $sugestao"
                )
            }
        }
    }

    private fun concluirReservaGlobal() {

        val identidade =
            identidadeReservada
                ?: return

        synchronized(
            travaGlobal
        ) {

            ultimaIdentidadeGlobalConcluida =
                identidade

            horarioUltimaConclusaoGlobal =
                System.currentTimeMillis()

            if (
                identidadeGlobalEmAnalise != null &&
                identidadesSaoMesmaOferta(
                    identidadeGlobalEmAnalise!!,
                    identidade
                )
            ) {

                identidadeGlobalEmAnalise =
                    null
            }
        }

        identidadeReservada =
            null
    }

    /*
     * Em caso de falha liberamos a reserva
     * para permitir outra tentativa posterior.
     */
    /*
     * =========================================================
     * BLOQUEIO APÓS FALHA FINAL DE ROTA
     * =========================================================
     *
     * O CalculadorRota já possui retry controlado.
     *
     * Quando todas as tentativas falham, esta oferta não deve
     * ser liberada imediatamente para o próximo frame.
     *
     * Reaproveitamos a mesma janela de deduplicação global usada
     * por ofertas concluídas. Isso impede o loop da MESMA oferta
     * ainda visível, mas não bloqueia uma identidade diferente.
     */
    private fun bloquearOfertaAposFalhaRota() {

        val identidade =
            identidadeReservada

        synchronized(
            travaGlobal
        ) {

            if (
                identidade != null
            ) {

                ultimaIdentidadeGlobalConcluida =
                    identidade

                horarioUltimaConclusaoGlobal =
                    System.currentTimeMillis()
            }

            if (
                identidade != null &&
                identidadeGlobalEmAnalise != null &&
                identidadesSaoMesmaOferta(
                    identidadeGlobalEmAnalise!!,
                    identidade
                )
            ) {

                identidadeGlobalEmAnalise =
                    null
            }
        }

        identidadeReservada =
            null

        geocodificacaoEmAndamento =
            false

        rotaEmAndamento =
            false

        coordenadaB =
            null

        coordenadaC =
            null

        /*
         * Também bloqueia a oscilação visual do estado enquanto
         * a mesma oferta continuar na tela.
         */
        ofertaAtualJaConcluidaNoEstado =
            true

        if (
            !GarupaEstado.interacaoPausada
        ) {

            GarupaEstado.atualizarEstado(
                EstadoOperacionalGarupa.PRONTO
            )
        }

        Log.d(
            "GARUPA_DEDUP",
            "🛑 Oferta bloqueada após falha final de rota | " +
                    "mesma identidade não será reprocessada nesta janela"
        )
    }

    private fun liberarReservaGlobal() {

        val identidade =
            identidadeReservada

        synchronized(
            travaGlobal
        ) {

            if (
                identidade != null &&
                identidadeGlobalEmAnalise != null &&
                identidadesSaoMesmaOferta(
                    identidadeGlobalEmAnalise!!,
                    identidade
                )
            ) {

                identidadeGlobalEmAnalise =
                    null
            }
        }

        identidadeReservada =
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

        if (
            !GarupaEstado.interacaoPausada
        ) {

            GarupaEstado.atualizarEstado(
                EstadoOperacionalGarupa.PRONTO
            )
        }

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

        val identificadorColeta =
            enderecoColeta.trim()

        if (
            identificadorColeta.length !in 4..180
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
                "pedido aguardando",
                "pedidos para coletar"
            )

        if (
            sinaisDeRuido.any { ruido ->

                identificadorColeta.contains(
                    ruido,
                    ignoreCase = true
                )
            }
        ) {

            return false
        }

        /*
         * Caso 1: endereço tradicional.
         */
        val possuiNumero =
            Regex(
                """\d"""
            ).containsMatchIn(
                identificadorColeta
            )

        val possuiTipoVia =
            Regex(
                """\b(rua|r\.|av\.?|avenida|alameda|travessa|estrada|rodovia|praça|praca)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(
                identificadorColeta
            )

        if (
            possuiNumero &&
            possuiTipoVia
        ) {

            return true
        }

        /*
         * Caso 2: algumas plataformas exibem apenas o nome/local
         * da coleta na oferta (ex.: "Barceloneta").
         *
         * Como esse texto já veio de uma ParadaOferta classificada
         * como COLETA, aceitamos um identificador textual razoável.
         */
        val quantidadeLetras =
            identificadorColeta.count {
                it.isLetter()
            }

        val quantidadeDigitos =
            identificadorColeta.count {
                it.isDigit()
            }

        return quantidadeLetras >= 4 &&
                quantidadeDigitos < 5
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

    private fun criarIdentidadeOferta(
        oferta: OfertaTemporaria
    ): IdentidadeOfertaGlobal? {

        val valor =
            oferta.valor
                ?: return null

        val distancia =
            oferta.distanciaTotal
                ?: return null

        if (oferta.paradas.size < 2) {
            return null
        }

        return IdentidadeOfertaGlobal(
            valor =
                valor,

            distanciaTela =
                distancia,

            quantidadePedidos =
                oferta.quantidadePedidos,

            paradas =
                oferta.paradas
                    .map { parada ->
                        parada.copy()
                    }
        )
    }

    private fun identidadesSaoMesmaOferta(
        a: IdentidadeOfertaGlobal,
        b: IdentidadeOfertaGlobal
    ): Boolean {

        if (
            kotlin.math.abs(
                a.valor - b.valor
            ) > 0.02
        ) {
            return false
        }

        /*
         * A distância mostrada pelo Keeta pertence à própria
         * oferta e tende a permanecer estável. Aceitamos uma
         * pequena margem para erros de leitura do OCR.
         */
        if (
            kotlin.math.abs(
                a.distanciaTela -
                        b.distanciaTela
            ) > 0.20
        ) {
            return false
        }

        if (
            a.quantidadePedidos != null &&
            b.quantidadePedidos != null &&
            a.quantidadePedidos !=
            b.quantidadePedidos
        ) {
            return false
        }

        if (
            a.paradas.size !=
            b.paradas.size
        ) {
            return false
        }

        /*
         * Comparamos as paradas por similaridade e não por texto
         * exato. Cada parada de B só pode casar uma vez, o que
         * preserva corretamente duas entregas reais no mesmo
         * endereço.
         */
        val usadosEmB =
            mutableSetOf<Int>()

        for (paradaA in a.paradas) {

            val indiceCompativel =
                b.paradas
                    .indices
                    .firstOrNull { indice ->

                        indice !in usadosEmB &&
                                paradasSaoMesmaParada(
                                    paradaA,
                                    b.paradas[indice]
                                )
                    }
                    ?: return false

            usadosEmB.add(
                indiceCompativel
            )
        }

        return true
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