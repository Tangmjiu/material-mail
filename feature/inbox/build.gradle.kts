plugins {
    id("materialmail.android.library")
    id("materialmail.android.compose")
}

android {
    namespace = "com.materialmail.feature.inbox"
}

dependencies {
    implementation(project(":designsystem"))
}
