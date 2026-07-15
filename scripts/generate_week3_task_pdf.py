from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.colors import HexColor
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate,
    Frame,
    KeepTogether,
    LongTable,
    PageBreak,
    PageTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
)
from reportlab.platypus.tableofcontents import TableOfContents


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "output" / "pdf" / "BNBU_Sports_Week3_三端开发任务书.pdf"

NAVY = HexColor("#17324D")
BLUE = HexColor("#2563EB")
TEAL = HexColor("#0F9D8A")
CYAN = HexColor("#E7F8F6")
PALE_BLUE = HexColor("#EDF4FF")
PALE_ORANGE = HexColor("#FFF6E8")
INK = HexColor("#243444")
MUTED = HexColor("#607286")
LINE = HexColor("#D9E3EA")
LIGHT = HexColor("#F6F9FB")
WHITE = colors.white


def register_fonts():
    pdfmetrics.registerFont(TTFont("YaHei", r"C:\Windows\Fonts\msyh.ttc", subfontIndex=0))
    pdfmetrics.registerFont(TTFont("YaHei-Bold", r"C:\Windows\Fonts\msyhbd.ttc", subfontIndex=0))


register_fonts()

styles = getSampleStyleSheet()
styles.add(
    ParagraphStyle(
        name="CoverKicker",
        fontName="YaHei-Bold",
        fontSize=11,
        leading=16,
        textColor=TEAL,
        alignment=TA_LEFT,
        spaceAfter=12,
    )
)
styles.add(
    ParagraphStyle(
        name="CoverTitleCN",
        fontName="YaHei-Bold",
        fontSize=28,
        leading=40,
        textColor=NAVY,
        alignment=TA_LEFT,
        spaceAfter=16,
    )
)
styles.add(
    ParagraphStyle(
        name="CoverSubtitleCN",
        fontName="YaHei",
        fontSize=12,
        leading=20,
        textColor=MUTED,
        alignment=TA_LEFT,
    )
)
styles.add(
    ParagraphStyle(
        name="H1CN",
        fontName="YaHei-Bold",
        fontSize=20,
        leading=28,
        textColor=NAVY,
        spaceBefore=2,
        spaceAfter=12,
        keepWithNext=True,
    )
)
styles.add(
    ParagraphStyle(
        name="H2CN",
        fontName="YaHei-Bold",
        fontSize=13,
        leading=20,
        textColor=BLUE,
        spaceBefore=9,
        spaceAfter=6,
        keepWithNext=True,
    )
)
styles.add(
    ParagraphStyle(
        name="TaskTitle",
        fontName="YaHei-Bold",
        fontSize=14,
        leading=21,
        textColor=NAVY,
        spaceAfter=7,
        keepWithNext=True,
    )
)
styles.add(
    ParagraphStyle(
        name="BodyCN",
        fontName="YaHei",
        fontSize=9.4,
        leading=15.5,
        textColor=INK,
        spaceAfter=5,
    )
)
styles.add(
    ParagraphStyle(
        name="BodySmall",
        fontName="YaHei",
        fontSize=8.4,
        leading=13.5,
        textColor=INK,
    )
)
styles.add(
    ParagraphStyle(
        name="Label",
        fontName="YaHei-Bold",
        fontSize=8.4,
        leading=12,
        textColor=NAVY,
    )
)
styles.add(
    ParagraphStyle(
        name="BulletCN",
        fontName="YaHei",
        fontSize=9.2,
        leading=15,
        textColor=INK,
        leftIndent=10,
        firstLineIndent=-7,
        bulletIndent=0,
        spaceAfter=3,
    )
)
styles.add(
    ParagraphStyle(
        name="Callout",
        fontName="YaHei",
        fontSize=9,
        leading=15,
        textColor=NAVY,
    )
)
styles.add(
    ParagraphStyle(
        name="TOCHeading",
        fontName="YaHei-Bold",
        fontSize=12,
        leading=18,
        textColor=NAVY,
        leftIndent=4,
    )
)


def p(text, style="BodyCN"):
    return Paragraph(text, styles[style])


def bullets(items):
    return [Paragraph(f"• {item}", styles["BulletCN"]) for item in items]


def info_table(rows, widths=None, background=LIGHT):
    widths = widths or [28 * mm, 145 * mm]
    data = [[p(label, "Label"), p(value, "BodySmall")] for label, value in rows]
    table = Table(data, colWidths=widths, hAlign="LEFT")
    table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), background),
                ("BOX", (0, 0), (-1, -1), 0.6, LINE),
                ("INNERGRID", (0, 0), (-1, -1), 0.35, LINE),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 7),
                ("RIGHTPADDING", (0, 0), (-1, -1), 7),
                ("TOPPADDING", (0, 0), (-1, -1), 6),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
            ]
        )
    )
    return table


def callout(title, text, color=CYAN):
    content = p(f"<b>{title}</b><br/>{text}", "Callout")
    table = Table([[content]], colWidths=[173 * mm])
    table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), color),
                ("BOX", (0, 0), (-1, -1), 0.7, TEAL),
                ("LEFTPADDING", (0, 0), (-1, -1), 10),
                ("RIGHTPADDING", (0, 0), (-1, -1), 10),
                ("TOPPADDING", (0, 0), (-1, -1), 8),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
            ]
        )
    )
    return table


def task_block(task_id, title, priority, goal, requirements, deliverables, acceptance, dependencies):
    header = Table(
        [[p(task_id, "Label"), p(priority, "Label")]],
        colWidths=[137 * mm, 36 * mm],
        hAlign="LEFT",
    )
    priority_bg = PALE_ORANGE if "P0" in priority else PALE_BLUE
    header.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (0, 0), PALE_BLUE),
                ("BACKGROUND", (1, 0), (1, 0), priority_bg),
                ("BOX", (0, 0), (-1, -1), 0.6, LINE),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("ALIGN", (1, 0), (1, 0), "CENTER"),
                ("LEFTPADDING", (0, 0), (-1, -1), 8),
                ("RIGHTPADDING", (0, 0), (-1, -1), 8),
                ("TOPPADDING", (0, 0), (-1, -1), 5),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
            ]
        )
    )
    flow = [header, Spacer(1, 4), p(title, "TaskTitle")]
    flow.append(info_table([("目标", goal), ("依赖项", dependencies)], background=LIGHT))
    flow.extend([p("功能要求", "H2CN"), *bullets(requirements)])
    flow.append(
        info_table(
            [
                ("交付物", "；".join(deliverables)),
                ("验收标准", "；".join(acceptance)),
            ],
            background=WHITE,
        )
    )
    flow.append(Spacer(1, 9))
    return flow


class Week3DocTemplate(BaseDocTemplate):
    def __init__(self, filename, **kwargs):
        super().__init__(filename, **kwargs)
        frame = Frame(
            self.leftMargin,
            self.bottomMargin,
            self.width,
            self.height,
            id="normal",
            leftPadding=0,
            rightPadding=0,
            topPadding=0,
            bottomPadding=0,
        )
        self.addPageTemplates(PageTemplate(id="main", frames=frame, onPage=self.draw_page))

    def draw_page(self, canvas, doc):
        canvas.saveState()
        page_width, page_height = A4
        if doc.page == 1:
            canvas.setFillColor(NAVY)
            canvas.rect(0, page_height - 14 * mm, page_width, 14 * mm, fill=1, stroke=0)
            canvas.setFillColor(TEAL)
            canvas.rect(0, 0, page_width, 7 * mm, fill=1, stroke=0)
        else:
            canvas.setFillColor(NAVY)
            canvas.rect(0, page_height - 8 * mm, page_width, 8 * mm, fill=1, stroke=0)
            canvas.setFont("YaHei", 7.8)
            canvas.setFillColor(MUTED)
            canvas.drawString(self.leftMargin, 12 * mm, "BNBU Sports | Week 3 三端开发任务书")
            canvas.drawRightString(page_width - self.rightMargin, 12 * mm, f"第 {doc.page} 页")
            canvas.setStrokeColor(LINE)
            canvas.setLineWidth(0.5)
            canvas.line(self.leftMargin, 16 * mm, page_width - self.rightMargin, 16 * mm)
        canvas.restoreState()

    def afterFlowable(self, flowable):
        if isinstance(flowable, Paragraph) and flowable.style.name == "H1CN":
            text = flowable.getPlainText()
            key = "h1-%s" % abs(hash((text, self.page)))
            self.canv.bookmarkPage(key)
            self.canv.addOutlineEntry(text, key, level=0, closed=False)
            self.notify("TOCEntry", (0, text, self.page, key))


def checklist_table(rows):
    data = [[p("编号", "Label"), p("验收场景", "Label"), p("通过条件", "Label")]]
    for number, scenario, expected in rows:
        data.append([p(number, "BodySmall"), p(scenario, "BodySmall"), p(expected, "BodySmall")])
    table = LongTable(data, colWidths=[18 * mm, 54 * mm, 101 * mm], repeatRows=1, hAlign="LEFT")
    table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), NAVY),
                ("TEXTCOLOR", (0, 0), (-1, 0), WHITE),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [WHITE, LIGHT]),
                ("BOX", (0, 0), (-1, -1), 0.7, LINE),
                ("INNERGRID", (0, 0), (-1, -1), 0.35, LINE),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 6),
                ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                ("TOPPADDING", (0, 0), (-1, -1), 6),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
            ]
        )
    )
    return table


def build_story():
    story = []

    # Cover
    story.extend(
        [
            Spacer(1, 32 * mm),
            p("BNBU SPORTS · WEEK 3", "CoverKicker"),
            p("Web、iOS 与 Android<br/>三端开发任务书", "CoverTitleCN"),
            p("围绕学生端全场景可用、教师端效率提升、移动端体验统一与服务端容量准备开展本周任务。", "CoverSubtitleCN"),
            Spacer(1, 24 * mm),
            callout(
                "本周核心结果",
                "完成学生网页版的核心功能对齐；优化教师证据查看、成绩换算、排序与日期规则；统一 iOS 和 Android UI 及状态反馈；形成服务端容量测算和压测方案。",
                PALE_BLUE,
            ),
            Spacer(1, 12 * mm),
            info_table(
                [
                    ("文档性质", "团队任务总册 / 开发与验收依据"),
                    ("覆盖范围", "Web 学生端、Web 教师端、iOS、Android、服务端调研"),
                    ("时间标识", "Week 3，不绑定具体自然日期"),
                    ("责任信息", "不在文档内设置负责人或负责人空栏"),
                ],
                background=LIGHT,
            ),
            Spacer(1, 42 * mm),
            p("版本：Week 3 任务发布版", "CoverSubtitleCN"),
            PageBreak(),
        ]
    )

    # TOC
    story.append(p("目录", "H1CN"))
    toc = TableOfContents()
    toc.levelStyles = [
        ParagraphStyle(
            name="TOCLevel1",
            fontName="YaHei",
            fontSize=10,
            leading=20,
            leftIndent=4,
            firstLineIndent=0,
            textColor=INK,
        )
    ]
    story.extend([toc, Spacer(1, 8 * mm)])
    story.append(
        callout(
            "阅读方式",
            "P0 为本周必须完成的闭环，P1 为应完成的关键优化，P2 为调研或配套交付。每项任务均以“目标、功能要求、交付物、验收标准、优先级、依赖项”描述。",
        )
    )
    story.append(Spacer(1, 9 * mm))
    story.append(p("任务边界", "H2CN"))
    story.extend(
        bullets(
            [
                "学生网页版按完整原生学生端能力对齐，不只提供临时上传页面。",
                "成绩换算使用后台可配置规则，不把学校评分表硬编码在客户端。",
                "移动端本周先完成 UI、结构和业务状态统一，再由产品确认接口规则。",
                "服务端本周交付调研、测算及压测方案，不直接修改生产架构。",
            ]
        )
    )
    story.append(PageBreak())

    # Goals and dependencies
    story.append(p("1. 本周目标与依赖关系", "H1CN"))
    story.append(
        callout(
            "总体目标",
            "让学生在 Web、iOS、Android 任一入口都能完成核心业务；让教师更快核验打卡证据和导出成绩；为集中上传场景建立可执行的容量与扩容依据。",
            PALE_BLUE,
        )
    )
    story.extend([Spacer(1, 5 * mm), p("四阶段推进", "H2CN")])
    phase_data = [
        [p("阶段", "Label"), p("主要工作", "Label"), p("阶段出口", "Label")],
        [p("第一阶段", "BodySmall"), p("确认三端 UI 基线、成绩规则数据结构、打卡日期规则和导入顺序保存方式。", "BodySmall"), p("页面与规则口径冻结。", "BodySmall")],
        [p("第二阶段", "BodySmall"), p("优先完成 Web 学生端闭环和教师端证据查看，再完成日期、成绩和排序。", "BodySmall"), p("Web 核心流程可演示。", "BodySmall")],
        [p("第三阶段", "BodySmall"), p("完成 iOS 与 Android UI、操作路径、状态文案和异常反馈对齐。", "BodySmall"), p("统一接口需求清单形成。", "BodySmall")],
        [p("第四阶段", "BodySmall"), p("以管理员、教师、学生三类角色执行全流程验收，并汇总服务端调研。", "BodySmall"), p("问题清单与调研报告可交接。", "BodySmall")],
    ]
    phase_table = Table(phase_data, colWidths=[27 * mm, 99 * mm, 47 * mm], repeatRows=1)
    phase_table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), NAVY),
                ("TEXTCOLOR", (0, 0), (-1, 0), WHITE),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [WHITE, LIGHT]),
                ("BOX", (0, 0), (-1, -1), 0.7, LINE),
                ("INNERGRID", (0, 0), (-1, -1), 0.35, LINE),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 6),
                ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                ("TOPPADDING", (0, 0), (-1, -1), 7),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
            ]
        )
    )
    story.extend([phase_table, Spacer(1, 8 * mm), p("关键依赖", "H2CN")])
    story.extend(
        bullets(
            [
                "Web 学生端、iOS 与 Android 共用同一业务定义，但客户端不得在产品确认前各自扩展接口字段。",
                "成绩录入、百分制换算和导出依赖后台规则配置与统一数据模型。",
                "打卡时间范围必须由服务端最终裁决，客户端和网页端负责提前提示。",
                "容量调研使用当前限制作为基线：最多 6 张图片、单图 8MB、1 个视频 100MB、Nginx 请求体限制 120MB。",
            ]
        )
    )
    story.append(PageBreak())

    # Web tasks
    story.append(p("2. Web 端开发任务", "H1CN"))
    story.extend(
        task_block(
            "W3-WEB-01",
            "学生网页版完整能力对齐",
            "P0 · 必须完成",
            "为无法安装 App、客户端异常或设备不兼容的学生提供完整可用的网页入口，保证所有学生均能完成核心业务。",
            [
                "对齐原生学生端的信息架构：主页、课程、打卡、成绩和个人中心。",
                "完成学生登录、会话保持、退出登录和失效会话处理。",
                "完成可打卡任务查看、任务详情、证据选择、上传、提交、结果反馈和历史记录闭环。",
                "图片上传遵循最多 6 张、单图不超过 8MB；视频遵循最多 1 个、最大 100MB，并显示上传进度。",
                "覆盖桌面浏览器和移动浏览器，重点验证 iPhone Safari、Android Chrome 及系统相册选取。",
                "课程、成绩与个人信息的字段、状态和文案与原生端保持一致。",
            ],
            ["学生网页版可运行版本", "移动端浏览器适配记录", "登录与打卡流程测试记录"],
            [
                "学生仅使用浏览器即可从登录完成一次有效打卡并在历史记录中查看结果",
                "不支持的文件、超限文件、断网和服务端失败均有明确提示且不得伪造成功",
                "主要页面在手机宽度下无横向溢出、遮挡或不可操作控件",
            ],
            "需要现有学生端业务字段、认证方式、课程与打卡接口说明；上传接口应支持浏览器调用。",
        )
    )
    story.append(PageBreak())
    story.append(p("2. Web 端开发任务（续）", "H1CN"))
    story.extend(
        task_block(
            "W3-WEB-02",
            "教师端打卡证据相册式查看",
            "P0 · 必须完成",
            "减少教师逐条进入详情页的操作，让教师能够快速核验图片、识别重复内容或疑似抄袭。",
            [
                "在打卡记录列表或记录详情中直接展示证据缩略图，并与学生、任务和提交时间绑定。",
                "支持批量缩略图浏览、全屏预览、放大、缩小、拖动、关闭及前后切换。",
                "移动端手势和桌面端鼠标操作均可用，交互参考 iPhone 相册的查看方式。",
                "图片按需加载并提供加载中、失败、重试和无权限状态，避免一次加载全部原图。",
                "预览时保留学生和打卡记录上下文，关闭后回到原来的筛选与滚动位置。",
            ],
            ["缩略图证据列表", "全屏图片查看器", "性能与兼容性测试记录"],
            [
                "教师无需逐条进入独立详情页即可快速查看同一记录的全部图片",
                "图片可连续切换并支持缩放，关闭后列表状态不丢失",
                "无权限、签名地址失效和加载失败时不会显示空白页或破坏列表布局",
            ],
            "依赖打卡记录与证据文件关联关系、可访问的缩略图或对象存储签名地址。",
        )
    )
    story.extend(
        task_block(
            "W3-WEB-03",
            "教师自定义打卡日期范围",
            "P0 · 必须完成",
            "允许教师为课程或任务设置明确的打卡开始时间和结束时间，防止教学周期外录入。",
            [
                "新增开始日期与结束日期配置，结束时间不得早于开始时间。",
                "开始前显示未开始，时间范围内允许提交，结束后显示已结束并禁止提交。",
                "网页端和移动端进行提前校验，服务端以统一时区和服务器时间做最终校验。",
                "编辑日期范围时保留已有打卡记录，不静默删除历史数据。",
            ],
            ["日期范围配置界面", "服务端校验规则说明", "边界测试记录"],
            [
                "开始时刻前一分钟不可提交，进入范围后可提交，结束时刻后不可提交",
                "绕过前端直接请求服务端仍会被拒绝并返回明确错误码",
                "教师修改日期后，学生端状态能正确刷新",
            ],
            "依赖课程或打卡任务模型、统一时区约定和服务端时间校验。",
        )
    )
    story.append(PageBreak())
    story.append(p("2. Web 端开发任务（续）", "H1CN"))
    story.extend(
        task_block(
            "W3-WEB-04",
            "成绩录入、百分制换算与导出",
            "P0 · 必须完成",
            "统一打卡、体测和期末成绩的百分制口径，并允许教师在原始成绩与换算成绩之间选择。",
            [
                "后台维护可配置的项目换算规则，至少支持 800 米、1000 米等耐力跑项目。",
                "耐力跑原始时间统一以“分:秒”录入和展示，服务端可保存标准化秒数用于计算。",
                "成绩录入和导出支持选择原始成绩、换算成绩或百分制成绩。",
                "20 小时打卡折算为 100 分，基础公式为 min(有效小时数 / 20 × 100, 100)。",
                "体测、期末及其他考核项目统一转换为 0 至 100 分后再参与汇总，权重规则由后台配置。",
                "规则调整需记录版本，历史成绩能够追溯当时使用的规则。",
            ],
            ["成绩规则配置界面", "分秒录入组件", "原始分/换算分/百分制导出选项", "规则版本说明"],
            [
                "800/1000 米显示为分秒且换算结果由后台规则计算",
                "有效打卡 20 小时显示 100 分，少于 20 小时按比例计算，超过 20 小时不超过 100 分",
                "同一批数据分别导出三种形态时字段、单位和结果清晰可辨",
                "客户端不得内置一套与服务端不同的评分表",
            ],
            "依赖可配置规则数据结构、体测项目定义、成绩权重规则和服务端计算接口。",
        )
    )
    story.extend(
        task_block(
            "W3-WEB-05",
            "学生导入顺序保留与可选排序",
            "P1 · 应完成",
            "默认按照教师导入学生表格时的原始顺序展示和导出，同时保留按学号、姓名排序的能力。",
            [
                "导入时为每条学生记录保存稳定的导入顺序字段，并按导入批次管理。",
                "学生列表与成绩表默认使用导入顺序，不因数据库主键、更新时间或字符串学号排序而变化。",
                "教师可主动切换为按学号或姓名排序；再次选择默认顺序时恢复导入顺序。",
                "导出默认沿用当前所选排序方式，并在导出参数中记录排序规则。",
            ],
            ["导入顺序字段与迁移说明", "列表排序切换", "导出顺序测试样例"],
            [
                "包含前导零、不同长度学号和中文姓名的表格导入后默认顺序完全不变",
                "按学号、姓名、导入顺序切换结果稳定且无数据遗漏",
                "同一筛选条件下页面顺序与导出顺序一致",
            ],
            "依赖导入批次标识、学生唯一标识及数据库稳定排序能力。",
        )
    )
    story.append(PageBreak())

    # Mobile
    story.append(p("3. iOS 与 Android 端任务", "H1CN"))
    story.extend(
        task_block(
            "W3-MOB-01",
            "双端 UI、结构与交互统一",
            "P0 · 必须完成",
            "以当前 iOS 视觉结构为基线，让 iOS 与 Android 在信息架构、视觉层级和主要操作路径上保持一致。",
            [
                "统一底部导航的页面数量、顺序、名称、图标、选中状态和安全区处理。",
                "统一页面层级、标题、卡片、按钮、颜色、间距、圆角、空状态和加载状态。",
                "移除 Android 独有而 iOS 不存在的三角提示标识，避免双端状态含义不一致。",
                "保留浅色、深色和跟随系统三种显示模式，并统一各主题的颜色语义和对比度。",
                "逐屏对齐主页、课程、打卡、成绩、通知和个人中心。",
            ],
            ["双端 UI 对比清单", "主要页面截图", "差异关闭记录"],
            [
                "同一账号、同一状态下，双端页面结构、主要文案和操作入口一致",
                "三种主题下无错色、遮挡、低对比度或图标语义冲突",
                "Android 不再出现 iOS 无对应含义的三角提示",
            ],
            "依赖已确认的 iOS 页面结构、统一设计令牌和共享业务状态表。",
        )
    )
    story.extend(
        task_block(
            "W3-MOB-02",
            "业务流程、状态文案与异常反馈对齐",
            "P0 · 必须完成",
            "确保学生在两种移动端上执行相同业务时获得一致的步骤、状态和错误反馈。",
            [
                "对齐课程查看、任务选择、证据上传、提交确认、历史记录、成绩展示和通知流程。",
                "统一上传失败、断网、重复打卡、日期范围外、文件超限、鉴权失效和服务器错误文案。",
                "所有可恢复错误提供重试入口；重试不得造成重复记录或重复计分。",
                "上传中显示真实进度，退出或切后台时给出明确状态，不以模拟成功替代后端结果。",
            ],
            ["双端状态与错误文案表", "异常路径测试记录", "重试与幂等验证记录"],
            [
                "相同错误码在两端显示相同含义和可执行的下一步",
                "断网或服务端失败时不会生成成功记录",
                "重复点击提交或失败重试不会产生重复打卡",
            ],
            "依赖统一错误码、提交幂等机制、上传状态接口和日期范围规则。",
        )
    )
    story.append(PageBreak())
    story.append(p("4. 三端接口规则准备", "H1CN"))
    story.extend(
        task_block(
            "W3-API-01",
            "统一接口需求清单与产品确认包",
            "P1 · 应完成",
            "在 UI 与业务流程对齐后，整理 Web、iOS、Android 的共同接口需求，为后续统一服务器规则提供可确认的输入。",
            [
                "逐接口列出路径、方法、认证方式、请求字段、响应字段、分页、排序、时间格式和错误码。",
                "统一文件元数据：文件类型、大小、时长、对象存储键、访问地址、上传状态和关联记录。",
                "统一成绩字段：原始值、显示值、单位、换算分、百分制分、规则版本和权重。",
                "统一打卡日期范围、服务器时间、时区和边界错误的表达方式。",
                "列出三端当前差异和迁移影响，由产品确认后再固化服务器接口。",
            ],
            ["三端接口需求清单", "字段差异表", "错误码与状态表", "待产品确认项清单"],
            [
                "每个端均可用同一份接口定义描述核心流程",
                "差异项有明确来源和影响，不由单个端自行决定最终规则",
                "接口确认包足以支持后续服务端统一与三端联调",
            ],
            "依赖 W3-MOB-01、W3-MOB-02 和 Web 核心流程的页面及状态确认。",
        )
    )

    # Server
    story.append(PageBreak())
    story.append(p("5. 服务端容量与冗余调研", "H1CN"))
    story.append(
        callout(
            "已知风险",
            "单次理论最大证据量为 6 × 8MB + 100MB = 148MB，高于当前 Nginx 120MB 请求体限制。若图片与视频通过同一请求组合上传，最大场景可能在进入应用服务前即被拒绝。",
            PALE_ORANGE,
        )
    )
    story.extend([Spacer(1, 5 * mm)])
    story.extend(
        task_block(
            "W3-SRV-01",
            "容量模型、压测方案与冗余建议",
            "P1 · 调研交付",
            "在不修改生产环境的前提下，形成可量化的上传承载范围、压测方法和后续扩容路线。",
            [
                "按单次最多 6 张 8MB 图片与 1 个 100MB 视频建立平均、常规峰值和理论峰值模型。",
                "测算 50、100、300 名学生集中上传时的入口带宽、临时磁盘、对象存储、数据库连接和 API 并发压力。",
                "比较逐文件上传、客户端直传对象存储、分片上传和断点续传的复杂度、稳定性与服务器压力。",
                "规划失败重试、请求幂等、防重复提交、限流、队列削峰、健康检查、监控告警和容量扩展。",
                "明确压测环境、测试数据清理、指标采集、停止条件和生产隔离要求。",
            ],
            ["容量测算表", "风险清单", "压测场景与指标定义", "推荐上传架构", "后续扩容建议"],
            [
                "报告明确 120MB 限制与 148MB 理论上限的处理策略",
                "50、100、300 并发场景均给出带宽、存储和接口压力估算",
                "压测方案不使用真实学生隐私数据且不直接冲击生产环境",
                "建议包含触发扩容或降级的可观测指标",
            ],
            "依赖现有 Nginx、应用服务、数据库、LightCOS 配置与历史上传日志；本周不实施生产改造。",
        )
    )

    story.append(PageBreak())
    story.append(p("5. 服务端容量与冗余调研（续）", "H1CN"))
    capacity_data = [
        [p("集中上传人数", "Label"), p("理论最大数据量", "Label"), p("必须观察的指标", "Label")],
        [p("50", "BodySmall"), p("约 7.4GB", "BodySmall"), p("入口吞吐、上传成功率、P95/P99 延迟、临时磁盘峰值。", "BodySmall")],
        [p("100", "BodySmall"), p("约 14.8GB", "BodySmall"), p("应用并发、数据库连接、对象存储请求速率、重试次数。", "BodySmall")],
        [p("300", "BodySmall"), p("约 44.4GB", "BodySmall"), p("限流效果、队列积压、错误率、恢复时间与扩容触发点。", "BodySmall")],
    ]
    capacity_table = Table(capacity_data, colWidths=[34 * mm, 42 * mm, 97 * mm], repeatRows=1)
    capacity_table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), NAVY),
                ("TEXTCOLOR", (0, 0), (-1, 0), WHITE),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [WHITE, LIGHT]),
                ("BOX", (0, 0), (-1, -1), 0.7, LINE),
                ("INNERGRID", (0, 0), (-1, -1), 0.35, LINE),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 6),
                ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                ("TOPPADDING", (0, 0), (-1, -1), 7),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
            ]
        )
    )
    story.extend([p("理论峰值参考", "H2CN"), capacity_table, Spacer(1, 7 * mm)])
    story.append(
        callout(
            "推荐优先研究方向",
            "将证据按文件独立上传，优先评估客户端直传对象存储与分片上传；业务 API 只保存文件元数据并完成记录提交。这样可减少应用服务器带宽与临时磁盘压力，也能规避组合请求超过 120MB 的问题。该建议需通过测试环境压测后再决定是否实施。",
            PALE_BLUE,
        )
    )
    story.extend([Spacer(1, 7 * mm), p("压测方案最低要求", "H2CN")])
    story.extend(
        bullets(
            [
                "测试数据使用生成文件和匿名账号，压测结束后清理对象、临时文件与测试记录。",
                "分别执行小图片、多图片、大视频、图片加视频、断点重传和重复提交场景。",
                "采集成功率、吞吐、P50/P95/P99 延迟、HTTP 错误、CPU、内存、网络、磁盘、数据库连接和对象存储请求指标。",
                "设置错误率、资源使用率和延迟停止阈值；触发后立即停止升压并保留现场数据。",
                "报告区分测算值与实测值，注明环境规格，禁止将测试环境结论直接等同于生产容量。",
            ]
        )
    )

    # Acceptance
    story.append(PageBreak())
    story.append(p("6. Week 3 联合验收清单", "H1CN"))
    story.append(
        checklist_table(
            [
                ("AC-01", "学生网页端完整打卡", "学生仅使用浏览器可完成登录、任务查看、证据上传、提交，并在记录中看到真实结果。"),
                ("AC-02", "多设备与相册选取", "iPhone Safari、Android Chrome 和桌面浏览器均可从系统相册选择符合规则的图片。"),
                ("AC-03", "证据相册式预览", "教师可在记录上下文中查看缩略图、全屏切换、缩放和拖动，无需逐条打开独立页面。"),
                ("AC-04", "打卡日期边界", "开始前、范围内、结束后三种状态正确；绕过前端仍由服务端拒绝范围外提交。"),
                ("AC-05", "耐力跑分秒显示", "800/1000 米原始成绩以分:秒展示，计算使用标准化值且不存在只显示总秒数的问题。"),
                ("AC-06", "原始与换算成绩", "教师可分别查看和导出原始成绩、换算成绩、百分制成绩，规则来源与版本清楚。"),
                ("AC-07", "20 小时百分制", "有效打卡 20 小时为 100 分，少于 20 小时按比例折算，超过 20 小时不超过 100 分。"),
                ("AC-08", "统一百分制汇总", "打卡、体测、期末均转换为 0 至 100 分后按后台配置权重汇总。"),
                ("AC-09", "导入顺序保持", "含前导零和不同长度学号的学生表默认保持教师导入顺序，页面与导出一致。"),
                ("AC-10", "可选排序", "教师可切换导入顺序、学号和姓名排序，切换过程不丢失、不重复学生。"),
                ("AC-11", "双端 UI 对齐", "iOS 与 Android 的导航、结构、主要组件和状态文案通过逐屏对比。"),
                ("AC-12", "三种主题", "浅色、深色、跟随系统模式均可切换并保持，主要页面无错色、遮挡或低对比度。"),
                ("AC-13", "异常与重试", "断网、超限、重复打卡、日期范围外和服务器失败均有一致提示，可恢复错误支持安全重试。"),
                ("AC-14", "接口确认包", "字段、请求、响应、错误码、上传与成绩规则清单完整，可交由产品确认。"),
                ("AC-15", "容量调研", "完成容量测算、风险清单、压测方案、指标定义和推荐架构，且未修改生产环境。"),
                ("AC-16", "三角色回归", "管理员、教师、学生分别完成权限允许的主流程，越权操作被拒绝并给出明确反馈。"),
            ]
        )
    )

    story.append(PageBreak())
    story.append(p("7. 交付要求与完成定义", "H1CN"))
    story.append(p("本周交付包", "H2CN"))
    story.extend(
        bullets(
            [
                "Web 学生端完整核心流程，以及教师端证据查看、日期、成绩与排序优化。",
                "iOS 与 Android UI 对比清单、状态文案表和异常路径验证记录。",
                "三端接口需求、字段差异、错误码和待产品确认项清单。",
                "服务端容量测算、风险清单、压测场景、指标定义和推荐架构。",
                "管理员、教师、学生三类角色联合验收结果与遗留问题清单。",
            ]
        )
    )
    story.append(p("完成定义", "H2CN"))
    story.append(
        info_table(
            [
                ("功能", "P0 主流程可运行，失败路径不会伪造成功，关键边界由服务端最终校验。"),
                ("体验", "Web、iOS、Android 的核心页面、状态和反馈口径一致，移动浏览器可正常操作。"),
                ("数据", "成绩规则可配置、排序稳定、导出可追溯，历史记录不会因规则或日期调整被静默删除。"),
                ("质量", "核心验收清单逐项记录通过、失败或待确认；失败项包含复现步骤和实际结果。"),
                ("安全", "测试不使用真实隐私数据，不公开密钥、数据库密码、对象存储凭证或真实学生证据。"),
            ],
            background=LIGHT,
        )
    )
    story.extend([Spacer(1, 9 * mm), callout("最终判断", "只有当核心功能可演示、关键边界验证完成、三端差异有明确结论、容量调研形成书面交付时，Week 3 才可标记为完成。", PALE_BLUE)])
    return story


def main():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    doc = Week3DocTemplate(
        str(OUTPUT),
        pagesize=A4,
        leftMargin=18 * mm,
        rightMargin=19 * mm,
        topMargin=18 * mm,
        bottomMargin=23 * mm,
        title="BNBU Sports Week 3 Web、iOS 与 Android 三端开发任务书",
        author="BNBU Sports Project Team",
        subject="Week 3 development task book",
    )
    doc.multiBuild(build_story())
    print(OUTPUT)


if __name__ == "__main__":
    main()
