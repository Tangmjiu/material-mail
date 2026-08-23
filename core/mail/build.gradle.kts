plugins {
    id("materialmail.android.library")
}

android {
    namespace = "com.materialmail.core.mail"
}

dependencies {
    implementation(project(":core:model"))

    // Jakarta Mail（Angus 实现）：IMAP/SMTP/MIME 引擎。
    // 选型理由：IMAP 正确性（IDLE/UIDVALIDITY/中文文件夹名）是本项目最大
    // 技术风险，自研协议栈不可接受；所有访问收敛在本模块的 facade 之后，
    // 未来可替换。
    implementation(libs.angus.mail)
    implementation(libs.jakarta.activation.api)
    implementation(libs.angus.activation)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}