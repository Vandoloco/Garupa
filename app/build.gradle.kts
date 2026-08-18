plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)

    id("com.google.gms.google-services")
}

android {
    namespace = "br.com.garupa.app"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "br.com.garupa.app"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {

    /*
     * =========================================================
     * ANDROID / COMPOSE
     * =========================================================
     */

    implementation(
        platform(
            libs.androidx.compose.bom
        )
    )

    implementation(
        libs.androidx.activity.compose
    )

    implementation(
        libs.androidx.compose.material3
    )

    implementation(
        libs.androidx.compose.ui
    )

    implementation(
        libs.androidx.compose.ui.graphics
    )

    implementation(
        libs.androidx.compose.ui.tooling.preview
    )

    implementation(
        libs.androidx.core.ktx
    )

    implementation(
        libs.androidx.lifecycle.runtime.ktx
    )

    /*
     * =========================================================
     * OCR
     * =========================================================
     *
     * Leitura dos pedidos exibidos na tela.
     */

    implementation(
        "com.google.mlkit:text-recognition:16.0.1"
    )

    /*
     * =========================================================
     * LOCALIZAÇÃO
     * =========================================================
     */

    implementation(
        "com.google.android.gms:play-services-location:21.3.0"
    )

    /*
     * =========================================================
     * IA LOCAL - GEMMA / LiteRT-LM
     * =========================================================
     *
     * Mantemos a versão fixa que já validamos
     * no Realme.
     */

    implementation(
        "com.google.ai.edge.litertlm:litertlm-android:0.14.0"
    )

    /*
     * LiteRT-LM 0.14.0 precisa das coroutines 1.11.0.
     *
     * Sem isso já comprovamos o crash:
     *
     * NoSuchMethodError:
     * SendChannel.close$default(...)
     */

    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0"
    )

    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0"
    )

    /*
     * =========================================================
     * FIREBASE
     * =========================================================
     */

    implementation(
        platform(
            "com.google.firebase:firebase-bom:34.16.0"
        )
    )

    /*
     * Firebase AI Logic.
     *
     * Este será o segundo motor de inteligência
     * do Garupa, usando Gemini pela internet.
     */

    implementation(
        "com.google.firebase:firebase-ai"
    )

    /*
     * App Check em modo de desenvolvimento.
     *
     * Depois, quando o Garupa estiver pronto para
     * produção, substituímos por um provedor de
     * produção adequado.
     */

    implementation(
        "com.google.firebase:firebase-appcheck-debug"
    )

    /*
     * =========================================================
     * TESTES
     * =========================================================
     */

    testImplementation(
        libs.junit
    )

    androidTestImplementation(
        platform(
            libs.androidx.compose.bom
        )
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )
}

/*
 * =============================================================
 * COROUTINES
 * =============================================================
 *
 * Forçamos todas as dependências transitivas a usarem
 * a mesma versão já validada com LiteRT-LM.
 */

configurations.configureEach {

    resolutionStrategy {

        force(
            "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0",
            "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0"
        )
    }
}