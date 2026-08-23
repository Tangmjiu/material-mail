plugins {
    id("materialmail.android.library")
    id("materialmail.android.compose")
}

android {
    namespace = "com.materialmail.feature.composer"
}

dependencies {
    implementation(project(":designsystem"))
}
