# 通知扩展能力调查

调查日期：2026-11-01。基础结论来自 AOSP Android 16 源码；MIUI/HyperOS 的差异另标注，不能用 AOSP 推断厂商实现完全相同。

## 1. 移除信息：普通权限可以记录

`NotificationListenerService.onNotificationRemoved(sbn, rankingMap, reason)` 从 API 26 起提供移除原因。`reason` 能区分点击、用户划掉、全部清除、应用取消、监听器取消、超时、渠道变更等。应用已保存原始整数及文字说明。移除回调中的 SBN 是轻量版本，因此保留之前收到的完整快照，仅更新生命周期。

参考 [NotificationListenerService.java 第 535 行起](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/service/notification/NotificationListenerService.java#535)。移除时间是本应用收到回调的时间，不是精确系统操作时间。原因码表无法提供操作者身份或区分所有厂商动作。断连期间的移除无法事后可靠恢复。

## 2. 发送 PendingIntent 与移除通知：可以，但不能完全复现 SystemUI

普通监听器能取得 `contentIntent`、`deleteIntent`、`fullScreenIntent` 和 `actions[].actionIntent`，持有有效句柄时可以 `send()`。需处理失效、权限限制及系统后台启动规则；本实现只在用户可见界面主动点击后发送，对 Android 14+ 提供启动选项，Android 16+ 使用仅界面可见时允许启动的选项。

[PendingIntent.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/app/PendingIntent.java) 的发送接口会调用系统 IntentSender。执行应用提供的 Intent 不会自动复现 SystemUI 的点击上报、通知关闭或所有启动选项，也不能保证发送方业务一定成功。

移除使用 [NotificationListenerService.cancelNotification，第 744 行](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/service/notification/NotificationListenerService.java#744)。不要用 `deleteIntent.send()` 代替通知移除：前者只是执行通知附带的删除动作，不会自行从 NotificationManager 移除通知，且可能导致重复副作用。常驻/前台服务等通知能否取消由系统决定。

历史持久化不保存 PendingIntent 的 Parcel 原始字节。Binder 引用不是跨重启稳定的存储格式；重新构建显式 Intent 也不等同于原 PendingIntent 的创建者身份和授权。

## 3. 提取真实 Intent：有特权实现路径

`PendingIntent.getIntent()` 是隐藏方法，最终调用 `ActivityManager.getService().getIntentForIntentSender(mTarget)`。参考 [PendingIntent.java 第 1366 行](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/app/PendingIntent.java#1366)。

系统服务 [ActivityManagerService.java 第 5742–5753 行](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/am/ActivityManagerService.java#5742) 先校验 `GET_INTENT_SENDER_INTENT`，再返回 `requestIntent` 的副本。该权限是 signature 级别，见 [AndroidManifest.xml 第 4427 行](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/res/AndroidManifest.xml#4427)。仅绕过隐藏 API 限制不够。

AOSP 的 [Shell 清单第 381 行](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/packages/Shell/AndroidManifest.xml#381) 声明此权限，因此在 AOSP Android 16 上可以设计 Shizuku shell UserService 路径。通过 AIDL 将 PendingIntent 传入 UserService，在特权进程读取 mTarget 并调用 IActivityManager，返回只包含数据的结果；root 也可作为该进程的身份。不能只在普通应用进程反射调用 getIntent 然后期望自动获得 Shizuku 权限。

返回的是服务当前保存的 requestIntent，不保证获取 getActivities 的完整任务栈，也不代表所有动态 fillIn 参数或最终解析结果。提取应在接收时进行，分别记录提取时间、运行 UID、结果或失败原因；Intent 字段应分项存储 action/data/type/component/package/categories/flags/extras，Intent URI 不能完整表达所有 extras。此处为方案，当前 APK 未实现该桥接。

[Shizuku 官方 API 文档](https://github.com/RikkaApps/Shizuku-API#userservice) 说明 UserService 在 root/shell 身份运行、可使用非 SDK API，同时提醒该进程不是正常 Android 应用进程。厂商系统仍可能修改权限或 SELinux 策略，需要实机验证。

## 4. 发送者、隐藏包名、PID 和进程：可获取部分，需保留语义

| 字段 | 含义 | 获取路径 |
| --- | --- | --- |
| packageName | SDK 暴露的通知所属应用，可能受厂商改写 | 普通模式已保存 |
| opPkg | 发布通知的包，可与所属应用不同 | API 29+ 已保存 |
| uid / user | 通知所属应用 UID 和资料用户 | 普通模式已保存 |
| PendingIntent.creatorPackage / creatorUid | 操作令牌的创建者，不能据此认定通知发送者 | 普通模式已保存 |
| SBN.pkg 原始字段 | SBN 中保存的原始包名 | 隐藏 API 读取，待接入 |
| SBN.initialPid | 系统构建 SBN 时传入的调用 PID | 隐藏 API 读取，待接入 |

[StatusBarNotification.java 第 55–68 行](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/service/notification/StatusBarNotification.java#55) 定义 pkg、opPkg、initialPid；[第 386–400 行](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/service/notification/StatusBarNotification.java#386) 给出 opPkg 的说明与隐藏 getInitialPid。

[NotificationManagerService.java 第 8454–8456 行](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/notification/NotificationManagerService.java#8454) 将 callingPid 写入 SBN。它是“发布调用时的 PID”，不保证收到通知时进程仍活着，也不是任意时刻所属应用的 PID。系统合成通知及通知代理场景需要另外标注。

[MyInjectorPriv 的 SystemUIHandler.kt 第 208–235 行](../../MyInjectorPriv/app/src/main/java/io/github/a13e300/myinjector/SystemUIHandler.kt#L208) 明确说明 MIUI 会改写 getPackageName，并读取 `pkg`、`opPkg`、`initialPid`。这证明该项目采用了这个适配路径，但它运行在注入的 SystemUI 环境，不能证明普通监听器进程拥有相同权限，也不能证明所有 HyperOS 版本都采用同样实现。

SBN 对象中的 pkg 和 initialPid 是已被 Parcel 交付的数据，原则上读取字段本身需要处理隐藏 API，不必为每次读取都请求 root。若采用 Shizuku UserService，可把 SBN 通过 Binder 传入特权进程读取并保留公开包名和原始包名，避免直接覆盖为一个“真实发送者”。这仍需在目标 MIUI 版本验证 Parcel 内容是否已被改写。

进程反查应尽快在特权进程读取 `/proc/<pid>/status`、`cmdline`、`stat` 和可用的 ActivityManager 进程信息，记录 pid、UID、进程名、进程启动 ticks、采集时间与设备 boot ID。联合 UID 和启动 ticks 检查 PID 复用；无法匹配就记录“已退出/无法确认”，不要把当前占用相同 PID 的进程记成发送者。`getRunningAppProcesses()` 在普通应用中受可见范围限制，shell 的 /proc 读取也受内核和 SELinux 限制，root 权限不保证绕过所有策略。

结论：通知事件日志及当前操作已实现；Intent 提取、原始 pkg/PID 和进程归属目前是有源码依据的可选扩展方案，尚未作为已实现功能发布，也未在小米实机验证。


## 5. 枚举其他应用的所有通知渠道

存在公开接口 `NotificationListenerService.getNotificationChannels(pkg, user)`，但调用者必须具有关联的伴生设备，或是通知助理，并有对应用户的通知访问权。普通通知使用权本身不足以调用。见 [官方 API 说明](https://developer.android.com/reference/android/service/notification/NotificationListenerService.html#getNotificationChannels(java.lang.String,android.os.UserHandle))，以及[SDK 37 的 NotificationListenerService.java 第 990–1006 行](../../Android/Sdk/sources/android-37.0/android/service/notification/NotificationListenerService.java#L990)。

AOSP Android 16 的 [NotificationManagerService.java 第 7121 行](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/notification/NotificationManagerService.java#7121) 调用 `verifyPrivilegedListener`；[第 7195–7210 行](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/notification/NotificationManagerService.java#7195) 检查伴生设备或通知助理身份及用户范围。

普通 `NotificationManager.getNotificationChannels()` 查询的是调用包或已获通知代理授权的包，创建其他应用的 Context 不会自行获得代理权限，见[SDK 37 的 NotificationManager.java 第 1399–1408 行](../../Android/Sdk/sources/android-37.0/android/app/NotificationManager.java#L1399)。

本版本只展示渠道屏蔽规则，不枚举应用的完整渠道清单。用户从已归档通知的详情添加对应渠道，匹配 `Notification.channelId`，并使用包名、用户 ID 隔离。渠道名称仅用于展示，不参与规则匹配。
