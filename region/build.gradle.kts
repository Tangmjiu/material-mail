plugins {
    id("materialmail.android.library")
}

android {
    namespace = "com.materialmail.region"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:capability"))
}
