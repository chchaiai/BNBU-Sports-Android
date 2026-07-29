package edu.bnbu.student.mvp.core.mock

import edu.bnbu.student.mvp.core.model.CheckInRecord
import edu.bnbu.student.mvp.core.model.CheckInTimeWindow
import edu.bnbu.student.mvp.core.model.Course
import edu.bnbu.student.mvp.core.model.CreditType
import edu.bnbu.student.mvp.core.model.Exemption
import edu.bnbu.student.mvp.core.model.GradeBlock
import edu.bnbu.student.mvp.core.model.GradeRow
import edu.bnbu.student.mvp.core.model.GradeSubItem
import edu.bnbu.student.mvp.core.model.Membership
import edu.bnbu.student.mvp.core.model.NoticeCategory
import edu.bnbu.student.mvp.core.model.ProofAttachment
import edu.bnbu.student.mvp.core.model.ProofMediaType
import edu.bnbu.student.mvp.core.model.StudentNotice
import edu.bnbu.student.mvp.core.model.StudentProfile
import edu.bnbu.student.mvp.core.model.StudentProgress
import edu.bnbu.student.mvp.core.model.StudentWorkspace
import edu.bnbu.student.mvp.core.model.SyncOperation
import edu.bnbu.student.mvp.core.model.SyncOperationStatus
import edu.bnbu.student.mvp.core.model.SyncOperationType
import edu.bnbu.student.mvp.core.model.TeacherInfo

/** Complete, internally consistent local data for the Mock user. */
object MockStudentWorkspace {
    const val studentId = "2024010836"

    fun create(): StudentWorkspace {
        val student = StudentProfile(
            id = studentId,
            name = "林若晴",
            email = "ruoqing.lin@bnbu.edu.cn",
            college = "工商与管理学院",
            className = "2024级工商管理1班",
            status = "正常",
            gender = "female",
            gradeLevel = "sophomore",
            admissionYear = 2024,
            currentAcademicYear = "2025-2026 学年",
            gradeCalculatedAt = "2026-07-26 18:00"
        )
        val badminton = Course(
            id = "course-badminton-2026-spring", code = "PE2026B12", section = "02",
            name = "大学体育（羽毛球）", semester = "2025-2026 学年第二学期",
            students = 32, pending = 1, completion = 88, missing = 4,
            deadline = "2026-08-02 23:59", teacher = "陈宇航", teacherId = "teacher-chen-yuhang",
            semesterId = "2025-2026-2", academicYear = "2025-2026", term = "第二学期"
        )
        val yoga = Course(
            id = "course-yoga-2026-summer", code = "PE2026S08", section = "01",
            name = "暑期体能与瑜伽", semester = "2025-2026 学年夏季学期",
            students = 28, pending = 0, completion = 64, missing = 10,
            deadline = "2026-08-16 23:59", teacher = "周思敏", teacherId = "teacher-zhou-simin",
            semesterId = "2025-2026-summer", academicYear = "2025-2026", term = "夏季学期"
        )
        val records = listOf(
            record("record-20260725-yoga", yoga.id, "柔韧与核心训练记录", CreditType.CourseRelated, 2.0, "2026-07-25 19:42", "瑜伽", "完成 40 分钟核心与拉伸训练。", "已收到，等待教师审核。", "yoga_core_01.jpg", "yoga_core_02.jpg"),
            record("record-20260722-run", null, "自主运动打卡", CreditType.General, 2.0, "2026-07-22 07:18", "跑步", "晨跑 5 公里，配速 6 分 12 秒。", "记录有效，已计入其他运动时长。", "run_track.jpg", "run_5km.mp4"),
            record("record-20260718-badminton", badminton.id, "羽毛球专项练习打卡", CreditType.CourseRelated, 2.0, "2026-07-18 20:05", "羽毛球", "完成高远球、吊球及步法练习。", "动作练习和场地信息完整，已通过。", "badminton_01.jpg", "badminton_02.jpg"),
            record("record-20260712-cycle", null, "自主运动打卡", CreditType.General, 2.0, "2026-07-12 17:36", "骑行", "骑行 18 公里。", "已计入其他运动时长。", "cycling_route.jpg"),
            record("record-20260708-strength", null, "自主运动打卡", CreditType.General, 2.0, "2026-07-08 18:15", "力量训练", "完成器械力量训练与拉伸。", "已通过。", "strength_01.jpg", "strength_02.jpg"),
            record("record-20260703-badminton", badminton.id, "羽毛球专项练习打卡", CreditType.CourseRelated, 2.0, "2026-07-03 19:30", "羽毛球", "双打配合与发接发练习。", "已通过。", "badminton_03.jpg"),
            record("record-20260629-swim", null, "自主运动打卡", CreditType.General, 2.0, "2026-06-29 15:50", "游泳", "游泳 1,200 米。", "已通过。", "swimming_01.jpg"),
            record("record-20260626-badminton", badminton.id, "羽毛球专项练习打卡", CreditType.CourseRelated, 2.0, "2026-06-26 19:15", "羽毛球", "完成杀球与防守转换练习。", "已通过。", "badminton_04.jpg", "badminton_05.jpg")
        )
        val membership = Membership(
            id = "membership-school-badminton-team", type = "team", organization = "北师港浸大羽毛球队",
            studentId = studentId, studentName = student.name, status = "有效", validUntil = "2026-08-31",
            offset = "课程相关时长抵扣 2 小时", comment = "2026 春季学期校队训练证明已核验。",
            updatedBy = "体育部管理员", updatedAt = "2026-07-10 09:20"
        )
        return StudentWorkspace(
            student = student,
            courses = listOf(badminton, yoga),
            progress = StudentProgress(studentId, student.name, student.college, student.className, 8.0, 8.0, 6.0, 8.0, 24, 18, 8, "进行中", "Mock 用户数据 · 2026-07-26 汇总", membership),
            records = records,
            grades = GradeRow(
                studentId = studentId,
                studentName = student.name,
                visibleBlocks = listOf(
                    GradeBlock(
                        id = "physical",
                        name = "1000米/800米 跑步",
                        weight = 0.50,
                        score = 86,
                        scoreDisplay = "86",
                        isVisible = true,
                        displayOrder = 10,
                        blockType = "physical_test",
                        description = "本学期耐力跑测试成绩。",
                        subItems = null
                    ),
                    GradeBlock(
                        id = "checkin",
                        name = "打卡成绩",
                        weight = 0.50,
                        score = 88,
                        scoreDisplay = "88",
                        isVisible = true,
                        displayOrder = 20,
                        blockType = "checkin",
                        description = "有效打卡与组织认证学时。",
                        subItems = listOf(
                            GradeSubItem("课程相关运动", 90, "90"),
                            GradeSubItem("组织认证", 80, "80")
                        )
                    )
                ),
                totalScore = 86,
                totalDisplay = "86",
                isPassed = true,
                courseGradeStatus = "in_progress",
                displayConfigVersion = 1,
                sourceTrace = "打卡 16/20 小时；理论测验、课堂出勤及体测成绩已同步。",
                enduranceRunTimeSeconds = 252
            ),
            memberships = listOf(membership),
            notices = listOf(
                StudentNotice("notice-course-deadline", "羽毛球课程打卡截止提醒", "课程相关运动时长还差 2 小时，请在 8 月 2 日 23:59 前完成并提交凭证。", "今天 09:00", NoticeCategory.Deadline, true),
                StudentNotice("notice-yoga-review", "柔韧与核心训练记录已提交", "你于 7 月 25 日提交的 2 小时课程打卡已保存，可在打卡记录中查看状态。", "7 月 25 日 19:44", NoticeCategory.Review, true),
                StudentNotice("notice-team-credit", "校队训练时长已抵扣", "北师港浸大羽毛球队训练证明已核验，已抵扣课程相关运动时长 2 小时。", "7 月 10 日 09:20", NoticeCategory.Organization, false)
            ),
            teachers = listOf(TeacherInfo(badminton.teacherId, badminton.teacher), TeacherInfo(yoga.teacherId, yoga.teacher)),
            syncOperations = listOf(SyncOperation("sync-mock-workspace", SyncOperationType.ResetLocalData, "Mock 用户数据已加载", "林若晴 · 2025-2026 学年第二学期", "2026-07-26 18:00", SyncOperationStatus.LocalOnly)),
            exemptions = listOf(Exemption("exemption-800m-2026", studentId, student.name, "800m", "physical_test", reason = "因踝关节扭伤申请本学期 800 米测试缓测。", status = "审核中", proofFiles = listOf("mock://proof/medical_note.pdf"), reviewComment = "已收到校医院证明，正在审核。", reviewerName = "体育部教务组", createdAt = "2026-07-21 11:05", updatedAt = "2026-07-21 11:20")),
            // Mock sessions do not have a backend policy endpoint. Keep the
            // local check-in flow usable at any time for UI verification.
            checkInTimeWindow = CheckInTimeWindow(
                windowMode = "semester_wide",
                dateRangeStart = null,
                dateRangeEnd = null,
                dailyStartTime = "00:00",
                dailyEndTime = "23:59",
                excludedDates = emptyList(),
                semesterDeadline = null
            )
        )
    }

    private fun record(
        id: String, courseId: String?, title: String, creditType: CreditType, hours: Double,
        submittedAt: String, sportType: String, note: String, feedback: String, vararg files: String
    ): CheckInRecord {
        val proofs = files.mapIndexed { index, name ->
            val isVideo = name.endsWith(".mp4", ignoreCase = true)
            ProofAttachment("$id-proof-$index", if (isVideo) ProofMediaType.Video else ProofMediaType.Image, name, if (isVideo) 8_600_000 else 1_240_000, if (isVideo) 76.0 else null, source = "mock://proof/$name")
        }
        return CheckInRecord(
            id = id, courseId = courseId, taskTitle = title, creditType = creditType, hours = hours,
            submittedAt = submittedAt,
            proofSummary = "${proofs.count { it.type == ProofMediaType.Image }} 张图片${if (proofs.any { it.type == ProofMediaType.Video }) "，1 个短视频" else ""}",
            proofPhotoCount = proofs.count { it.type == ProofMediaType.Image }, proofVideoCount = proofs.count { it.type == ProofMediaType.Video },
            proofFiles = proofs, teacherPublicFeedback = feedback, teacherInternalNote = null, note = note, sportType = sportType
        )
    }
}
