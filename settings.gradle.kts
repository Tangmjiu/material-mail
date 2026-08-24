pluginManagement {
    repositories {
        // 中国大陆镜像优先，官方源兜底
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "material-mail"

// ── App Shell ─────────────────────────────
include(":app")
include(":app-shell")

// ── Core（纯邮箱领域，永远不许依赖 Pro/Agent/Region/UI）──
include(":core:model")
include(":core:database")
include(":core:mail")
include(":core:sync")
include(":core:search")
include(":core:capability")
include(":core:crypto")

// ── Design System ─────────────────────────
include(":designsystem")

// ── Feature ───────────────────────────────
include(":feature:inbox")
include(":feature:composer")
include(":feature:settings")
include(":feature:account")

// ── Agent / Region（独立可摘除模块）─────────
include(":agent")
include(":region")

// ── Pro（闭源，仅私有完整产品仓库存在 pro/ 目录）─────────────
// 双重保护：目录存在性 + 显式开关。开源社区版仓库没有 pro/ 目录，
// 物理上不可能把 Pro 代码编进 Community 产物（Open Core 铁律）。
val proEnabled = providers.gradleProperty("materialmail.includePro").orNull?.toBoolean() ?: true
if (proEnabled && java.io.File(rootDir, "pro").isDirectory) {
    // Pro 模块（闭源，仅私有完整产品仓库）：
    include(":pro:licensing")
    include(":pro:automation")
    include(":pro:advanced-search")
    include(":pro:productivity")
    include(":pro:connectors")
    include(":pro:personalization")
    include(":pro:stats")
    include(":pro:app")
}
