plugins {
    id("materialmail.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.materialmail.core.database"
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core:model"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.sqlite.framework)

    // SQLCipher：本地加密可选项（默认关闭，由用户设置开启，见 DatabaseFactory）
    implementation(libs.sqlcipher.android)
}