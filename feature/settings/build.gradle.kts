plugins {
    id("materialmail.android.library")
    id("materialmail.android.compose")
}

android {
    namespace = "com.materialmail.feature.settings"
}

dependencies {
    implementation(project(":designsystem"))
}
