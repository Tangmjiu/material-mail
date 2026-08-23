plugins {
    id("materialmail.android.library")
}

android {
    namespace = "com.materialmail.agent"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:capability"))
}
