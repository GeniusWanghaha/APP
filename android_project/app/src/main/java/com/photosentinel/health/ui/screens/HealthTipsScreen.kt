package com.photosentinel.health.ui.screens

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
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.photosentinel.health.ui.components.SectionHeader
import com.photosentinel.health.ui.theme.AccentBlue
import com.photosentinel.health.ui.theme.AccentCyan
import com.photosentinel.health.ui.theme.AccentIndigo
import com.photosentinel.health.ui.theme.AccentOrange
import com.photosentinel.health.ui.theme.AccentTeal
import com.photosentinel.health.ui.theme.BgCard
import com.photosentinel.health.ui.theme.BgPrimary
import com.photosentinel.health.ui.theme.StatusExcellent
import com.photosentinel.health.ui.theme.StatusPoor
import com.photosentinel.health.ui.theme.TextPrimary
import com.photosentinel.health.ui.theme.TextSecondary
import com.photosentinel.health.ui.theme.TextTertiary

// ─── 数据模型 ──────────────────────────────────────

private data class HealthTip(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val isAvoid: Boolean,
    val detail: String
)

private val exerciseTips = listOf(
    HealthTip(
        id = "e1", title = "有氧步行", subtitle = "每天 30 分钟快走，改善血管弹性",
        icon = Icons.AutoMirrored.Outlined.DirectionsRun, color = StatusExcellent, isAvoid = false,
        detail = "快走是最安全的有氧运动方式之一。每天坚持 30 分钟中速快走（约 5~6 km/h），" +
            "能有效促进外周血液循环，降低静息血压 5~8 mmHg，改善血管内皮功能。\n\n" +
            "建议：选择平坦路面，保持心率在最大心率的 50%~70%（约 220 - 年龄 × 0.6），" +
            "饭后 30 分钟开始为宜，避免空腹或极端天气下运动。"
    ),
    HealthTip(
        id = "e2", title = "游泳", subtitle = "全身运动，强化心肺功能",
        icon = Icons.Outlined.Pool, color = AccentBlue, isAvoid = false,
        detail = "游泳是全身性有氧运动，水的浮力减轻关节负担，水压促进静脉回流，" +
            "对心血管系统是温和而高效的锻炼。每周 2~3 次、每次 30~45 分钟即可显著改善心肺耐力。\n\n" +
            "建议：水温 26~28°C 为宜，避免过冷刺激引起血管收缩。有高血压者应避免憋气过久。" +
            "蛙泳和自由泳对心血管效果最佳。"
    ),
    HealthTip(
        id = "e3", title = "太极拳", subtitle = "调节自主神经，改善血管弹性",
        icon = Icons.Outlined.SelfImprovement, color = AccentIndigo, isAvoid = false,
        detail = "太极拳通过缓慢柔和的动作配合深呼吸，能有效降低交感神经兴奋性，" +
            "降低静息心率和血压。研究表明长期练习可改善动脉弹性指标（PWV 降低 10%~15%）。\n\n" +
            "建议：每天 20~30 分钟，选择 24 式简化太极拳入门。" +
            "注意保持呼吸自然均匀，动作缓慢连贯，避免屏气。"
    ),
    HealthTip(
        id = "e4", title = "骑行", subtitle = "中等强度有氧，增强下肢血液回流",
        icon = Icons.Outlined.DirectionsBike, color = AccentOrange, isAvoid = false,
        detail = "骑行属于中等强度有氧运动，腿部规律性运动能显著促进下肢静脉回流，" +
            "减少血液淤积，预防静脉曲张。同时改善心输出量和血管顺应性。\n\n" +
            "建议：每次 30~40 分钟，保持匀速（15~20 km/h），避免爬坡时过度用力。" +
            "使用固定功率自行车也可达到同样效果，更安全。"
    ),
    HealthTip(
        id = "e5", title = "拉伸与瑜伽", subtitle = "改善血管柔韧性，降低应激反应",
        icon = Icons.Outlined.Spa, color = AccentTeal, isAvoid = false,
        detail = "规律的拉伸运动能改善动脉壁柔韧性，降低动脉僵硬度。" +
            "瑜伽中的深呼吸训练可以激活副交感神经，降低皮质醇水平，从而保护血管内皮。\n\n" +
            "建议：每天 15~20 分钟，重点拉伸大腿后侧、髋部和背部。" +
            "呼吸配合动作，吸气准备，呼气时加深拉伸。避免弹振式拉伸。"
    ),
    HealthTip(
        id = "e6", title = "抗阻训练", subtitle = "适度力量训练，改善代谢与血管功能",
        icon = Icons.Outlined.FitnessCenter, color = AccentCyan, isAvoid = false,
        detail = "适度的抗阻训练（如弹力带、轻量哑铃）能改善胰岛素敏感性，降低甘油三酯水平，" +
            "间接保护血管健康。注意强度不宜过大，以免引起血压骤升。\n\n" +
            "建议：每周 2~3 次，每次 20~30 分钟，使用中低重量、高次数（12~15 次/组）。" +
            "避免憋气（Valsalva 动作），全程保持正常呼吸。"
    )
)

private val dietTips = listOf(
    HealthTip(
        id = "d1", title = "多吃深海鱼类", subtitle = "富含 Omega-3，减少血管炎症",
        icon = Icons.Outlined.Restaurant, color = AccentBlue, isAvoid = false,
        detail = "三文鱼、鳕鱼、沙丁鱼等深海鱼类富含 EPA 和 DHA（Omega-3 脂肪酸），" +
            "能显著降低血液中甘油三酯水平，减少血管壁炎症反应，抑制血小板聚集，预防血栓形成。\n\n" +
            "建议：每周食用 2~3 次，每次 100~150g。清蒸或低温烹饪可最大程度保留营养。" +
            "素食者可用亚麻籽油、核桃替代补充 ALA 型 Omega-3。"
    ),
    HealthTip(
        id = "d2", title = "忌精制糖类", subtitle = "奶茶、蛋糕、果汁等加速血管老化",
        icon = Icons.Outlined.Block, color = StatusPoor, isAvoid = true,
        detail = "精制糖（蔗糖、果葡糖浆等）在体内代谢会产生糖化终产物（AGEs），" +
            "AGEs 与血管壁胶原蛋白交联，导致血管僵硬失去弹性，加速动脉硬化进程。" +
            "长期高糖饮食还会诱发胰岛素抵抗、2 型糖尿病，进一步损伤心脑血管。\n\n" +
            "危害食物：奶茶（一杯含糖 40~60g）、蛋糕、碳酸饮料、果汁（非鲜榨）、甜面包。\n\n" +
            "替代：用水果（低 GI 类如蓝莓、草莓）替代甜食，用无糖茶饮替代含糖饮料。"
    ),
    HealthTip(
        id = "d3", title = "多吃坚果", subtitle = "不饱和脂肪酸降低 LDL 胆固醇",
        icon = Icons.Outlined.CheckCircle, color = StatusExcellent, isAvoid = false,
        detail = "核桃、杏仁、腰果等坚果富含单不饱和脂肪酸和多不饱和脂肪酸，" +
            "能有效降低低密度脂蛋白（LDL，\"坏胆固醇\"），升高高密度脂蛋白（HDL，\"好胆固醇\"），" +
            "减少动脉粥样硬化风险。坚果中的维生素 E 还具有抗氧化作用，保护血管内皮。\n\n" +
            "建议：每天一小把（约 25~30g），原味无盐为佳。核桃、杏仁效果最优。" +
            "注意控制总量，坚果热量较高。"
    ),
    HealthTip(
        id = "d4", title = "多吃深色蔬菜", subtitle = "天然硝酸盐扩张血管，降低血压",
        icon = Icons.Outlined.CheckCircle, color = StatusExcellent, isAvoid = false,
        detail = "菠菜、甜菜根、芹菜、西兰花等深色蔬菜富含天然硝酸盐，" +
            "在体内转化为一氧化氮（NO），直接扩张血管平滑肌，降低外周阻力和血压。" +
            "此外，深色蔬菜富含钾离子，有助于排出多余钠离子，进一步控制血压。\n\n" +
            "建议：每天 400~500g 蔬菜摄入，其中深色蔬菜占一半以上。" +
            "甜菜根汁对降压效果显著，可作为辅助手段。"
    ),
    HealthTip(
        id = "d5", title = "忌高盐饮食", subtitle = "增加血管壁压力，诱发高血压",
        icon = Icons.Outlined.Block, color = StatusPoor, isAvoid = true,
        detail = "高盐（钠）摄入导致体内水钠潴留，血容量增加，血管壁承受更大压力，" +
            "长期可致血管壁增厚、弹性下降，是高血压的首要饮食风险因素。\n\n" +
            "WHO 建议每日钠摄入不超过 2000 mg（约 5g 盐）。中国人均日摄入约 10g，远超标准。\n\n" +
            "高盐食物：腌制品、方便面、酱油、火锅底料、零食薯片。\n" +
            "替代：使用低钠盐，用醋、柠檬汁、香料调味代替加盐。"
    ),
    HealthTip(
        id = "d6", title = "多吃全谷物", subtitle = "膳食纤维降低胆固醇，稳定血糖",
        icon = Icons.Outlined.CheckCircle, color = AccentTeal, isAvoid = false,
        detail = "燕麦、糙米、全麦面包等全谷物富含可溶性膳食纤维（如 β-葡聚糖），" +
            "能在肠道中结合胆汁酸，促进胆固醇排出，降低血清总胆固醇和 LDL 水平。" +
            "同时全谷物 GI 值低，有助于稳定餐后血糖，减少血管糖化损伤。\n\n" +
            "建议：每天主食中全谷物占 1/3 以上。燕麦粥作为早餐是简便的选择。" +
            "注意循序渐进增加粗粮比例，避免消化不适。"
    ),
    HealthTip(
        id = "d7", title = "忌反式脂肪", subtitle = "人造奶油、油炸食品损伤血管内皮",
        icon = Icons.Outlined.Block, color = StatusPoor, isAvoid = true,
        detail = "反式脂肪酸（存在于人造奶油、起酥油、油炸食品中）会升高 LDL、降低 HDL，" +
            "同时促进血管壁炎症反应，加速动脉粥样硬化斑块形成。WHO 建议完全避免摄入。\n\n" +
            "高风险食物：炸鸡、薯条、饼干、蛋挞、奶油蛋糕、速溶咖啡伴侣。\n\n" +
            "识别方法：配料表中含\"氢化植物油\"\"人造奶油\"\"起酥油\"\"代可可脂\"的食品均含反式脂肪。"
    ),
    HealthTip(
        id = "d8", title = "适量饮茶", subtitle = "茶多酚抗氧化，保护血管内皮",
        icon = Icons.Outlined.CheckCircle, color = AccentIndigo, isAvoid = false,
        detail = "绿茶、乌龙茶中富含茶多酚（尤其是 EGCG），具有强抗氧化作用，" +
            "能清除自由基，抑制 LDL 氧化，减少动脉粥样硬化的起始环节。" +
            "研究表明每天 3~5 杯绿茶可降低心血管事件风险约 20%。\n\n" +
            "建议：每天 3~5 杯（约 600~1000 ml），淡茶为主。避免过浓或睡前饮用。" +
            "不加糖、不加奶，才能最大化保留茶多酚的活性。"
    )
)

// ─── 主界面 ────────────────────────────────────────

@Composable
fun HealthTipsScreen(modifier: Modifier = Modifier) {
    var selectedTip by rememberSaveable { mutableStateOf<String?>(null) }
    val allTips = exerciseTips + dietTips
    val currentTip = selectedTip?.let { id -> allTips.firstOrNull { it.id == id } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Text(
                "心血管健康建议",
                style = MaterialTheme.typography.displayMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Text(
                "以下为通用性心血管保健建议，仅供参考，不替代医疗诊断。",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
        item { Spacer(Modifier.height(4.dp)) }

        // ═══════════ 运动建议 ═══════════
        item { SectionHeader(title = "运动建议") }
        items(exerciseTips, key = { it.id }) { tip ->
            TipCard(tip = tip, onClick = { selectedTip = tip.id })
        }

        item { Spacer(Modifier.height(8.dp)) }

        // ═══════════ 饮食建议 ═══════════
        item { SectionHeader(title = "饮食建议") }
        items(dietTips, key = { it.id }) { tip ->
            TipCard(tip = tip, onClick = { selectedTip = tip.id })
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    // 详情弹窗
    if (currentTip != null) {
        AlertDialog(
            onDismissRequest = { selectedTip = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = BgCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(currentTip.color.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(currentTip.icon, null, tint = currentTip.color, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            currentTip.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        if (currentTip.isAvoid) {
                            Text("需要避免", style = MaterialTheme.typography.labelMedium, color = StatusPoor)
                        }
                    }
                }
            },
            text = {
                Text(
                    currentTip.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
            },
            confirmButton = {
                TextButton(onClick = { selectedTip = null }) {
                    Text("了解了", color = AccentCyan)
                }
            }
        )
    }
}

@Composable
private fun TipCard(tip: HealthTip, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tip.color.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(tip.icon, null, tint = tip.color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tip.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    if (tip.isAvoid) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(StatusPoor.copy(alpha = 0.10f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("避免", style = MaterialTheme.typography.labelSmall, color = StatusPoor)
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    tip.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                tint = TextTertiary, modifier = Modifier.size(20.dp)
            )
        }
    }
}
