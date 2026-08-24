plugins {
    id("materialmail.android.application")
    id("materialmail.android.compose")
}

android {
    namespace = "com.materialmail.app"

    defaultConfig {
        applicationId = "com.materialmail"
    }
}

dependencies {
    implementation(project(":app-shell"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}