package br.com.garupa.app.core.oferta

/*
 * =============================================================
 * TIPO SEMÂNTICO DE EVIDÊNCIA DA OFERTA
 * =============================================================
 *
 * Esta classe NÃO decide se devemos aceitar uma corrida.
 *
 * Ela descreve apenas:
 *
 * "O que este pedaço de informação parece representar?"
 *
 * Isso permite separar:
 *
 * - leitura da tela;
 * - compreensão da oferta;
 * - decisão do Garupa.
 *
 * A classificação poderá evoluir conforme o Garupa
 * acumular evidências e aprender novos padrões.
 * =============================================================
 */

enum class TipoEvidenciaOferta {

    /*
     * Valor financeiro da oferta.
     *
     * Exemplo:
     * R$ 11,69
     * R$ 15,23
     */
    VALOR,

    /*
     * Informação de distância.
     *
     * Exemplo:
     * Distância total 8,6 km
     * 7,3 km total
     */
    DISTANCIA,

    /*
     * Rótulo explícito indicando uma parada.
     *
     * Exemplo:
     * Coleta
     * Coleta 1
     * Entrega
     * Entrega 1
     */
    ROTULO_PARADA,

    /*
     * Nome de restaurante, estabelecimento,
     * condomínio, local ou referência que
     * possa fazer parte de uma parada.
     *
     * Exemplos observados:
     *
     * Barceloneta
     * Piola - Alphaville
     * Hino'motto Sushi Delivery!
     */
    NOME_LOCAL,

    /*
     * Endereço reconhecido com evidências
     * suficientes de via + número.
     *
     * Exemplo:
     * Alameda Campinas, 705, Alphaville
     * Av. Dr. Carlos de Moraes Barros, 362
     */
    ENDERECO,

    /*
     * Complemento de localização.
     *
     * Pode ser bairro, cidade, estado ou
     * continuação de um endereço.
     *
     * Exemplo:
     * Campesina, Osasco - SP
     */
    COMPLEMENTO_LOCAL,

    /*
     * Informação temporal relacionada
     * à execução da oferta.
     *
     * Exemplo:
     * Entrega até 22:44
     * 31 min
     */
    TEMPO,

    /*
     * Elemento de ação da interface.
     *
     * Exemplos:
     * Aceitar
     * Recusar
     * Rejeitar
     * Pegar
     */
    ACAO,

    /*
     * Texto aparentemente pertencente
     * ao mapa e não necessariamente
     * à descrição da oferta.
     *
     * Exemplos que vimos:
     *
     * CHÁCARAS
     * MARCO
     * VERTE
     * VILLE
     * Carapicuíba
     *
     * IMPORTANTE:
     * isso é uma hipótese semântica,
     * não uma exclusão definitiva.
     */
    CONTEXTO_MAPA,

    /*
     * Informação que parece pertencer
     * à oferta, mas ainda não sabemos
     * exatamente qual papel exerce.
     *
     * Ela deve ser preservada para que
     * camadas posteriores possam usar
     * posição, vizinhança e histórico.
     */
    CONTEXTO_OFERTA,

    /*
     * Evidência que ainda não conseguimos
     * interpretar com confiança.
     *
     * DESCONHECIDA não significa inútil.
     * Significa apenas:
     *
     * "ainda não sei".
     *
     * Isso é importante para a evolução
     * futura do Garupa.
     */
    DESCONHECIDA
}