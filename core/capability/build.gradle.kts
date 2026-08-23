plugins {
    id("materialmail.android.library")
}

android {
    namespace = "com.materialmail.core.capability"
}

dependencies {
    implementation(project(":core:model"))
}
