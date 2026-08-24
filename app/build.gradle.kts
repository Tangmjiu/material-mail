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
    implementation(project(":designsystem"))
    implementation(project(":feature:inbox"))
    implementation(project(":feature:account"))
    implementation(project(":feature:composer"))
    implementation(project(":feature:settings"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:sync"))
    implementation(project(":core:crypto"))
    implementation(project(":core:search"))
    implementation(project(":agent"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.compose.material.icons.extended)
}