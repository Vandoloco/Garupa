package br.com.garupa.app.core.acessibilidade

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GarupaAccessibilityService : AccessibilityService() {

    private var ultimoEvento = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()

        Log.d(
            "GARUPA",
            "👁️ Serviço de acessibilidade conectado"
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (event == null) {
            return
        }

        val agora = System.currentTimeMillis()

        if (agora - ultimoEvento < 500) {
            return
        }

        ultimoEvento = agora

        val pacote = event.packageName?.toString() ?: "desconhecido"
        val classe = event.className?.toString() ?: "desconhecida"

        val textoEvento = event.text
            .map { it.toString() }
            .filter { it.isNotBlank() }
            .joinToString(" | ")

        Log.d(
            "GARUPA_EVENTO",
            "App: $pacote | Classe: $classe | Texto: $textoEvento"
        )

        val raiz = rootInActiveWindow ?: return

        val textos = mutableListOf<String>()

        coletarTextos(
            node = raiz,
            textos = textos
        )

        val textosUnicos = textos
            .filter { it.isNotBlank() }
            .distinct()

        if (textosUnicos.isNotEmpty()) {

            Log.d(
                "GARUPA_TELA",
                textosUnicos.joinToString(" | ")
            )
        }
    }

    private fun coletarTextos(
        node: AccessibilityNodeInfo?,
        textos: MutableList<String>
    ) {

        if (node == null) {
            return
        }

        node.text
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                textos.add(it)
            }

        node.contentDescription
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                textos.add(it)
            }

        for (i in 0 until node.childCount) {
            coletarTextos(
                node = node.getChild(i),
                textos = textos
            )
        }
    }

    override fun onInterrupt() {

        Log.d(
            "GARUPA",
            "👁️ Serviço de acessibilidade interrompido"
        )
    }
}