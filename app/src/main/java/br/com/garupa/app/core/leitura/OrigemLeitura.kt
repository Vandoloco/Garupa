package br.com.garupa.app.core.leitura

enum class OrigemLeitura {

    /*
     * Print escolhida manualmente
     * pela galeria durante os testes.
     */
    TESTE,

    /*
     * Frame produzido automaticamente
     * pelo serviço de captura de tela.
     */
    CAPTURA_CONTINUA
}