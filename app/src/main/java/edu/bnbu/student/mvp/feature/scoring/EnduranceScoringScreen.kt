package edu.bnbu.student.mvp.feature.scoring

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bnbu.student.mvp.core.data.ApiStudentRepository
import edu.bnbu.student.mvp.core.designsystem.ActionButton
import edu.bnbu.student.mvp.core.designsystem.BNBUColors
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.StatusBadge
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.designsystem.ValidationPanel
import edu.bnbu.student.mvp.core.model.EnduranceConversionRequest
import edu.bnbu.student.mvp.core.model.EnduranceScoreResult
import edu.bnbu.student.mvp.core.model.StudentProfile
import kotlinx.coroutines.launch

@Composable
fun EnduranceScoringScreen(
    student: StudentProfile,
    repository: ApiStudentRepository,
    onBack: () -> Unit
) {
    var minutes by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<EnduranceScoreResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Auto-determine run type from gender
    val runType = if (student.gender == "male") "1000m" else "800m"

    fun convert() {
        val minVal = minutes.toIntOrNull() ?: 0
        val secVal = seconds.toIntOrNull() ?: 0
        val totalSeconds = minVal * 60 + secVal

        if (totalSeconds <= 0) {
            errorMessage = "请输入有效的跑步时间"
            return
        }

        if (student.gender.isBlank()) {
            errorMessage = "请先在个人资料中设置性别"
            return
        }
        if (student.gradeLevel.isBlank()) {
            errorMessage = "请先在个人资料中设置年级"
            return
        }

        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val response = repository.convertEndurance(
                    EnduranceConversionRequest(
                        timeSeconds = totalSeconds,
                        gender = student.gender,
                        gradeLevel = student.gradeLevel
                    )
                )
                result = EnduranceScoreResult(
                    score = response.score,
                    tier = response.tier,
                    timeSeconds = response.timeSeconds,
                    gender = response.gender,
                    gradeLevel = response.gradeLevel,
                    gradeGroup = response.gradeGroup
                )
            } catch (e: Exception) {
                errorMessage = "换算失败: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = BNBUColors.Ink
            )
            Text(
                text = "返回",
                color = BNBUColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionTitle(
            eyebrow = "Endurance Scoring",
            title = "耐力跑成绩换算"
        )

        Spacer(Modifier.height(8.dp))

        SwissPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    tint = BNBUColors.Blue,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "测试项目: $runType",
                        color = BNBUColors.Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "${student.genderLabel} · ${student.gradeLabel}",
                        color = BNBUColors.Muted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Time input
        SwissPanel {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "分钟",
                        color = BNBUColors.Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter { c -> c.isDigit() }.take(2) },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Text(
                    text = "′",
                    color = BNBUColors.Ink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "秒",
                        color = BNBUColors.Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { seconds = it.filter { c -> c.isDigit() }.take(2) },
                        placeholder = { Text("00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Text(
                    text = "″",
                    color = BNBUColors.Ink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.height(14.dp))

            ActionButton(
                title = if (isLoading) "换算中..." else "开始换算",
                icon = Icons.Filled.Timer,
                filled = true,
                onClick = { convert() }
            )
        }

        // Error
        errorMessage?.let { msg ->
            Spacer(Modifier.height(12.dp))
            ValidationPanel(message = msg)
        }

        // Result
        result?.let { score ->
            Spacer(Modifier.height(16.dp))
            SectionTitle(eyebrow = "Result", title = "换算结果")

            Spacer(Modifier.height(8.dp))

            val scoreColor = when (score.tier) {
                "excellent" -> Color(0xFF3A9DF6)
                "good" -> Color(0xFF4CAF50)
                "pass" -> Color(0xFFFF9800)
                "fail" -> Color(0xFFF44336)
                else -> Color(0xFF757575)
            }

            SwissPanel {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "单项得分",
                            color = BNBUColors.Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "${score.score}",
                            color = scoreColor,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "等级",
                            color = BNBUColors.Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                        StatusBadge(text = score.tierLabel, filled = true)
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BNBUColors.BlueSoft)
                        .border(1.dp, BNBUColors.Line, RectangleShape)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = BNBUColors.Blue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "输入时间: ${score.timeSeconds / 60}′${score.timeSeconds % 60}″",
                        color = BNBUColors.Ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "${student.genderLabel} · ${student.gradeLabel}",
                        color = BNBUColors.Muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
