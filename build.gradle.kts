plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

/**
 * 模块边界守卫（最高优先级架构约束的机器化执行）：
 *   :core:*       只能依赖 :core:*
 *   :designsystem 不依赖任何项目内模块
 *   :agent/:region 只能依赖 :core:* 和 :designsystem
 *   :feature:*    可依赖 :core:* / :designsystem / :region / :agent
 *   :app          组装层，不受限
 * 未来 Pro 模块加入时在此追加规则：:core 与 :community 代码永远不得依赖 :pro:*
 */
tasks.register("checkModuleBoundaries") {
    group = "verification"
    description = "校验模块依赖方向，防止 Core 被污染"
    notCompatibleWithConfigurationCache("读取项目依赖图")
    doLast {
        val violations = mutableListOf<String>()
        fun allowed(projectPath: String, dep: String): Boolean = when {
            projectPath.startsWith(":core:") -> dep.startsWith(":core:")
            projectPath == ":designsystem" -> false
            projectPath == ":agent" || projectPath == ":region" ->
                dep.startsWith(":core:") || dep == ":designsystem"
            projectPath.startsWith(":feature:") ->
                dep.startsWith(":core:") || dep == ":designsystem" || dep == ":region" || dep == ":agent"
            // Community 壳物理上不得依赖 Pro
            projectPath == ":app" -> !dep.startsWith(":pro:")
            projectPath == ":app-shell" -> !dep.startsWith(":pro:")
            // Pro 壳（完整产品组装层）：不受限，负责接线 Community + Pro
            projectPath == ":pro:app" -> true
            // Pro 业务模块：只依赖 Core/设计系统/能力层/Pro 自身，不反向依赖 Community feature
            // （Community 页面复用经由 :pro:app 壳组装，业务模块保持解耦）
            projectPath.startsWith(":pro:") ->
                dep.startsWith(":core:") || dep == ":designsystem" ||
                    dep == ":agent" || dep == ":region" || dep.startsWith(":pro:")
            else -> true        }
        rootProject.subprojects.forEach { p ->
            p.configurations.forEach { c ->
                c.dependencies.withType(org.gradle.api.artifacts.ProjectDependency::class.java).forEach { dep ->
                    val target = dep.dependencyProject.path
                    if (!allowed(p.path, target)) violations += "${p.path} 禁止依赖 $target"
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("模块边界违规：\n" + violations.joinToString("\n"))
        }
        logger.lifecycle("✔ 模块边界校验通过（${rootProject.subprojects.size} 个模块）")
    }
}
