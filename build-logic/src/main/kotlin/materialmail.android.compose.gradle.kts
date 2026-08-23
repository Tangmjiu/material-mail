import com.android.build.api.dsl.CommonExtension

plugins {
    alias(libs.plugins.kotlin.compose)
}

extensions.configure<CommonExtension<*, *, *, *, *, *>> {
    buildFeatures {
        compose = true
    }
}

dependencies {
    val bom = platform(libs.compose.bom)
    "implementation"(bom)
    "implementation"(libs.compose.ui)
    "implementation"(libs.compose.ui.tooling.preview)
    "implementation"(libs.compose.material3)
    "debugImplementation"(libs.compose.ui.tooling)
}
