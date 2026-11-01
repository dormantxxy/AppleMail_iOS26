# Apple Mail (iOS 26) — Android 移植版

将 **iOS 26 Apple Mail 邮箱客户端** 移植为 Android 应用，支持**真实邮箱登录与收发**。

- **包名**：`com.apple.mail`（与 iOS Bundle ID 一致）
- **技术栈**：Kotlin + Jetpack Compose + Material 3 + Haze（Liquid Glass）+ JavaMail（IMAP/SMTP）
- **构建**：GitHub Actions CI 自动构建 APK

---

## ✨ 功能

### 添加账户（登录流程，对齐 iOS 26 Mail）
1. 选择服务商：iCloud / Google / Outlook / Microsoft Exchange / Yahoo / QQ / 163 / 126 / 新浪 / 其他(自定义 IMAP)
2. 输入姓名、邮箱、密码/授权码（自定义可填 IMAP/SMTP 主机端口与 SSL/STARTTLS）
3. 验证连接（真实 IMAP 登录校验，失败给出可读原因）
4. 选择同步内容（邮件 / 通讯录 / 日历 / 备忘录）
5. 保存并自动拉取

### 邮件
- ✅ IMAP 收信（发件人/主题/时间/正文/附件/已读/旗标）
- ✅ SMTP 发信（收件人、抄送、多账户选择发件账户）
- ✅ 多账户统一收件箱，侧栏添加/移除账户
- ✅ **已读 / 未读 / 旗标 回写服务器**（IMAP STORE）
- ✅ **删除（移入废纸篓/expunge）与归档（移入 Archive/All Mail）回写服务器**
- ✅ **附件下载**（保存到应用外部存储 `Android/data/com.apple.mail/files/attachments`）
- ✅ **真实文件夹**：收件箱 / 已发送 / 草稿 / 垃圾 / 废纸篓（按选择从服务器拉取）
- ✅ **下拉刷新**
- ✅ 本地分类（关键词启发式）：主要 / 交易 / 更新 / 推广
- ✅ 搜索、多选批处理（标记/归档/删除，均回写服务器）

> ⚠️ **密码说明**（与 iOS 26 一致）：
> - **QQ / 163 / 126 / 新浪**：网页版开启 IMAP/SMTP，用 **授权码**
> - **Gmail / iCloud / Yahoo**：用 **应用专用密码**
> - **Outlook / Exchange**：账户密码或应用密码（部分企业租户需 OAuth）
> 账号密码仅存本机（SharedPreferences，Base64 混淆，非强加密）。

---

## 🛠 构建（GitHub Actions）

推送后 `.github/workflows/build.yml` 自动：JDK17 → `gradle wrapper` → `./gradlew :app:assembleRelease` → 上传 APK。
产物：`app/build/outputs/apk/release/app-release.apk`

---

## 📁 结构

```
app/src/main/java/com/apple/mail/
├── MainActivity.kt          # 入口 + 导航
├── MailViewModel.kt         # 状态/账户/收发/回写/附件
├── data/
│   ├── MailModels.kt        # 模型
│   ├── MailProvider.kt      # 服务商预设
│   ├── MailRepository.kt    # 真实 IMAP/SMTP + 服务器回写 + 附件
│   └── AccountStore.kt      # 账户持久化
└── ui/ (theme/components/screens)
    ├── InboxScreen.kt       # 收件箱（下拉刷新/分类/侧栏/空态）
    ├── AddAccountScreen.kt  # 登录流程
    ├── EmailDetailScreen.kt # 阅读 + 附件下载
    ├── ComposeScreen.kt     # 撰写（真实发送）
    └── DigestScreen.kt      # 摘要
```

## 📦 关键依赖

AGP 8.2.2 / Kotlin 1.9.22 / Compose BOM 2024.09.03 / Compose Compiler 1.5.11 / Haze 1.6.10 / JavaMail(android-mail) 1.6.7 / Navigation 2.8.1

> ⚠️ Haze 用 **v1.x** API：`Modifier.hazeEffect(state)`（毛玻璃）+ 自绘渐变/描边模拟玻璃高光。
