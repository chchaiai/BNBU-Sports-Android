package edu.bnbu.student.mvp.core.model

import edu.bnbu.student.mvp.core.designsystem.interfaceText

/** Localizes stable server enums without changing their authoritative values. */
fun progressStatusLabel(value: String): String = when (value.trim().uppercase()) {
    "COMPLETED", "QUALIFIED" -> interfaceText("已达标", "Completed")
    "IN_PROGRESS", "RUNNING", "ACTIVE" -> interfaceText("进行中", "In progress")
    "NOT_STARTED", "PENDING" -> interfaceText("尚未开始", "Not started")
    else -> interfaceText("状态未知", "Status unavailable")
}

fun studentStatusLabel(value: String): String = when (value.trim().uppercase()) {
    "ACTIVE" -> interfaceText("账号正常", "Active")
    "PENDING_CONTACT_BINDING", "PENDING" ->
        interfaceText("待完成邮箱验证", "Email verification required")
    "DISABLED", "INACTIVE" -> interfaceText("账号已停用", "Disabled")
    "LOCKED" -> interfaceText("账号已锁定", "Locked")
    else -> interfaceText("状态未知", "Status unavailable")
}

fun feedbackCategoryLabel(value: String): String = when (value.trim().uppercase()) {
    "BUG" -> interfaceText("功能异常", "Feature issue")
    "ACCESSIBILITY" -> interfaceText("无障碍问题", "Accessibility")
    "PRIVACY" -> interfaceText("隐私问题", "Privacy")
    "SUGGESTION" -> interfaceText("功能建议", "Suggestion")
    "OTHER" -> interfaceText("其他", "Other")
    else -> interfaceText("其他", "Other")
}

/** Internal IDs and UUIDs are never a fallback for the public student number. */
internal fun StudentProfile.safeStudentNumberOrNull(): String? {
    val value = studentNumber.trim()
    return value.takeIf {
        it.isNotEmpty() &&
            !it.equals(id.trim(), ignoreCase = true) &&
            !UUID_LIKE_VALUE.matches(it)
    }
}

internal fun StudentProfile.studentNumberForDisplay(): String =
    safeStudentNumberOrNull()
        ?: interfaceText("学号未提供", "Student number unavailable")

private val UUID_LIKE_VALUE = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
)
