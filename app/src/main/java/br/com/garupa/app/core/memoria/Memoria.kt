package br.com.garupa.app.core.memoria

import android.util.Log

enum class TipoMemoriaGarupa {

    /*
     * Algo que acabou de acontecer.
     *
     * Exemplo:
     * "Piloto disse que o trânsito está pesado."
     */
    RECENTE,

    /*
     * Algo relevante durante o dia/turno atual.
     *
     * Exemplo:
     * "Hoje apareceram várias ofertas ruins."
     */
    DIA,

    /*
     * Algo que pode continuar sendo útil
     * nos próximos dias, semanas ou meses.
     *
     * Exemplo:
     * "Piloto costuma evitar determinada região."
     */
    LONGO_PRAZO
}

enum class OrigemMemoriaGarupa {

    CONVERSA,

    OFERTA,

    DECISAO,

    LOCALIZACAO,

    ROTINA,

    SISTEMA,

    APRENDIZADO
}

data class RegistroMemoriaGarupa(

    val id: Long = System.currentTimeMillis(),

    val texto: String,

    val tipo: TipoMemoriaGarupa,

    val origem: OrigemMemoriaGarupa,

    /*
     * Quanto acreditamos que essa memória
     * merece atenção.
     *
     * 0.0 = quase descartável
     * 1.0 = muito importante
     */
    val importancia: Double = 0.5,

    /*
     * Quando esse registro foi criado.
     */
    val criadoEm: Long = System.currentTimeMillis()
)

class Memoria {

    /*
     * =========================================================
     * MEMÓRIA RECENTE
     * =========================================================
     *
     * Contexto imediato da conversa e acontecimentos
     * que acabaram de ocorrer.
     *
     * Mantemos pequena propositalmente.
     */
    private val recentes =
        mutableListOf<RegistroMemoriaGarupa>()

    /*
     * =========================================================
     * MEMÓRIA DO DIA
     * =========================================================
     *
     * Guarda acontecimentos relevantes do turno/dia.
     */
    private val doDia =
        mutableListOf<RegistroMemoriaGarupa>()

    /*
     * =========================================================
     * MEMÓRIA DE LONGO PRAZO
     * =========================================================
     *
     * Preferências, hábitos, padrões e conhecimentos
     * que podem continuar úteis no futuro.
     *
     * Nesta primeira versão ainda fica somente
     * durante a vida do processo.
     *
     * Persistência em disco virá na próxima camada.
     */
    private val longoPrazo =
        mutableListOf<RegistroMemoriaGarupa>()

    private val trava =
        Any()

    fun carregar() {

        /*
         * Neste momento ainda não carregamos dados
         * persistidos.
         *
         * Mantemos esta função porque ela já faz parte
         * do ciclo de inicialização do Garupa e será
         * usada posteriormente para restaurar a memória
         * de longo prazo do armazenamento local.
         */

        Log.d(
            "GARUPA_MEMORIA_VIDA",
            "🧠 Memória de convivência pronta"
        )
    }

    /*
     * =========================================================
     * REGISTRAR
     * =========================================================
     */

    fun registrar(
        texto: String,
        tipo: TipoMemoriaGarupa,
        origem: OrigemMemoriaGarupa,
        importancia: Double = 0.5
    ) {

        val textoLimpo =
            texto
                .replace(
                    Regex("\\s+"),
                    " "
                )
                .trim()

        if (
            textoLimpo.isBlank()
        ) {
            return
        }

        val importanciaSegura =
            importancia
                .coerceIn(
                    0.0,
                    1.0
                )

        val registro =
            RegistroMemoriaGarupa(
                texto =
                    textoLimpo,

                tipo =
                    tipo,

                origem =
                    origem,

                importancia =
                    importanciaSegura
            )

        synchronized(
            trava
        ) {

            when (
                tipo
            ) {

                TipoMemoriaGarupa.RECENTE -> {

                    recentes.add(
                        registro
                    )

                    limitarRecentes()
                }

                TipoMemoriaGarupa.DIA -> {

                    doDia.add(
                        registro
                    )

                    limitarMemoriaDoDia()
                }

                TipoMemoriaGarupa.LONGO_PRAZO -> {

                    if (
                        !jaExisteMemoriaSemelhante(
                            registro,
                            longoPrazo
                        )
                    ) {

                        longoPrazo.add(
                            registro
                        )
                    }
                }
            }
        }

        Log.d(
            "GARUPA_MEMORIA_VIDA",
            "📝 Memória registrada | " +
                    "tipo=${registro.tipo} | " +
                    "origem=${registro.origem} | " +
                    "importancia=${"%.2f".format(registro.importancia)} | " +
                    registro.texto
        )
    }

    /*
     * =========================================================
     * ATALHOS SEMÂNTICOS
     * =========================================================
     *
     * Estes métodos deixam o restante do Garupa mais
     * natural de ler.
     */

    fun lembrarConversa(
        texto: String,
        importancia: Double = 0.5
    ) {

        registrar(
            texto =
                texto,

            tipo =
                TipoMemoriaGarupa.RECENTE,

            origem =
                OrigemMemoriaGarupa.CONVERSA,

            importancia =
                importancia
        )
    }

    fun lembrarDoDia(
        texto: String,
        origem: OrigemMemoriaGarupa,
        importancia: Double = 0.6
    ) {

        registrar(
            texto =
                texto,

            tipo =
                TipoMemoriaGarupa.DIA,

            origem =
                origem,

            importancia =
                importancia
        )
    }

    fun aprender(
        texto: String,
        origem: OrigemMemoriaGarupa = OrigemMemoriaGarupa.APRENDIZADO,
        importancia: Double = 0.8
    ) {

        registrar(
            texto =
                texto,

            tipo =
                TipoMemoriaGarupa.LONGO_PRAZO,

            origem =
                origem,

            importancia =
                importancia
        )
    }

    /*
     * =========================================================
     * CONSULTA
     * =========================================================
     */

    fun obterRecentes(
        limite: Int = 10
    ): List<RegistroMemoriaGarupa> {

        synchronized(
            trava
        ) {

            return recentes
                .takeLast(
                    limite.coerceAtLeast(
                        0
                    )
                )
                .map {
                    it.copy()
                }
        }
    }

    fun obterMemoriasDoDia(
        limite: Int = 30
    ): List<RegistroMemoriaGarupa> {

        synchronized(
            trava
        ) {

            return doDia
                .takeLast(
                    limite.coerceAtLeast(
                        0
                    )
                )
                .map {
                    it.copy()
                }
        }
    }

    fun obterLongoPrazo(): List<RegistroMemoriaGarupa> {

        synchronized(
            trava
        ) {

            return longoPrazo
                .map {
                    it.copy()
                }
        }
    }

    /*
     * =========================================================
     * CONTEXTO PARA O CÉREBRO
     * =========================================================
     *
     * Mais adiante o GarupaCerebro poderá pedir este
     * resumo antes de interpretar uma fala.
     *
     * Ainda não estamos gerando respostas aqui.
     * Memória só fornece contexto.
     */

    fun construirContexto(): String {

        synchronized(
            trava
        ) {

            val partes =
                mutableListOf<String>()

            if (
                recentes.isNotEmpty()
            ) {

                partes.add(
                    buildString {

                        append(
                            "Memórias recentes:\n"
                        )

                        recentes
                            .takeLast(
                                8
                            )
                            .forEach { memoria ->

                                append(
                                    "- "
                                )

                                append(
                                    memoria.texto
                                )

                                append(
                                    "\n"
                                )
                            }
                    }.trim()
                )
            }

            if (
                doDia.isNotEmpty()
            ) {

                partes.add(
                    buildString {

                        append(
                            "Contexto do dia:\n"
                        )

                        doDia
                            .takeLast(
                                12
                            )
                            .forEach { memoria ->

                                append(
                                    "- "
                                )

                                append(
                                    memoria.texto
                                )

                                append(
                                    "\n"
                                )
                            }
                    }.trim()
                )
            }

            if (
                longoPrazo.isNotEmpty()
            ) {

                partes.add(
                    buildString {

                        append(
                            "Memórias de longo prazo:\n"
                        )

                        longoPrazo
                            .sortedByDescending {
                                it.importancia
                            }
                            .take(
                                15
                            )
                            .forEach { memoria ->

                                append(
                                    "- "
                                )

                                append(
                                    memoria.texto
                                )

                                append(
                                    "\n"
                                )
                            }
                    }.trim()
                )
            }

            return partes
                .joinToString(
                    separator =
                        "\n\n"
                )
        }
    }

    /*
     * =========================================================
     * LIMPEZA DE CONTEXTO TRANSITÓRIO
     * =========================================================
     */

    fun limparRecentes() {

        synchronized(
            trava
        ) {

            recentes.clear()
        }

        Log.d(
            "GARUPA_MEMORIA_VIDA",
            "🧹 Memória recente limpa"
        )
    }

    fun iniciarNovoDia() {

        synchronized(
            trava
        ) {

            recentes.clear()

            doDia.clear()
        }

        /*
         * Longo prazo NÃO é apagado.
         */

        Log.d(
            "GARUPA_MEMORIA_VIDA",
            "🌅 Novo dia iniciado | memória de longo prazo preservada"
        )
    }

    /*
     * =========================================================
     * CONTROLE DE TAMANHO
     * =========================================================
     */

    private fun limitarRecentes() {

        val limite =
            30

        while (
            recentes.size >
            limite
        ) {

            recentes.removeAt(
                0
            )
        }
    }

    private fun limitarMemoriaDoDia() {

        val limite =
            200

        while (
            doDia.size >
            limite
        ) {

            doDia.removeAt(
                0
            )
        }
    }

    /*
     * =========================================================
     * DEDUPLICAÇÃO SIMPLES
     * =========================================================
     *
     * Evita que um padrão de longo prazo seja registrado
     * repetidamente com exatamente o mesmo conteúdo.
     *
     * Aprendizado probabilístico mais sofisticado entra
     * depois.
     */

    private fun jaExisteMemoriaSemelhante(
        nova: RegistroMemoriaGarupa,
        existentes: List<RegistroMemoriaGarupa>
    ): Boolean {

        val textoNovo =
            normalizar(
                nova.texto
            )

        return existentes.any { existente ->

            normalizar(
                existente.texto
            ) ==
                    textoNovo
        }
    }

    private fun normalizar(
        texto: String
    ): String {

        return texto
            .lowercase()
            .replace(
                Regex("[^a-z0-9áàâãéèêíïóôõöúç ]"),
                ""
            )
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }
}
