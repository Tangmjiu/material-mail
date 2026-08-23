plugins {
    id("materialmail.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.materialmail.core.model"
}

dependencies {
    // 仅用于 @Serializable 注解与 JSON 编解码，模型本身保持无 Android 依赖
    implementation(libs.kotlinx.serialization.json)
}