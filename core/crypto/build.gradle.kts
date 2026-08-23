plugins {
    id("materialmail.android.library")
}

android {
    namespace = "com.materialmail.core.crypto"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}