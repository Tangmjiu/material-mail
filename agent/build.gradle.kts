plugins {
    id("materialmail.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.materialmail.agent"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:capability"))
    implementation(project(":core:database"))
    implementation(project(":core:crypto"))
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}