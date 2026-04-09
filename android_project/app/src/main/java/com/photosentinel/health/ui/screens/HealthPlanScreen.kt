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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.photosentinel.health.domain.model.PlanCategory
import com.photosentinel.health.ui.components.SectionHeader
import com.photosentinel.health.ui.theme.AccentBlue
import com.photosentinel.health.ui.theme.AccentCyan
import com.photosentinel.health.ui.theme.AccentIndigo
import com.photosentinel.health.ui.theme.AccentOrange
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

@Composable
fun HealthPlanScreen(
    modifier: Modifier = Modifier,
    viewModel: HealthPlanViewModel = viewModel()
) {
    val uiState = viewModel.uiState
    val plans = uiState.plans
    val completedCount = plans.count { it.isCompleted }
    val pendingPlans = plans.filterNot { it.isCompleted }
    val progress = if (plans.isNotEmpty()) completedCount.toFloat() / plans.size else 0f

    var keyword by rememberSaveable { mutableStateOf("") }
    var selectedCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedCategory = selectedCategoryName?.let { PlanCategory.valueOf(it) }

    val filteredPlans = plans.filter { plan ->
        val categoryMatched = selectedCategory == null || plan.category == selectedCategory
        val keywordMatched =
            keyword.isBlank() ||
                plan.title.contains(keyword, ignoreCase = true) ||
                plan.description.contains(keyword, ignoreCase = true) ||
                plan.time.contains(keyword, ignoreCase = true)
        categoryMatched && keywordMatched
    }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(title = "执行方案台")

            // 进度卡片
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                            text = "已完成 $completedCount / ${plans.size} 项",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = AccentCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AccentCyan,
                        trackColor = DividerColor
                    )
                    Text(
                        text = if (pendingPlans.isNotEmpty()) {
                            "下一步: ${pendingPlans.first().title}（${pendingPlans.first().time}）"
                        } else {
                            "所有执行步骤已完成，可以进入复测与报告复盘。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }

            // 搜索
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜索步骤", color = TextTertiary) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = DividerColor,
                    focusedContainerColor = BgCard,
                    unfocusedContainerColor = BgCard
                )
            )

            // 分类筛选
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip(
                    label = "全部",
                    selected = selectedCategory == null,
                    onClick = { selectedCategoryName = null }
                )
                PlanCategory.values().forEach { category ->
                    CategoryChip(
                        label = category.toChineseLabel(),
                        selected = selectedCategory == category,
                        onClick = { selectedCategoryName = category.name }
                    )
                }
            }

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.setAllPlansCompleted(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = plans.isNotEmpty()
                ) {
                    Text("全部完成")
                }
                OutlinedButton(
                    onClick = { viewModel.setAllPlansCompleted(false) },
                    enabled = plans.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("全部重置")
                }
                OutlinedButton(
                    onClick = { viewModel.refresh() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("刷新")
                }
            }

            // 列表
            if (filteredPlans.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = BgDeep)
                ) {
                    Text(
                        text = "当前筛选条件下没有步骤，建议清空关键词或切换分类。",
                        color = TextTertiary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                SectionHeader(title = "步骤清单（${filteredPlans.size}）")
                LazyColumn(
                    modifier = Modifier.height(500.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPlans, key = { it.id }) { plan ->
                        PlanItem(
                            plan = plan,
                            onToggle = {
                                viewModel.togglePlan(plan.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) AccentCyan.copy(alpha = 0.12f) else BgCard,
            labelColor = if (selected) AccentCyan else TextSecondary
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) AccentCyan.copy(alpha = 0.3f) else DividerColor
        ),
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun PlanItem(
    plan: HealthPlan,
    onToggle: () -> Unit
) {
    val categoryIcon: ImageVector = when (plan.category) {
        PlanCategory.EXERCISE -> Icons.AutoMirrored.Outlined.DirectionsRun
        PlanCategory.DIET -> Icons.Outlined.Restaurant
        PlanCategory.SLEEP -> Icons.Outlined.Bedtime
        PlanCategory.MEDICATION -> Icons.Outlined.Medication
        PlanCategory.CHECKUP -> Icons.Outlined.MonitorHeart
    }

    val categoryColor: Color = when (plan.category) {
        PlanCategory.EXERCISE -> StatusExcellent
        PlanCategory.DIET -> AccentOrange
        PlanCategory.SLEEP -> AccentIndigo
        PlanCategory.MEDICATION -> StatusPoor
        PlanCategory.CHECKUP -> AccentBlue
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (plan.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (plan.isCompleted) StatusExcellent else TextTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(categoryColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (plan.isCompleted) TextTertiary else TextPrimary,
                    textDecoration = if (plan.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = plan.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = plan.time,
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary
            )
        }
    }
}

private fun PlanCategory.toChineseLabel(): String {
    return when (this) {
        PlanCategory.EXERCISE -> "操作"
        PlanCategory.DIET -> "采集"
        PlanCategory.SLEEP -> "报告"
        PlanCategory.MEDICATION -> "风控"
        PlanCategory.CHECKUP -> "检查"
    }
}
