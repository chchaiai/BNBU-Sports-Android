package edu.bnbu.student.mvp.feature.profile

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.designsystem.bnbuClickable

/**
 * Privacy policy & data collection disclosure screen.
 * Required for compliance with the Personal Information Protection Law (PIPL).
 *
 * Fixes AND-009: Privacy policy was previously missing from the Android app.
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .bnbuClickable(onClick = onBack)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "返回",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            Text(
                text = "隐私政策",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall
            )
        }

        item { PrivacySection("一、信息收集", listOf(
            "本应用由北京师范大学珠海校区（BNBU）体育教学部门开发，仅收集与体育课程成绩管理直接相关的必要信息。",
            "账户信息：学号/工号、姓名、邮箱、学院、年级。",
            "体育数据：打卡记录、运动凭证（图片/视频）、体测成绩、免测申请材料。",
            "设备能力：应用声明网络访问权限（android.permission.INTERNET）。拍照功能调用系统相机应用并通过 FileProvider 接收照片，本应用不声明 CAMERA 权限。"
        )) }

        item { PrivacySection("二、信息使用", listOf(
            "所有收集的信息仅用于以下目的：",
            "• 体育学时计算与成绩评定",
            "• 打卡记录存储与学时统计",
            "• 校队/社团抵扣认证",
            "• 系统通知（申请材料处理结果、任务截止提醒）",
            "数据不会用于任何商业目的，不会推送个性化广告。"
        )) }

        item { PrivacySection("三、信息存储与安全", listOf(
            "业务服务器地址由安装包的 BNBU_API_BASE_URL 配置决定，业务数据由后端及其配置的对象存储服务保存。",
            "密码仅用于发起登录请求，Android 端不会把密码写入本地持久化状态。API 请求使用 Bearer Token 认证，登录令牌优先使用 Android Keystore 支持的加密方式保存。",
            "工作台缓存、个人资料摘要和未提交草稿会保存在应用私有存储中，以支持会话恢复和离线查看；退出登录会清理这些本地记录。",
            "正式版要求业务接口使用系统信任的 HTTPS 证书并禁止明文流量。调试版可为联调服务器和本地模拟器临时放行 HTTP；调试版不应承载真实敏感数据。",
            "通过拍照生成的原始照片位于应用专属图片目录；清除应用数据或卸载会删除本地副本，但不会自动删除已经提交到服务器的记录。"
        )) }

        item { PrivacySection("四、信息共享", listOf(
            "您的信息仅在以下范围内共享：",
            "• 您的体育老师可查看您的学时、成绩和打卡记录。",
            "• 经授权的系统管理员可处理课程统计、申请和故障排查。",
            "• 校队/社团负责人可提交成员抵扣申请，但不能访问成员的其他个人数据。",
            "• 上传凭证可能由部署方配置的对象存储服务处理。",
            "您的个人信息不会被出售或用于个性化广告。"
        )) }

        item { PrivacySection("五、用户权利", listOf(
            "根据《中华人民共和国个人信息保护法》，您享有以下权利：",
            "• 知情权：了解您的个人信息被收集和使用的情况。",
            "• 查阅权：随时查看您的学时、成绩和打卡记录。",
            "• 更正权：联系体育老师或系统管理员修正错误的个人资料和课程记录。",
            "• 删除权：联系系统管理员请求删除您的个人数据。",
            "• 撤回同意：卸载或清除应用数据只会移除设备上的本地副本；如需注销账户、撤回授权或删除服务器数据，请联系系统管理员。"
        )) }

        item { PrivacySection("六、未成年人保护", listOf(
            "本应用的主要用户为年满 18 周岁的大学生。如涉及未成年用户（如预科生），其个人信息在获得监护人同意后方可收集和使用。"
        )) }

        item { PrivacySection("七、政策更新", listOf(
            "本隐私政策可能适时修订。重大变更将通过 App 内通知或学校公告方式告知。",
            "最新修订日期：2026 年 7 月 14 日。"
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
    val cs = MaterialTheme.colorScheme
    SwissPanel {
        Text(
            text = title,
            color = cs.onSurface,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(12.dp))
        paragraphs.forEach { paragraph ->
            Text(
                text = paragraph,
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
