# 通知档案

原生 Android 通知记录应用，包名 `io.github.a13e300.notificationmanager`，支持 Android 10（API 29）及以上。首次打开后，在系统设置中为“通知档案”开启通知使用权。

## 已实现

- Material 3 Expressive 主题与组件、动态配色、深色模式、边到边布局，Navigation 3 页面导航。
- NotificationListenerService 监听通知；每次发布、更新保存独立快照，连接时采集已有通知。
- Room 保存可检索字段与生命周期，应用私有文件保存 JSON 详情和 PNG 图像。记录数量没有应用层上限，不定期清理，实际受设备空间与 SQLite 限制。
- Paging 分页列表，按应用、标题/正文/应用名称/包名、接收日期范围、移除状态组合筛选。内容搜索按字面子串匹配，`%`、`_` 不是通配符。
- 首页顶部卡片和黑名单页提示先随列表滚走；搜索框到顶后固定显示，仅下方过滤器随向下滚动收起、向上滚动展开。工具栏保持可用，列表视口随输入法调整，末尾内容可滚动到键盘上方。
- 详情展示通知公开字段、可读取属性、extras、操作按钮、RemoteInput、PendingIntent 创建者、渠道、排序及分组信息。Bitmap 原图持久保存，Icon 保存为 128px PNG。
- 记录移除时间和系统原因码，区分旧版本、已移除和连接中断后的未知状态。
- 列表仅给常驻通知显示带图标的“常驻”标签，普通通知不加标签。详情中的“通知类型”卡片显示“常驻通知”“普通通知”或“未知”，可点击复制。类型取自接收快照时的 ongoing 标记，旧记录展示时从归档 JSON 补读，缺失或损坏时详情显示未知。
- 设置项列表带图标；记录开关与常驻通知开关即时生效，无整体保存按钮。规则只影响新快照。
- 应用黑名单使用独立应用列表，可按名称/包名搜索、只看已排除应用。勾选或取消立即生效，已卸载应用的旧条目仍可移除。
- 通知渠道黑名单按包名、用户与渠道 ID 匹配，即时停止对应渠道的新记录。设置页只查看/移除已有规则，从通知详情的应用/渠道卡片可直接加入相应黑名单。规则不删除历史记录或系统通知，清空历史也会保留渠道规则。
- 文本过滤在对话框中编辑标题/正文的包含与排除关键词，显式保存后生效，取消不修改原规则。
- 详情按信息项展示带图标的卡片，点击复制；应用和渠道卡片内可跳转对应系统设置，通知操作也放在卡片内。
- 原始 JSON 通过卡片打开弹窗，支持滚动查看、选择文字和复制全文。
- 图像卡片显示接收时保存的小图标、大图标及附加图片，并标注来源和尺寸；相同内容只显示一份预览，保留原归档文件。点击图像卡片复制其来源和尺寸信息。
- 工具栏使用图标按钮，支持返回、打开记录设置和删除单条记录；批量清理入口统一放在设置页。
- 设置页提供“清空已屏蔽通知记录”和“移除所有记录”，均需二次确认。前者只删除当前应用/渠道黑名单匹配的历史及其文件，后者清空全部历史。二者保留规则、屏蔽名单和系统通知，与新通知写入串行执行。筛选清理按 256 条一批处理，不把全部历史加载到内存中。
- 用户确认后发送当前有效通知的点击/按钮 PendingIntent，或调用监听器 API 移除系统通知。

## 构建

需要 Git、Android SDK 37。Gradle daemon 使用 `gradle/gradle-daemon-jvm.properties` 配置的 JDK 25，可由 Gradle 自动下载；应用的 Java 字节码级别为 17。配置 `ANDROID_HOME` 或本机 `local.properties`：

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest :app:lintDebug
# 请明确指定测试设备，避免对连接的个人设备执行测试
ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedDebugAndroidTest
```

APK 文件名参考 MyInjectorPriv，包含当前 Git 提交次数和短哈希：

- Debug：`app/build/outputs/apk/debug/NotificationManager-<提交次数>-<短哈希>-debug.apk`
- Release：`app/build/outputs/apk/release/NotificationManager-<提交次数>-<短哈希>-release.apk`

应用版本名为 `<提交次数>-<短哈希>`，需在已有提交的 Git 工作区构建。

Release 默认读取仓库根目录的 `keystore.properties`，配置项与 MyInjectorPriv 一致：

```properties
storeFile=../keys/release.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

`storeFile` 的相对路径以 `app/` 目录为基准。`keystore.properties` 和密钥库文件已加入 `.gitignore`。配置文件不存在时使用 Android debug 密钥库签名；当前首次 release 使用此默认方式。

Room 数据库版本保持 **3**，仅保留 `app/schemas/io.github.a13e300.notificationmanager.ArchiveDatabase/3.json`。首次提交不包含旧开发版本的 schema、迁移代码或迁移测试；后续发布版本改表时再添加迁移。


当前采用 AGP 9.4.1、Kotlin Compose 插件 2.4.21、Compose 1.12.1、Material3 1.5.0-beta02、Navigation3 1.2.0、Lifecycle 2.11.0、Room 2.8.5、Paging 3.5.3。Material3 使用当前 Expressive beta API，其余主要 Jetpack 依赖采用稳定版本。版本依据见 [AndroidX 发布页](https://developer.android.com/jetpack/androidx/versions) 和 [Material3 发布说明](https://developer.android.com/jetpack/androidx/releases/compose-material3)。

## 数据与能力边界

这里保存的是监听器实际收到的快照，并非系统通知数据库的完整备份。Android 的脱敏、工作资料策略、监听中断、厂商限制会影响可见性。断开期间无法补回已经移除的通知，重新连接也不能推断准确移除时间。

JSON 是便于检查的结构化归档，不是可反序列化的 Notification。CharSequence 保存纯文本；RemoteViews 保存包名与布局 ID，未保存内部视图动作；未知 Parcelable 保存类型与描述，不支持重放；超过 16 层的嵌套会注明省略。来源应用的 content URI 记录地址，不复制 URI 指向的附件。extras 单字段读取失败会单独标注，不声称完整保存任意厂商对象。

PendingIntent 的 Binder 句柄只留在内存中，更新、进程重启或系统取消后不能依靠历史数据恢复。按钮的 RemoteInput 定义会记录，目前没有输入回复界面。发送 PendingIntent 不等同于完整模拟 SystemUI 的点击统计、自动取消等逻辑；系统移除通过 `cancelNotification(key)` 实现，不直接发送 deleteIntent，以免重复触发删除副作用。

内容搜索目前使用 SQLite 子串扫描；包名与时间有索引，分页限制列表内存，但巨大历史库的全文搜索仍需要后续根据实测引入 FTS。文件和 SQLite 无法跨介质原子提交，进程在文件写入后崩溃可能留下孤立目录；当前不自动回收，避免误删记录。磁盘写入失败会在列表显示错误，不能保证设备故障时不丢记录。

通知数据保存在应用私有目录，无网络权限，并关闭云备份和设备迁移。卸载或清除应用数据会丢失归档。跨用户通知的系统设置跳转受系统权限限制。

搜索框位于列表固定标题中，只有下方过滤器使用 Material3 的 [enter-always 滚动行为](https://developer.android.com/reference/kotlin/androidx/compose/material3/SearchBarScrollBehavior)，且搜索区域实际到顶后才启用；页面边距先通过 `consumeWindowInsets` 消耗，再使用 `imePadding` 避让键盘，处理依据见 [Compose 窗口 Insets 文档](https://developer.android.com/develop/ui/compose/system/insets-ui)。

应用黑名单使用 `QUERY_ALL_PACKAGES` 查询当前用户安装的应用，包含没有启动入口的系统应用；列表同时保留历史通知与旧黑名单中的包名。声明依据见 [Android 官方应用可见性说明](https://developer.android.com/training/package-visibility/declaring)。查询接口的当前用户范围见 [SDK 源码 PackageManager.java](../Android/Sdk/sources/android-37.0/android/content/pm/PackageManager.java#L7384)。

普通通知监听权限不足以枚举其他应用的全部渠道：监听器查询接口还要求伴生设备关联或通知助理身份，见 [官方接口说明](https://developer.android.com/reference/android/service/notification/NotificationListenerService.html#getNotificationChannels(java.lang.String,android.os.UserHandle))。当前渠道名单只显示已添加的屏蔽规则，相关源码依据见特权能力调查第 5 节。

可选特权功能目前完成源码调查，尚未接入 Shizuku/root，详见 [特权能力调查](docs/privileged-capabilities.md)。

## 已完成验证

- Debug APK 构建、规则单元测试、Android 16 上的 Room/快照集成测试通过。
- Android Lint：0 错误，2 项非阻断建议（Gradle 补丁版本和 KTX 写法）。
- 独立 Android 16 模拟器验证了真实 NotificationListener 回调：系统通知标题、正文、图标路径入库，稍后提醒后的移除时间与原因码 18 正确。
- 人工检查列表与详情显示，并检查运行日志无应用崩溃。Android 10–15、小米系统、后台启动受限场景和大规模数据性能尚未做实机验证。
- 详情卡片在独立 Android 16 模拟器验证：应用/渠道设置跳转、卡片点击复制、JSON 弹窗及复制全文。全文复制通过粘贴回应用并与归档原文逐字比对验证。
- 全部移除操作验证覆盖数据库清空、文件清理、规则保留以及与新快照并发写入时的文件一致性。
- 独立 Android 16 模拟器确认工具栏图标导航正常，取消批量移除会保留记录，确认后清空列表与图像目录，系统通知仍保留且新通知可继续入库。
- 设置验证覆盖并发字段更新与旧黑名单保留；Android 16 实际通知验证了开关/黑名单立即生效、文本对话框取消不生效、显式保存后生效，以及进程重启后的持久化。
- ongoing 验证覆盖新快照的 true/false 保存及更新后旧快照的标记保留。Android 16 模拟器检查列表与详情对两种通知的展示。
- 独立 Android 16 模拟器验证两页介绍内容先滚走、搜索框到顶固定，以及过滤器在继续向下滚动时收起、反向滚动时展开；键盘打开及继续滚动时，列表底边均与输入法上边缘对齐。
- 渠道屏蔽验证覆盖不同应用/用户的同名渠道隔离、重复添加及取消屏蔽；清理测试跨多个批次并验证只清理应用或渠道黑名单匹配项。Android 16 界面验证了详情添加、名单移除恢复记录、两种清理的取消/确认以及对应文件清理。
