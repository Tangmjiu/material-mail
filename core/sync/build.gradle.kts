plugins {
    id("materialmail.android.library")
}

android {
    namespace = "com.materialmail.core.sync"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
}
