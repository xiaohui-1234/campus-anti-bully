package com.campus.android.core.labels

object DisplayLabels {
    private val EVENT_TYPE_LABELS: Map<String, String> = mapOf(
        "BUTTON" to "按钮",
        "VOICE" to "声音",
        "FIGHT" to "打架",
        "HELP" to "求助",
        "SOS" to "紧急求助"
    )

    private val ALARM_INFO_LABELS: Map<String, String> = mapOf(
        "button_alarm" to "按钮报警",
        "voice_help" to "语音求助",
        "fire" to "着火",
        "kill" to "杀人",
        "fight" to "打架",
        "kidnap" to "绑架",
        "explosion" to "爆炸",
        "blood" to "流血",
        "faint" to "晕倒",
        "stop_hit" to "停止殴打",
        "robbery" to "抢劫",
        "help_me" to "救命",
        "call_people" to "叫人",
        "group_fight" to "群殴",
        "dont_move" to "不许动",
        "general_alarm" to "一般报警"
    )

    private val READ_STATUS_LABELS: Map<String, String> = mapOf(
        "UNREAD" to "未读",
        "READ" to "已读"
    )

    private val FILE_STATUS_LABELS: Map<String, String> = mapOf(
        "UPLOADING" to "上传中",
        "SUCCESS" to "可播放",
        "FAILED" to "上传失败"
    )

    private val PUSH_STATUS_LABELS: Map<String, String> = mapOf(
        "PENDING" to "待通知",
        "PUSHED" to "已通知",
        "FAILED" to "通知失败"
    )

    private val ONLINE_STATUS_LABELS: Map<String, String> = mapOf(
        "ONLINE" to "在线",
        "OFFLINE" to "离线"
    )

    fun eventType(value: String?, fallback: String = "报警事件"): String {
        return label(EVENT_TYPE_LABELS, value, fallback)
    }

    fun alarmInfo(value: String?, fallback: String = "暂无告警信息"): String {
        return label(ALARM_INFO_LABELS, value, fallback)
    }

    fun readStatus(value: String?, fallback: String = "未知"): String {
        return label(READ_STATUS_LABELS, value, fallback)
    }

    fun fileStatus(value: String?, fallback: String = "未知"): String {
        return label(FILE_STATUS_LABELS, value, fallback)
    }

    fun pushStatus(value: String?, fallback: String = "未知"): String {
        return label(PUSH_STATUS_LABELS, value, fallback)
    }

    fun onlineStatus(value: String?, fallback: String = "未知"): String {
        return label(ONLINE_STATUS_LABELS, value, fallback)
    }

    fun eventSummary(
        alarmInfoValue: String?,
        eventTypeValue: String?,
        fallback: String = "请及时查看"
    ): String {
        val alarm = alarmInfo(alarmInfoValue, fallback = "")
        if (alarm.isNotBlank()) return alarm
        return eventType(eventTypeValue, fallback)
    }

    private fun label(labels: Map<String, String>, value: String?, fallback: String): String {
        val key = value?.trim().takeIf { !it.isNullOrEmpty() } ?: return fallback
        return labels[key] ?: key
    }
}
