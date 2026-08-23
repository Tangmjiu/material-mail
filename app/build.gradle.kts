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
    // 阶段 0：feature 模块暂不挂 UI，后续阶段接线
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
