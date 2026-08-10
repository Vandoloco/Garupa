package br.com.garupa.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.garupa.app.ui.theme.GarupaTheme

@Composable
fun GarupaTela(
    modifier: Modifier = Modifier,
    mensagem: String = "🤖 Garupa: Pronto para rodar!"
) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "🏍️ GARUPA",
            fontSize = 32.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Seu copiloto inteligente"
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = mensagem,
            fontSize = 18.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GarupaTelaPreview() {
    GarupaTheme {
        GarupaTela()
    }
}