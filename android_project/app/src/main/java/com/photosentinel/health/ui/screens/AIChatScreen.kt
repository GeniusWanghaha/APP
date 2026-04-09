package com.photosentinel.health.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photosentinel.health.ui.theme.AccentCyan
import com.photosentinel.health.ui.theme.BgCard
import com.photosentinel.health.ui.theme.BgDeep
import com.photosentinel.health.ui.theme.BgPrimary
import com.photosentinel.health.ui.theme.DividerColor
import com.photosentinel.health.ui.theme.StatusExcellent
import com.photosentinel.health.ui.theme.StatusPoor
import com.photosentinel.health.ui.theme.TextPrimary
import com.photosentinel.health.ui.theme.TextSecondary
import com.photosentinel.health.ui.theme.TextTertiary
import com.photosentinel.health.ui.viewmodel.ChatMessage
import com.photosentinel.health.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel()
) {
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "健康问答",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (viewModel.isServerOnline) StatusExcellent
                                    else StatusPoor
                                )
                        )
                        Text(
                            text = if (viewModel.isServerOnline) "已连接" else "未连接",
                            fontSize = 12.sp,
                            color = TextTertiary
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BgPrimary
            ),
            actions = {
                IconButton(onClick = { viewModel.checkServer() }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = TextTertiary
                    )
                }
                IconButton(onClick = { viewModel.clearChat() }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "清空",
                        tint = TextTertiary
                    )
                }
            }
        )

        viewModel.errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = StatusPoor.copy(alpha = 0.08f)
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(12.dp),
                    color = StatusPoor,
                    fontSize = 13.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            itemsIndexed(
                items = viewModel.messages,
                key = { index, message -> "${message.timestamp}_$index" }
            ) { _, message ->
                ChatBubble(message = message)
            }

            if (viewModel.isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = BgDeep
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "分析中",
                                    color = TextTertiary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                // 三个圆点表示加载
                                repeat(3) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(TextTertiary.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        // 输入区域
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = BgCard,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "输入 ECG/PPG 指标问题...",
                            color = TextTertiary
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 4,
                    enabled = !viewModel.isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = DividerColor,
                        focusedContainerColor = BgPrimary,
                        unfocusedContainerColor = BgPrimary
                    )
                )
                FilledIconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !viewModel.isLoading) {
                            viewModel.sendMessage(inputText.trim())
                            inputText = ""
                        }
                    },
                    modifier = Modifier.size(44.dp),
                    enabled = inputText.isNotBlank() && !viewModel.isLoading,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = AccentCyan,
                        contentColor = Color.White,
                        disabledContainerColor = DividerColor,
                        disabledContentColor = TextTertiary
                    )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.isFromUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = when {
                message.isError -> StatusPoor.copy(alpha = 0.08f)
                isUser -> AccentCyan
                else -> BgDeep
            }
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = when {
                    message.isError -> StatusPoor
                    isUser -> Color.White
                    else -> TextPrimary
                },
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}
