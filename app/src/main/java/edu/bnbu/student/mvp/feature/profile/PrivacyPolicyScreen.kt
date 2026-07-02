package edu.bnbu.student.mvp.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bnbu.student.mvp.core.designsystem.BNBUColors
import edu.bnbu.student.mvp.core.designsystem.SwissPanel

/**
 * Privacy policy & data collection disclosure screen.
 * Required for compliance with the Personal Information Protection Law (PIPL).
 *
 * Fixes AND-009: Privacy policy was previously missing from the Android app.
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "隐私政策",
                    color = BNBUColors.Ink,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.weight(1f))
            }
        }

        item { PrivacySection("一、信息收集", listOf(
            "本应用由北京师范大学珠海校区（BNBU）体育教学部门开发，仅收集与体育课程成绩管理直接相关的必要信息。",
            "账户信息：学号/工号、姓名、邮箱、学院、年级。",
            "体育数据：打卡记录、运动凭证（图片/视频）、体测成绩、免测申请材料。",
            "设备权限：仅申请网络访问权限（android.permission.INTERNET），用于与后端服务器通信。"
        )) }

        item { PrivacySection("二、信息使用", listOf(
            "所有收集的信息仅用于以下目的：",
            "• 体育学时计算与成绩评定",
            "• 打卡记录的审核与复查",
            "• 校队/社团抵扣认证",
            "• 系统通知（审核结果、任务截止提醒）",
            "数据不会用于任何商业目的，不会推送个性化广告。"
        )) }

        item { PrivacySection("三、信息存储与安全", listOf(
            "学生数据存储于北京师范大学珠海校区自有服务器（IP: 123.207.5.70），由 Baota 面板管理。",
            "密码通过 PBKDF2-SHA512 散列存储，不保存明文密码。",
            "API 通信使用 Bearer Token 认证。生产环境部署完成后将启用 HTTPS 加密传输。",
            "用户上传的运动凭证图片/视频保存在服务器 uploads 目录，由 Nginx 直接提供静态访问。"
        )) }

        item { PrivacySection("四、信息共享", listOf(
            "您的信息仅在以下范围内共享：",
            "• 您的体育老师可查看您的学时、成绩和打卡记录。",
            "• 系统管理员可查看导出的统计数据（不含原始凭证图片）。",
            "• 校队/社团负责人可提交成员抵扣申请，但不能访问成员的其他个人数据。",
            "您的个人信息不会出售、出租或共享给任何第三方。"
        )) }

        item { PrivacySection("五、用户权利", listOf(
            "根据《中华人民共和国个人信息保护法》，您享有以下权利：",
            "• 知情权：了解您的个人信息被收集和使用的情况。",
            "• 查阅权：随时查看您的学时、成绩和打卡记录。",
            "• 更正权：通过 App 更新个人资料（性别、年级），或联系老师修正错误记录。",
            "• 删除权：联系系统管理员请求删除您的个人数据。",
            "• 撤回同意：卸载 App 即视为撤回同意。如不再选修体育课程，可联系管理员注销账户。"
        )) }

        item { PrivacySection("六、未成年人保护", listOf(
            "本应用的主要用户为年满 18 周岁的大学生。如涉及未成年用户（如预科生），其个人信息在获得监护人同意后方可收集和使用。"
        )) }

        item { PrivacySection("七、政策更新", listOf(
            "本隐私政策可能适时修订。重大变更将通过 App 内通知或学校公告方式告知。",
            "最新修订日期：2026 年 6 月 19 日。"
        )) }

        item { PrivacySection("八、联系方式", listOf(
            "如对本隐私政策有任何疑问，请联系：",
            "北京师范大学珠海校区体育教学部门",
            "通过校内教务系统或体育老师联系系统管理员"
        )) }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun PrivacySection(title: String, paragraphs: List<String>) {
    SwissPanel {
        Text(
            text = title,
            color = BNBUColors.Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(12.dp))
        paragraphs.forEach { paragraph ->
            Text(
                text = paragraph,
                color = BNBUColors.Muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
