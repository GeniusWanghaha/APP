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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photosentinel.health.domain.model.HealthPlan
import com.photosentinel.health.domain.model.MallProduct
import com.photosentinel.health.domain.model.PlanCategory
import com.photosentinel.health.ui.components.SectionHeader
import com.photosentinel.health.ui.theme.AccentBlue
import com.photosentinel.health.ui.theme.AccentCyan
import com.photosentinel.health.ui.theme.AccentIndigo
import com.photosentinel.health.ui.theme.AccentOrange
import com.photosentinel.health.ui.theme.AccentTeal
import com.photosentinel.health.ui.theme.BgCard
import com.photosentinel.health.ui.theme.BgDeep
import com.photosentinel.health.ui.theme.BgPrimary
import com.photosentinel.health.ui.theme.DividerColor
import com.photosentinel.health.ui.theme.StatusExcellent
import com.photosentinel.health.ui.theme.StatusFair
import com.photosentinel.health.ui.theme.StatusPoor
import com.photosentinel.health.ui.theme.TextPrimary
import com.photosentinel.health.ui.theme.TextSecondary
import com.photosentinel.health.ui.theme.TextTertiary
import com.photosentinel.health.ui.viewmodel.HealthPlanViewModel
import com.photosentinel.health.ui.viewmodel.MallViewModel

/**
 * 执行台 — 合并原"方案"与"展示"
 */
@Composable
fun ExecutionScreen(
    modifier: Modifier = Modifier,
    planViewModel: HealthPlanViewModel = viewModel(),
    mallViewModel: MallViewModel = viewModel()
) {
    val planState = planViewModel.uiState
    val mallState = mallViewModel.uiState
    val plans = planState.plans
    val products = mallState.products

    val completedCount = plans.count { it.isCompleted }
    val pendingPlans = plans.filterNot { it.isCompleted }
    val progress = if (plans.isNotEmpty()) completedCount.toFloat() / plans.size else 0f

    var keyword by rememberSaveable { mutableStateOf("") }
    var selectedCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedCategory = selectedCategoryName?.let { PlanCategory.valueOf(it) }
    var selectedProductId by rememberSaveable { mutableStateOf<String?>(null) }

    val filteredPlans = plans.filter { plan ->
        val categoryMatched = selectedCategory == null || plan.category == selectedCategory
        val keywordMatched = keyword.isBlank() ||
            plan.title.contains(keyword, ignoreCase = true) ||
            plan.description.contains(keyword, ignoreCase = true)
        categoryMatched && keywordMatched
    }

    val selectedProduct = selectedProductId?.let { id -> products.firstOrNull { it.id == id } }

    val isLoading = planState.isLoading || mallState.isLoading
    val errorMessage = planState.errorMessage ?: mallState.errorMessage

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Loading
        if (isLoading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentCyan,
                    trackColor = DividerColor
                )
            }
        }

        errorMessage?.let { error ->
            item {
                Text(error, style = MaterialTheme.typography.bodySmall, color = StatusFair,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        // ═══════════ 执行方案 ═══════════
        item { Spacer(modifier = Modifier.height(4.dp)) }
        item { SectionHeader(title = "执行方案") }

        // 进度卡片
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "已完成 $completedCount / ${plans.size} 项",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, color = TextPrimary
                        )
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = AccentCyan, fontWeight = FontWeight.SemiBold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AccentCyan, trackColor = DividerColor
                    )
                    Text(
                        text = if (pendingPlans.isNotEmpty()) "下一步: ${pendingPlans.first().title}"
                        else "所有步骤已完成。",
                        style = MaterialTheme.typography.bodySmall, color = TextTertiary
                    )
                }
            }
        }

        // 搜索 + 分类
        item {
            OutlinedTextField(
                value = keyword, onValueChange = { keyword = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("搜索步骤", color = TextTertiary) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan, unfocusedBorderColor = DividerColor,
                    focusedContainerColor = BgCard, unfocusedContainerColor = BgCard
                )
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryChip("全部", selectedCategory == null) { selectedCategoryName = null }
                PlanCategory.values().forEach { cat ->
                    CategoryChip(cat.toLabel(), selectedCategory == cat) { selectedCategoryName = cat.name }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { planViewModel.setAllPlansCompleted(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp), enabled = plans.isNotEmpty()
                ) { Text("全部完成") }
                OutlinedButton(
                    onClick = { planViewModel.setAllPlansCompleted(false) },
                    enabled = plans.isNotEmpty(), shape = RoundedCornerShape(12.dp)
                ) { Text("重置") }
                OutlinedButton(
                    onClick = { planViewModel.refresh() }, shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text("刷新")
                }
            }
        }

        // 步骤列表
        if (filteredPlans.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = BgDeep)) {
                    Text("当前筛选条件下没有步骤。", color = TextTertiary, modifier = Modifier.padding(16.dp))
                }
            }
        } else {
            items(filteredPlans, key = { it.id }) { plan ->
                PlanItem(plan = plan, onToggle = { planViewModel.togglePlan(plan.id) })
            }
        }

        // ═══════════ 分隔 ═══════════
        item {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ═══════════ 系统架构 ═══════════
        item { SectionHeader(title = "系统架构") }
        item { ArchitectureCard(total = products.size) }

        // 模块列表
        items(products, key = { it.id }) { product ->
            ModuleCard(product = product, onDetail = { selectedProductId = product.id })
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }

    // 模块详情弹窗
    if (selectedProduct != null) {
        AlertDialog(
            onDismissRequest = { selectedProductId = null },
            shape = RoundedCornerShape(20.dp), containerColor = BgCard,
            title = {
                Text("${selectedProduct.name} · ${selectedProduct.price}",
                    color = TextPrimary, style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(selectedProduct.description, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    selectedProduct.toDetailLines().forEach { line ->
                        Text("  $line", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedProductId = null }) { Text("关闭", color = AccentCyan) }
            }
        )
    }
}

// ─── 子组件 ────────────────────────────────────────

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) AccentCyan.copy(alpha = 0.12f) else BgCard,
            labelColor = if (selected) AccentCyan else TextSecondary
        ),
        border = BorderStroke(1.dp, if (selected) AccentCyan.copy(alpha = 0.3f) else DividerColor),
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun PlanItem(plan: HealthPlan, onToggle: () -> Unit) {
    val catIcon: ImageVector = when (plan.category) {
        PlanCategory.EXERCISE -> Icons.AutoMirrored.Outlined.DirectionsRun
        PlanCategory.DIET -> Icons.Outlined.Restaurant
        PlanCategory.SLEEP -> Icons.Outlined.Bedtime
        PlanCategory.MEDICATION -> Icons.Outlined.Medication
        PlanCategory.CHECKUP -> Icons.Outlined.MonitorHeart
    }
    val catColor: Color = when (plan.category) {
        PlanCategory.EXERCISE -> StatusExcellent
        PlanCategory.DIET -> AccentOrange
        PlanCategory.SLEEP -> AccentIndigo
        PlanCategory.MEDICATION -> StatusPoor
        PlanCategory.CHECKUP -> AccentBlue
    }

    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard), elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (plan.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    null, tint = if (plan.isCompleted) StatusExcellent else TextTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(catColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(catIcon, null, tint = catColor, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    plan.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
                    color = if (plan.isCompleted) TextTertiary else TextPrimary,
                    textDecoration = if (plan.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(Modifier.height(2.dp))
                Text(plan.description, style = MaterialTheme.typography.bodySmall, color = TextTertiary, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            Text(plan.time, style = MaterialTheme.typography.labelMedium, color = TextTertiary)
        }
    }
}

@Composable
private fun ArchitectureCard(total: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard), elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AccountTree, null, tint = AccentCyan)
                Spacer(Modifier.width(8.dp))
                Text("共 $total 个核心模块", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "按\"硬件采集 - 通信链路 - 算法引擎 - AI解释\"路径讲解。",
                color = TextTertiary, style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModuleIcon(Icons.Outlined.Memory, AccentCyan)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                ModuleIcon(Icons.Outlined.NetworkCheck, AccentBlue)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                ModuleIcon(Icons.Outlined.Analytics, AccentOrange)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                ModuleIcon(Icons.Outlined.SmartToy, AccentIndigo)
            }
        }
    }
}

@Composable
private fun ModuleIcon(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun ModuleCard(product: MallProduct, onDetail: () -> Unit) {
    val icon = when (product.id) {
        "m1" -> Icons.Outlined.Memory; "m2" -> Icons.Outlined.NetworkCheck
        "m3" -> Icons.Outlined.Analytics; "m4" -> Icons.Outlined.SmartToy
        else -> Icons.Outlined.Hub
    }
    val iconColor = when (product.id) {
        "m1" -> AccentCyan; "m2" -> AccentBlue; "m3" -> AccentOrange; "m4" -> AccentIndigo
        else -> AccentTeal
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDetail),
        shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(iconColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(product.name, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(product.description, color = TextTertiary, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(product.price, color = AccentCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun PlanCategory.toLabel(): String = when (this) {
    PlanCategory.EXERCISE -> "操作"; PlanCategory.DIET -> "采集"; PlanCategory.SLEEP -> "报告"
    PlanCategory.MEDICATION -> "风控"; PlanCategory.CHECKUP -> "检查"
}

private fun MallProduct.toDetailLines(): List<String> = when (id) {
    "m1" -> listOf("输入: ECG/PPG 原始模拟信号与状态位", "输出: 带 FrameID 与微秒级时间戳的数字帧", "接口: ESP32-S3 + AD8232 + MAX30102")
    "m2" -> listOf("输入: 硬件采样帧", "输出: BLE 数据包、状态包、控制回执", "关键: 丢包检测、背压状态、CRC 校验")
    "m3" -> listOf("输入: 同步后的 ECG/PPG 时间轴", "输出: HR/HRV、PTT/PWTT、SQI、节律风险", "策略: 60 秒门控后统一发布真实指标")
    "m4" -> listOf("输入: 结构化指标与风险事件", "输出: 中文可读结论、趋势解释、复测建议", "边界: 仅健康提示，不替代临床诊断")
    else -> listOf("该模块暂无详细说明")
}
