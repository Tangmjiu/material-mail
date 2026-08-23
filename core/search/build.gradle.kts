plugins {
    id("materialmail.android.library")
}

android {
    namespace = "com.materialmail.core.search"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:capability"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}