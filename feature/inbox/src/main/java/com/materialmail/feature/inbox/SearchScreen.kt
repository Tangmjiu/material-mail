package com.materialmail.feature.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialmail.designsystem.component.MailListItem
import com.materialmail.designsystem.theme.MailTheme
import com.materialmail.designsystem.theme.MailTypeScale

/**
 * 本地搜索。纯本地 FTS，零网络（Local-first）。
 * 结果行复用 MailListItem：同一套排版层级，搜索结果不另起设计语言。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onOpenThread: (threadId: String) -> Unit,
    modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    TextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChanged,
                        placeholder = {
                            Text(
                                "搜索邮件",
                                style = MailTypeScale.subject,
                                color = MaterialTheme.colorScheme.outline)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        textStyle = MailTypeScale.subject.copy(
                            color = MaterialTheme.colorScheme.onSurface),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface))
        }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.searching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            when {
                uiState.searchedEmpty -> Box(
                    modifier = Modifier.fillMaxSize().padding(MailTheme.spacing.xxl),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline)
                        Text(
                            "没有找到匹配的邮件",
                            style = MailTypeScale.preview,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "搜索范围：主题、摘要、发件人（本地索引）",
                            style = MailTypeScale.meta,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = uiState.results, key = { it.threadId }) { result ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenThread(result.threadId) }) {
                            MailListItem(
                                sender = result.senderLine,
                                subject = result.subject,
                                preview = result.snippet,
                                time = result.timeText,
                                unread = false, // 搜索结果不表达读/未读状态
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh)
                        }
                    }
                }
            }
        }
    }
}