plugins {
    id("materialmail.android.library")
    id("materialmail.android.compose")
}

android {
    namespace = "com.materialmail.designsystem"
}

dependencies {
    implementation(libs.compose.material.icons.extended)
}
