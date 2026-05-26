#ifndef __CAMPUS_MQTT_H
#define __CAMPUS_MQTT_H

#include "stm32f10x.h"
#include <stdbool.h>

void Campus_MQTT_OnConnected(void);
bool Campus_MQTT_PublishOnline(void);
bool Campus_MQTT_SetPendingAlarm(const char *eventType, const char *alarmInfo);
bool Campus_MQTT_PostAlarmForFile(const char *wavPath, uint32_t fileSize);
void Campus_MQTT_ProcessSubRecv(void);
void Campus_MQTT_Service(uint32_t nowMs);

#endif
