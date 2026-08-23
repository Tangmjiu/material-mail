plugins {
    id("materialmail.android.library")
}

android {
    namespace = "com.materialmail.core.search"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
}
