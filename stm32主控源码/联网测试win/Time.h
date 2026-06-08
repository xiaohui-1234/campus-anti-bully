#ifndef __TIME_H
#define __TIME_H

#include "stm32f10x.h"

#define RX_BUF_MAX_LEN     1024

extern volatile uint8_t g_MqttTimRecvPending;
extern char g_MqttTimeRecvBuf[RX_BUF_MAX_LEN];

uint8_t Time_UpdateFromString(const char *timeStr);
uint8_t Time_IsValid(void);
uint8_t Time_GetTimestampMsText(char *out, uint32_t outSize);
uint8_t Time_GenerateRecordPath(char *path, uint32_t pathSize);

#endif
