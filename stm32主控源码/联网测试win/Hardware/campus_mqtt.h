#ifndef __CAMPUS_MQTT_H
#define __CAMPUS_MQTT_H

#include "stm32f10x.h"
#include <stdbool.h>

// MQTT连接建立后调用：发布设备上线状态。
void Campus_MQTT_OnConnected(void);

// 主动发布设备在线心跳/上线状态。
bool Campus_MQTT_PublishOnline(void);

// 记录待处理告警上下文；录音结束后会带着该上下文发起告警上报。
bool Campus_MQTT_SetPendingAlarm(const char *eventType, const char *alarmInfo);

// 将本地WAV文件信息上报到后端，等待后端返回上传URL。
bool Campus_MQTT_PostAlarmForFile(const char *wavPath, uint32_t fileSize);

// 处理ESP8266收到的MQTT订阅消息，主要用于执行后端下发的录音文件上传指令。
void Campus_MQTT_ProcessSubRecv(void);

// 周期服务函数：在主循环中调用，用于发布在线心跳。
void Campus_MQTT_Service(uint32_t nowMs);

#endif
