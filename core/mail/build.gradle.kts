plugins {
    id("materialmail.android.library")
}

android {
    namespace = "com.materialmail.core.mail"
}

dependencies {
    implementation(project(":core:model"))
}
