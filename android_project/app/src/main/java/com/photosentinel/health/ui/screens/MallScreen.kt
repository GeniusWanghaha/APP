package com.photosentinel.health.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photosentinel.health.domain.model.MallProduct
import com.photosentinel.health.ui.theme.AccentCyan
import com.photosentinel.health.ui.theme.BgCard
import com.photosentinel.health.ui.theme.BgPrimary
import com.photosentinel.health.ui.theme.BgSurface
import com.photosentinel.health.ui.theme.DividerColor
import com.photosentinel.health.ui.theme.StatusFair
import com.photosentinel.health.ui.theme.TextPrimary
import com.photosentinel.health.ui.theme.TextSecondary
import com.photosentinel.health.ui.theme.TextTertiary
import com.photosentinel.health.ui.viewmodel.MallViewModel

@Composable
fun MallScreen(
    modifier: Modifier = Modifier,
    viewModel: MallViewModel = viewModel()
) {
    val uiState = viewModel.uiState
    val products = uiState.products

    var keyword by rememberSaveable { mutableStateOf("") }
    var selectedTag by rememberSaveable { mutableStateOf("全部") }
    var selectedProductId by rememberSaveable { mutableStateOf<String?>(null) }

    val availableTags = listOf("全部") + products.mapNotNull { it.tag.takeIf { tag -> tag.isNotBlank() } }.distinct()
    val filteredProducts = products.filter { product ->
        val tagMatched = selectedTag == "全部" || product.tag == selectedTag
        val keywordMatched =
            keyword.isBlank() ||
                product.name.contains(keyword, ignoreCase = true) ||
                product.description.contains(keyword, ignoreCase = true) ||
                product.price.contains(keyword, ignoreCase = true)
        tagMatched && keywordMatched
    }
    val selectedProduct = selectedProductId?.let { id -> products.firstOrNull { it.id == id } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        if (uiState.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = AccentCyan,
                trackColor = DividerColor
            )
        }

        uiState.errorMessage?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = StatusFair,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ArchitectureOverviewCard(total = products.size)

            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索模块（名称/描述/层级）") }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableTags.forEach { tag ->
                    AssistChip(
                        onClick = { selectedTag = tag },
                        label = { Text(tag) },
                        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                            containerColor = if (selectedTag == tag) AccentCyan.copy(alpha = 0.16f) else BgCard,
                            labelColor = if (selectedTag == tag) AccentCyan else TextSecondary
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selectedTag == tag) AccentCyan.copy(alpha = 0.35f) else DividerColor
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("模块清单（${filteredProducts.size}）", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { viewModel.refresh() }) {
                    Text("刷新")
                }
            }

            if (filteredProducts.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = BgSurface)
                ) {
                    Text(
                        text = "当前筛选下无模块，建议清空关键词或切换标签。",
                        color = TextTertiary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.height(520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        ProductItemCard(
                            product = product,
                            onDetail = { selectedProductId = product.id }
                        )
                    }
                }
            }
        }
    }

    if (selectedProduct != null) {
        val details = selectedProduct.toDetailLines()
        AlertDialog(
            onDismissRequest = { selectedProductId = null },
            title = {
                Text(
                    text = "${selectedProduct.name} · ${selectedProduct.price}",
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(selectedProduct.description, color = TextSecondary)
                    details.forEach { line ->
                        Text("• $line", color = TextSecondary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedProductId = null }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun ArchitectureOverviewCard(total: Int) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AccountTree,
                    contentDescription = null,
                    tint = AccentCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("系统展示台", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "当前共 $total 个核心模块。建议答辩按“硬件采集 → 通信链路 → 手机算法 → AI解释”路径讲解。",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Memory, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                Icon(Icons.Outlined.NetworkCheck, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                Icon(Icons.Outlined.Analytics, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ProductItemCard(
    product: MallProduct,
    onDetail: () -> Unit
) {
    val icon = when (product.id) {
        "m1" -> Icons.Outlined.Memory
        "m2" -> Icons.Outlined.NetworkCheck
        "m3" -> Icons.Outlined.Analytics
        "m4" -> Icons.Outlined.SmartToy
        else -> Icons.Outlined.Hub
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetail),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        color = AccentCyan.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(product.name, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    if (product.tag.isNotBlank()) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(product.tag) },
                            colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                disabledContainerColor = AccentCyan.copy(alpha = 0.12f),
                                disabledLabelColor = AccentCyan
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(product.description, color = TextTertiary, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }

            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(product.price, color = AccentCyan, style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("详情", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun MallProduct.toDetailLines(): List<String> {
    return when (id) {
        "m1" -> listOf(
            "输入：ECG/PPG 原始模拟信号与状态位",
            "输出：带 FrameID 与微秒级时间戳的数字帧",
            "接口：ESP32-S3 + AD8232 + MAX30102 硬件组合"
        )
        "m2" -> listOf(
            "输入：硬件采样帧",
            "输出：BLE 数据包、状态包、控制回执",
            "关键：丢包检测、背压状态、CRC 校验"
        )
        "m3" -> listOf(
            "输入：同步后的 ECG/PPG 时间轴",
            "输出：HR/HRV、PTT/PWTT、SQI、节律风险",
            "策略：60 秒门控后统一发布真实指标"
        )
        "m4" -> listOf(
            "输入：结构化指标与风险事件",
            "输出：中文可读结论、趋势解释、复测建议",
            "边界：仅健康提示，不替代临床诊断"
        )
        else -> listOf("该模块暂无详细说明")
    }
}
