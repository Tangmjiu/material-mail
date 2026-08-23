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
