package com.materialmail.agent.model

/**
 * AI 模型接入配置（OpenAI 兼容协议）。
 *
 * 设计约束：
 *  - 只走 OpenAI 兼容 /chat/completions——国内主流厂商（DeepSeek/通义/Moonshot/智谱）
 *    都提供兼容端点，一套代码全覆盖，不维护 N 套 SDK；
 *  - API Key 单独存 core:crypto 的 CredentialStore（Keystore 加密），
 *    本配置类只保存非敏感字段；
 *  - 默认 DeepSeek：中国大陆可直连，不让用户一上来就配代理。
 */
data class ModelProviderPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val hint: String,
)

object ModelProviders {
    val DEEPSEEK = ModelProviderPreset(
        id = "deepseek",
        label = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat",
        hint = "中国大陆直连 · platform.deepseek.com 获取 API Key",
    )
    val QWEN = ModelProviderPreset(
        id = "qwen",
        label = "通义千问",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultModel = "qwen-plus",
        hint = "阿里云百炼 · bailian.console.aliyun.com 获取 API Key",
    )
    val MOONSHOT = ModelProviderPreset(
        id = "moonshot",
        label = "Moonshot Kimi",
        baseUrl = "https://api.moonshot.cn/v1",
        defaultModel = "moonshot-v1-8k",
        hint = "platform.moonshot.cn 获取 API Key",
    )
    val ZHIPU = ModelProviderPreset(
        id = "zhipu",
        label = "智谱 GLM",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        defaultModel = "glm-4-flash",
        hint = "bigmodel.cn 获取 API Key（glm-4-flash 免费）",
    )
    val OPENAI = ModelProviderPreset(
        id = "openai",
        label = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        hint = "需要可访问 OpenAI 的网络环境",
    )
    val CUSTOM = ModelProviderPreset(
        id = "custom",
        label = "自定义（OpenAI 兼容）",
        baseUrl = "",
        defaultModel = "",
        hint = "任何兼容 /chat/completions 的端点：Ollama / LM Studio / one-api 等",
    )

    val ALL = listOf(DEEPSEEK, QWEN, MOONSHOT, ZHIPU, OPENAI, CUSTOM)

    fun byId(id: String): ModelProviderPreset = ALL.firstOrNull { it.id == id } ?: CUSTOM
}

/** 非敏感配置（API Key 在 CredentialStore）。 */
data class ModelConfig(
    val providerId: String = ModelProviders.DEEPSEEK.id,
    val baseUrl: String = ModelProviders.DEEPSEEK.baseUrl,
    val model: String = ModelProviders.DEEPSEEK.defaultModel,
) {
    val preset: ModelProviderPreset get() = ModelProviders.byId(providerId)
}
