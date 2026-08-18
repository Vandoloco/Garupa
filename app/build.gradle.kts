plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // OCR - leitura dos pedidos na tela
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Localização atual do piloto
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // IA local - LiteRT-LM
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.14.0")

    /*
     * IMPORTANTE
     *
     * LiteRT-LM 0.14.0 foi compilado esperando
     * kotlinx-coroutines 1.11.0.
     *
     * Sem esta versão ocorre:
     *
     * NoSuchMethodError:
     * SendChannel.close$default(...)
     *
     * ao finalizar sendMessageAsync().
     */
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

/*
 * Força todas as dependências transitivas a usarem
 * a mesma versão de coroutines.
 *
 * Isso evita que alguma biblioteca traga 1.9.x
 * ou 1.10.x e cause o crash do LiteRT-LM.
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