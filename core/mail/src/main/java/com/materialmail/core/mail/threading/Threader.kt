package com.materialmail.core.mail.threading

import java.time.Instant

/** Threader 的输入：只需要 threading 相关的头部字段，与 Message 模型解耦。 */
data class ThreadingInput(
    val messageIdHeader: String,
    val inReplyTo: String?,
    val references: List<String>,
    val subject: String,
    val fromAddress: String,
    val sentAt: Instant,
)

/** Threading 结果树节点。[message] 为 null 表示"幽灵容器"（引用到了本地没有的消息）。 */
data class ThreadNode(
    val message: ThreadingInput?,
    val children: List<ThreadNode>,
)

/**
 * JWZ threading 算法（https://www.jwz.org/doc/threading.html 的实现）：
 *
 * 1. 按 Message-ID 建容器表；
 * 2. 按 References / In-Reply-To 链建树（检测并打断循环引用）；
 * 3. 无父容器晋升为根；
 * 4. 剪掉空的中间容器；
 * 5. 按规范化主题合并根（引用链断掉的回复归并到同主题线程）；
 * 6. 顶级按最新消息时间降序。
 *
 * 纯 Kotlin 实现，无 Android / jakarta 依赖，可单测。
 */
object Threader {

    private class Container {
        var message: ThreadingInput? = null
        var parent: Container? = null
        val children = mutableListOf<Container>()

        /** target 是否是本节点的祖先（用于打断引用环）。 */
        fun hasAncestor(target: Container): Boolean {
            var current = parent
            while (current != null) {
                if (current === target) return true
                current = current.parent
            }
            return false
        }
    }

    fun thread(messages: List<ThreadingInput>): List<ThreadNode> {
        if (messages.isEmpty()) return emptyList()

        val idTable = mutableMapOf<String, Container>()
        fun containerFor(id: String): Container = idTable.getOrPut(id) { Container() }

        // 1+2：建容器、按引用链连接
        for (message in messages) {
            val container = containerFor(message.messageIdHeader)
            container.message = message

            val refs = buildList {
                addAll(message.references)
                message.inReplyTo?.let(::add)
            }
            var previous: Container? = null
            for (ref in refs) {
                val refContainer = containerFor(ref)
                if (previous != null && refContainer.parent == null &&
                    refContainer !== previous && !refContainer.hasAncestor(previous) &&
                    !previous.hasAncestor(refContainer)
                ) {
                    refContainer.parent = previous
                    previous.children.add(refContainer)
                }
                previous = refContainer
            }
            if (previous != null && container.parent == null &&
                container !== previous && !container.hasAncestor(previous)
            ) {
                container.parent = previous
                previous.children.add(container)
            }
        }

        // 3：收集根集合
        val roots = idTable.values.filterTo(mutableListOf()) { it.parent == null }

        // 4：剪掉空的中间容器（空叶子直接删除，单亲空容器被孩子上提替代）
        fun prune(container: Container) {
            val iterator = container.children.iterator()
            while (iterator.hasNext()) {
                val child = iterator.next()
                when {
                    child.message == null && child.children.isEmpty() -> iterator.remove()
                    child.message == null -> {
                        iterator.remove()
                        for (grandChild in child.children) {
                            grandChild.parent = container
                            if (grandChild !in container.children) container.children.add(grandChild)
                        }
                        prune(container)
                        return
                    }
                    else -> prune(child)
                }
            }
        }

        val realRoots = mutableListOf<Container>()
        for (root in roots) {
            if (root.message == null) {
                // 幽灵根不晋升，孩子上提为根
                for (child in root.children) {
                    child.parent = null
                    if (child !in realRoots) realRoots.add(child)
                }
            } else {
                realRoots.add(root)
            }
        }
        realRoots.forEach(::prune)

        // 5：按规范化主题合并根（JWZ：败者整树挂到胜者下面）
        val subjectTable = mutableMapOf<String, Container>()
        val mergedRoots = mutableListOf<Container>()
        for (root in realRoots) {
            val subject = normalizeSubject(root.message?.subject ?: findSubject(root))
            val existing = if (subject.isEmpty()) null else subjectTable[subject]
            when {
                subject.isEmpty() || existing == null -> {
                    if (subject.isNotEmpty()) subjectTable[subject] = root
                    mergedRoots.add(root)
                }

                // 幽灵根让位给真实根
                existing.message == null && root.message != null -> {
                    existing.parent = root
                    root.children.add(existing)
                    subjectTable[subject] = root
                    mergedRoots.remove(existing)
                    mergedRoots.add(root)
                }

                existing.message != null && root.message == null -> {
                    root.parent = existing
                    existing.children.add(root)
                }

                // 双真实根：非回复者 / 较早者优先，败者整树挂到胜者下
                else -> {
                    val existingIsReply = isReplySubject(existing.message!!.subject)
                    val rootIsReply = isReplySubject(root.message!!.subject)
                    val winner = when {
                        existingIsReply && !rootIsReply -> existing
                        !existingIsReply && rootIsReply -> root
                        else -> if (root.message!!.sentAt < existing.message!!.sentAt) root else existing
                    }
                    val loser = if (winner === existing) root else existing
                    loser.parent = winner
                    winner.children.add(loser)
                    subjectTable[subject] = winner
                    mergedRoots.remove(loser)
                    if (winner !in mergedRoots) mergedRoots.add(winner)
                }
            }
        }

        // 6：顶级按最新消息时间降序
        return mergedRoots
            .sortedByDescending(::latestInstant)
            .map(::toNode)
    }

    private fun toNode(container: Container): ThreadNode = ThreadNode(
        message = container.message,
        children = container.children.map(::toNode),
    )

    private fun findSubject(container: Container): String =
        container.children.firstNotNullOfOrNull {
            it.message?.subject ?: findSubject(it).takeIf(String::isNotEmpty)
        } ?: ""

    private fun latestInstant(container: Container): Instant =
        (listOfNotNull(container.message?.sentAt) + container.children.map(::latestInstant)).max()

    private val REPLY_PREFIX_REGEX = Regex("(?i)^(re|fw|fwd|答复|回复|转发)[:：]\\s*")

    /** 规范化主题：循环去除 Re:/Fwd:/答复:/转发: 前缀，折叠空白。 */
    fun normalizeSubject(subject: String): String {
        var result = subject.trim()
        while (true) {
            val stripped = REPLY_PREFIX_REGEX.replaceFirst(result, "").trim()
            if (stripped == result) break
            result = stripped
        }
        return result.replace(Regex("\\s+"), " ")
    }

    private fun isReplySubject(subject: String): Boolean =
        REPLY_PREFIX_REGEX.containsMatchIn(subject.trim())
}