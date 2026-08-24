plugins {
    id("materialmail.android.application")
    id("materialmail.android.compose")
}

android {
    packaging {
        resources {
            excludes += listOf(
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/LICENSE",
                "META-INF/DEPENDENCIES",
            )
        }
    }
    namespace = "com.materialmail.app"

    defaultConfig {
        applicationId = "com.materialmail"
    }
}

dependencies {
    implementation(project(":app-shell"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
}