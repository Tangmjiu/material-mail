import com.android.build.api.dsl.CommonExtension

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<CommonExtension<*, *, *, *, *, *>>("android") {
    buildFeatures {
        compose = true
    }
}

dependencies {
    "implementation"(platform("androidx.compose:compose-bom:2025.06.01"))
    "implementation"("androidx.compose.ui:ui")
    "implementation"("androidx.compose.ui:ui-tooling-preview")
    "implementation"("androidx.compose.material3:material3:1.5.0-alpha12")
    "debugImplementation"("androidx.compose.ui:ui-tooling")
}
