plugins {
    id("materialmail.android.library")
    id("materialmail.android.compose")
}

android {
    namespace = "com.materialmail.region"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:capability"))
    implementation(project(":designsystem"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
}