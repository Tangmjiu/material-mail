plugins {
    id("materialmail.android.library")
}

android {
    namespace = "com.materialmail.core.database"
}

dependencies {
    implementation(project(":core:model"))
}
