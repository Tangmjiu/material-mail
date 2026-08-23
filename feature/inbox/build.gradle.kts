plugins {
    id("materialmail.android.library")
    id("materialmail.android.compose")
}

android {
    namespace = "com.materialmail.feature.inbox"
}

dependencies {
    implementation(project(":designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:sync"))
    implementation(project(":core:capability"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
}