plugins {
    id("materialmail.android.library")
    id("materialmail.android.compose")
}

android {
    namespace = "com.materialmail.appshell"
}

dependencies {
    api(project(":designsystem"))
    api(project(":feature:inbox"))
    api(project(":feature:composer"))
    api(project(":feature:account"))
    api(project(":feature:settings"))
    api(project(":core:model"))
    api(project(":core:database"))
    api(project(":core:sync"))
    api(project(":core:mail"))
    api(project(":core:crypto"))
    api(project(":core:search"))
    api(project(":core:capability"))
    api(project(":agent"))
    api(project(":region"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.androidx.glance.appwidget)
}