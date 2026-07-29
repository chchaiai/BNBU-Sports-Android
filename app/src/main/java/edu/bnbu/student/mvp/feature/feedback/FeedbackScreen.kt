package edu.bnbu.student.mvp.feature.feedback

import android.net.Uri
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import edu.bnbu.student.mvp.core.designsystem.AppleIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import edu.bnbu.student.mvp.BuildConfig
import edu.bnbu.student.mvp.core.data.ApiStudentRepository
import edu.bnbu.student.mvp.core.designsystem.ActionButton
import edu.bnbu.student.mvp.core.designsystem.EmptyPlaceholder
import edu.bnbu.student.mvp.core.designsystem.PrimaryActionButton
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.SegmentedControl
import edu.bnbu.student.mvp.core.designsystem.StatusBadge
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.designsystem.ValidationPanel
import edu.bnbu.student.mvp.core.designsystem.bnbuClickable
import edu.bnbu.student.mvp.core.designsystem.interfaceText
import edu.bnbu.student.mvp.core.local.AppLanguagePreferences
import edu.bnbu.student.mvp.core.model.ProofAttachment
import edu.bnbu.student.mvp.core.model.ProofMediaType
import edu.bnbu.student.mvp.core.network.ApiHttpException
import edu.bnbu.student.mvp.core.network.FeedbackTicketResponse
import edu.bnbu.student.mvp.core.network.SubmitFeedbackRequest
import edu.bnbu.student.mvp.core.state.StudentAppState
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

private const val MaxDescriptionLength = 2_000
private const val MaxScreenshots = 3

private enum class FeedbackTab { New, Tickets }

/** Problem-feedback form. Screenshots are uploaded first and submitted as COS keys. */
@Composable
fun FeedbackScreen(
    appState: StudentAppState,
    repository: ApiStudentRepository?,
    onUnauthorized: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    // Categories and transient messages are presentation copy. Keep their
    // remembered state scoped to the active app language so an in-place
    // recomposition can never keep a value translated for the old locale.
    val appLanguage = AppLanguagePreferences.currentLanguage
    var tab by remember { mutableStateOf(FeedbackTab.New) }
    var tickets by remember { mutableStateOf<List<FeedbackTicketResponse>>(emptyList()) }
    var isLoadingTickets by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember(appLanguage) { mutableStateOf<String?>(null) }
    var submittedTicket by remember { mutableStateOf<FeedbackTicketResponse?>(null) }
    val requestJob = remember { mutableStateOf<Job?>(null) }

    val categories = listOf(
        interfaceText("功能异常", "Feature issue"),
        interfaceText("打卡问题", "Check-in issue"),
        interfaceText("成绩问题", "Grade issue"),
        interfaceText("课程问题", "Course issue"),
        interfaceText("账号问题", "Account issue"),
        interfaceText("免测/认证", "Exemption or verification"),
        interfaceText("系统故障", "System issue"),
        interfaceText("其他", "Other")
    )
    var selectedCategory by remember(appLanguage) { mutableStateOf(categories.first()) }
    var description by remember { mutableStateOf("") }
    val currentPage = interfaceText("我的 / 问题反馈", "Profile / Report a problem")
    var email by remember { mutableStateOf(appState.workspace.student.email) }
    var phone by remember { mutableStateOf("") }
    var screenshots by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var cameraTempFile by remember { mutableStateOf<File?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }

    fun loadTickets() {
        if (isLoadingTickets || isSubmitting) return
        val availableRepository = repository ?: run {
            errorMessage = interfaceText(
                "演示或离线模式下暂时无法加载反馈记录。",
                "Feedback history is unavailable in demo or offline mode."
            )
            return
        }
        isLoadingTickets = true; errorMessage = null
        val job = appState.launchAuthenticatedRequest {
            try { tickets = availableRepository.listFeedbackTickets() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is ApiHttpException && e.statusCode == 401) { onUnauthorized(); return@launchAuthenticatedRequest }
                errorMessage = interfaceText(
                    "加载反馈记录失败：${e.message ?: "请稍后重试"}",
                    "Could not load feedback history: ${e.message ?: "Try again later."}"
                )
            } finally { isLoadingTickets = false }
        }
        requestJob.value = job
        if (job == null) { isLoadingTickets = false; onUnauthorized() }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraTempUri
        if (success && uri != null && screenshots.size < MaxScreenshots) screenshots = screenshots + uri
        else if (!success) cameraTempFile?.delete()
        cameraTempUri = null; cameraTempFile = null
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        screenshots = screenshots + uris.take((MaxScreenshots - screenshots.size).coerceAtLeast(0))
    }

    fun submit() {
        val availableRepository = repository ?: run {
            errorMessage = interfaceText(
                "演示或离线模式下暂时无法提交反馈，请连接服务器并使用正式账户后重试。",
                "Feedback cannot be submitted in demo or offline mode. Connect to the server with a signed-in account and try again."
            )
            return
        }
        if (!appState.isWriteAllowed) {
            errorMessage = interfaceText(
                "系统当前为只读模式，暂时无法提交反馈。",
                "The system is currently read-only; feedback cannot be submitted."
            )
            return
        }
        val note = description.trim()
        val contactEmail = email.trim()
        val contactPhone = phone.trim()
        when {
            isSubmitting -> return
            note.isEmpty() -> {
                errorMessage = interfaceText("请填写问题描述。", "Describe the problem.")
                return
            }
            note.length > MaxDescriptionLength -> {
                errorMessage = interfaceText(
                    "问题描述最多 $MaxDescriptionLength 字。",
                    "The description can contain up to $MaxDescriptionLength characters."
                )
                return
            }
            contactEmail.isEmpty() -> {
                errorMessage = interfaceText(
                    "请留下邮箱，便于接收处理回复。",
                    "Enter an email address so we can reply."
                )
                return
            }
            !contactEmail.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) -> {
                errorMessage = interfaceText("请输入有效的邮箱地址。", "Enter a valid email address.")
                return
            }
            contactPhone.isEmpty() -> {
                errorMessage = interfaceText(
                    "请留下联系电话，便于跟进问题。",
                    "Enter a phone number so we can follow up."
                )
                return
            }
            !contactPhone.matches(Regex("^[0-9+()\\-\\s]{5,32}$")) -> {
                errorMessage = interfaceText("请输入有效的联系电话。", "Enter a valid phone number.")
                return
            }
        }
        isSubmitting = true; errorMessage = null
        val screenshotSnapshot = screenshots
        val job = appState.launchAuthenticatedRequest {
            try {
                val screenshotsKeys = if (screenshotSnapshot.isEmpty()) emptyList() else availableRepository.uploadProofFiles(
                    screenshotSnapshot.mapIndexed { index, uri -> ProofAttachment(
                        id = uri.toString(), type = ProofMediaType.Image,
                        fileName = "feedback_screenshot_${index + 1}.jpg", byteCount = null, source = uri.toString()
                    ) }, context.cacheDir
                ).getOrThrow().map { it.cosKey }
                val ticket = availableRepository.submitFeedback(SubmitFeedbackRequest(
                    category = selectedCategory, description = note, currentPage = currentPage,
                    clientVersion = BuildConfig.VERSION_NAME, screenshots = screenshotsKeys,
                    email = contactEmail, phone = contactPhone
                ))
                submittedTicket = ticket
                tickets = listOf(ticket) + tickets.filterNot { it.id == ticket.id }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is ApiHttpException && e.statusCode == 401) { onUnauthorized(); return@launchAuthenticatedRequest }
                errorMessage = interfaceText(
                    "提交失败：${e.message ?: "请稍后重试"}",
                    "Submission failed: ${e.message ?: "Try again later."}"
                )
            } finally { isSubmitting = false }
        }
        requestJob.value = job
        if (job == null) { isSubmitting = false; onUnauthorized() }
    }

    DisposableEffect(Unit) { onDispose { requestJob.value?.cancel(); cameraTempFile?.delete() } }
    BackHandler {
        focusManager.clearFocus(force = true)
        when {
            isSubmitting -> errorMessage = interfaceText(
                "问题正在提交，请稍候。",
                "Your report is being submitted. Please wait."
            )
            submittedTicket != null -> submittedTicket = null
            else -> onBack()
        }
    }

    submittedTicket?.let { ticket ->
        FeedbackSubmittedScreen(ticket, onViewTickets = { submittedTicket = null; tab = FeedbackTab.Tickets; loadTickets() }, onBack = { submittedTicket = null })
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        item { BackTitle(onBack) }
        item { SectionTitle(title = interfaceText("问题反馈", "Report a problem")) }
        item {
            SegmentedControl(
                FeedbackTab.entries,
                tab,
                {
                    when (it) {
                        FeedbackTab.New -> interfaceText("提交问题", "New report")
                        FeedbackTab.Tickets -> interfaceText("我的反馈", "My reports")
                    }
                },
                { tab = it; if (it == FeedbackTab.Tickets && tickets.isEmpty()) loadTickets() }
            )
        }
        errorMessage?.let { message -> item { ValidationPanel(message) } }
        when (tab) {
            FeedbackTab.New -> item { FeedbackForm(
                categories, selectedCategory, { selectedCategory = it }, description, { description = it.take(MaxDescriptionLength) },
                email, { email = it }, phone, { phone = it }, screenshots, isSubmitting,
                writeEnabled = appState.isWriteAllowed && repository != null,
                serviceUnavailable = repository == null,
                onTakePicture = {
                    val directory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.cacheDir
                    val file = File(directory, "feedback_${System.currentTimeMillis()}.jpg")
                    runCatching {
                        file.parentFile?.mkdirs()
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        cameraTempFile = file; cameraTempUri = uri; cameraLauncher.launch(uri)
                    }.onFailure {
                        file.delete()
                        errorMessage = interfaceText(
                            "无法打开相机：${it.message ?: "请稍后重试"}",
                            "Could not open the camera: ${it.message ?: "Try again later."}"
                        )
                    }
                },
                onChooseImages = { galleryLauncher.launch(arrayOf("image/*")) },
                onRemoveScreenshot = { screenshots = screenshots - it }, onSubmit = ::submit
            ) }
            FeedbackTab.Tickets -> {
                if (isLoadingTickets) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(28.dp)) } }
                else if (tickets.isEmpty()) item {
                    EmptyPlaceholder(
                        interfaceText("暂无已提交问题", "No reports yet"),
                        interfaceText(
                            "提交问题后，可在这里查看处理状态。",
                            "After submitting a report, you can track its status here."
                        )
                    )
                }
                else items(tickets, key = { it.id.ifBlank { it.ticketNumber } }) { FeedbackTicketCard(it) }
                item {
                    ActionButton(
                        interfaceText("刷新处理状态", "Refresh status"),
                        Icons.Filled.Refresh,
                        false,
                        enabled = !isLoadingTickets && repository != null,
                        onClick = ::loadTickets
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable private fun BackTitle(onBack: () -> Unit) = Row(
    Modifier.fillMaxWidth().height(48.dp).bnbuClickable(onClick = onBack), verticalAlignment = Alignment.CenterVertically
) {
    val backLabel = interfaceText("返回", "Back")
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, backLabel)
    Text(backLabel, Modifier.padding(start = 4.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackForm(
    categories: List<String>, selectedCategory: String, onCategoryChanged: (String) -> Unit,
    description: String, onDescriptionChanged: (String) -> Unit,
    email: String, onEmailChanged: (String) -> Unit, phone: String, onPhoneChanged: (String) -> Unit,
    screenshots: List<Uri>, isSubmitting: Boolean, writeEnabled: Boolean, serviceUnavailable: Boolean,
    onTakePicture: () -> Unit, onChooseImages: () -> Unit,
    onRemoveScreenshot: (Uri) -> Unit, onSubmit: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val formEnabled = writeEnabled && !isSubmitting
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!writeEnabled) {
            ValidationPanel(
                if (serviceUnavailable) {
                    interfaceText(
                        "当前为演示或离线模式。你仍可查看反馈表单，但连接服务器并使用正式账户后才能提交。",
                        "You can view the feedback form in demo or offline mode, but submission requires a server connection and a signed-in account."
                    )
                } else {
                    interfaceText(
                        "系统当前为只读模式，暂时无法提交反馈。",
                        "The system is currently read-only; feedback submission is unavailable."
                    )
                }
            )
        }
        SwissPanel { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(interfaceText("问题内容", "Problem details"), style = MaterialTheme.typography.titleMedium)
            Text(
                interfaceText(
                    "请选择问题类型并描述你遇到的情况。",
                    "Choose a category and describe what happened."
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            ExposedDropdownMenuBox(expanded, { expanded = it }) {
                OutlinedTextField(selectedCategory, {}, readOnly = true, label = { Text(interfaceText("问题类型", "Category")) }, enabled = formEnabled,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded, { expanded = false }) { categories.forEach { category -> DropdownMenuItem({ Text(category) }, { onCategoryChanged(category); expanded = false }) } }
            }
            OutlinedTextField(
                description,
                onDescriptionChanged,
                label = { Text(interfaceText("问题描述（必填）", "Description (required)")) },
                placeholder = {
                    Text(
                        interfaceText(
                            "例如：操作步骤、预期结果和实际情况",
                            "Include the steps, expected result, and actual result"
                        )
                    )
                },
                supportingText = { Text("${description.length}/$MaxDescriptionLength") },
                minLines = 5,
                enabled = formEnabled,
                modifier = Modifier.fillMaxWidth()
            )
        } }
        SwissPanel { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(interfaceText("截图（可选）", "Screenshots (optional)"), style = MaterialTheme.typography.titleMedium)
            Text(
                interfaceText(
                    "最多 $MaxScreenshots 张。截图可帮助我们更快定位问题。",
                    "Add up to $MaxScreenshots screenshots to help us diagnose the problem."
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            screenshots.forEachIndexed { index, uri -> Row(verticalAlignment = Alignment.CenterVertically) {
                val screenshotLabel = interfaceText("截图 ${index + 1}", "Screenshot ${index + 1}")
                AsyncImage(uri, screenshotLabel, Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
                Text(screenshotLabel, Modifier.weight(1f))
                IconButton({ onRemoveScreenshot(uri) }, enabled = formEnabled) {
                    Icon(Icons.Filled.Delete, interfaceText("删除截图", "Remove screenshot"))
                }
            } }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(interfaceText("拍摄", "Take photo"), Icons.Filled.CameraAlt, false, Modifier.weight(1f), formEnabled && screenshots.size < MaxScreenshots, onTakePicture)
                ActionButton(interfaceText("从相册选择", "Choose photos"), Icons.Filled.Photo, false, Modifier.weight(1f), formEnabled && screenshots.size < MaxScreenshots, onChooseImages)
            }
        } }
        SwissPanel { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(interfaceText("联系方式", "Contact details"), style = MaterialTheme.typography.titleMedium)
            Text(
                interfaceText(
                    "用于回复和跟进此问题，不会公开展示。",
                    "Used only to reply and follow up. These details are not displayed publicly."
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(email, onEmailChanged, label = { Text(interfaceText("邮箱（必填）", "Email (required)")) }, placeholder = { Text("name@example.com") }, singleLine = true, enabled = formEnabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, onPhoneChanged, label = { Text(interfaceText("联系电话（必填）", "Phone number (required)")) }, placeholder = { Text(interfaceText("例如：138 0000 0000", "e.g. +1 555 0100")) }, singleLine = true, enabled = formEnabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        } }
        PrimaryActionButton(
            interfaceText("提交问题", "Submit report"),
            Icons.Filled.Send,
            enabled = writeEnabled,
            loading = isSubmitting,
            onClick = onSubmit
        )
    }
}

@Composable private fun FeedbackSubmittedScreen(ticket: FeedbackTicketResponse, onViewTickets: () -> Unit, onBack: () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    BackTitle(onBack); SectionTitle(title = interfaceText("问题已提交", "Report submitted"))
    SwissPanel { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)); Text(interfaceText("我们已收到你的问题。", "We received your report."), style = MaterialTheme.typography.titleMedium)
        Text(interfaceText("工单编号：", "Ticket: ") + ticket.ticketNumber.ifBlank { ticket.id })
        Row(verticalAlignment = Alignment.CenterVertically) { Text(interfaceText("当前状态：", "Status: ")); StatusBadge(ticket.status.feedbackStatusLabel(), filled = true) }
    } }
    PrimaryActionButton(interfaceText("查看处理状态", "View status"), Icons.Filled.SupportAgent, onClick = onViewTickets)
}

@Composable private fun FeedbackTicketCard(ticket: FeedbackTicketResponse) = SwissPanel { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(ticket.ticketNumber.ifBlank { ticket.id }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); StatusBadge(ticket.status.feedbackStatusLabel(), filled = true) }
    Text(ticket.category, color = MaterialTheme.colorScheme.primary); Text(ticket.description, maxLines = 3)
    if (ticket.createdAt.isNotBlank()) Text(interfaceText("提交时间：", "Submitted: ") + ticket.createdAt, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ticket.reply?.takeIf { it.isNotBlank() }?.let { Text(interfaceText("处理说明：", "Response: ") + it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
} }

private fun String.feedbackStatusLabel(): String = when (lowercase()) {
    "pending", "open", "submitted", "待处理" -> interfaceText("待处理", "Pending")
    "processing", "in_progress", "处理中" -> interfaceText("处理中", "In progress")
    "resolved", "closed", "completed", "已解决", "已关闭" -> interfaceText("已解决", "Resolved")
    "rejected", "已驳回" -> interfaceText("已驳回", "Rejected")
    else -> ifBlank { interfaceText("待处理", "Pending") }
}
