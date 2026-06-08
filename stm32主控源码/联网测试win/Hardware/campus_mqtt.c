#include "campus_mqtt.h"
#include "campus_config.h"
#include "esp8266.h"
#include "ff.h"
#include "LED.h"
#include "System/Delay.h"
#include "System/bsp_timer.h"
#include "Time.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// 后端上传指令去重记录，避免同一条MQTT消息重复触发文件上传。
typedef struct {
	char mqtt_msg_id[48];
	char event_id[64];
	char object_key[160];
	uint8_t used;
	uint8_t finished;
	uint8_t success;
} CAMPUS_DEDUP_ENTRY;

// 当前待上传告警的上下文；由告警触发时写入，文件上传确认后清空。
typedef struct {
	char event_type[16];
	char alarm_info[48];
	char wav_path[64];
	char post_msg_id[48];
	uint32_t file_size;
	uint8_t active;
} CAMPUS_ALARM_CONTEXT;

static CAMPUS_DEDUP_ENTRY g_DedupCache[CAMPUS_DEDUP_CACHE_SIZE];
static uint8_t g_DedupCursor = 0;
static CAMPUS_ALARM_CONTEXT g_AlarmCtx;
static uint32_t g_MsgCounter = 0;

static char g_PayloadBuf[CAMPUS_MQTT_PAYLOAD_MAX_LEN];
static char g_MqttCmdBuf[CAMPUS_MQTT_CMD_MAX_LEN];
static char g_UploadHost[CAMPUS_HTTP_HOST_MAX_LEN];
static char g_UploadHostHeader[CAMPUS_HTTP_HOST_MAX_LEN + 8U];
static char g_UploadPort[8];
static char g_UploadPath[CAMPUS_HTTP_PATH_MAX_LEN];
static char g_HttpHead[1024];
static uint8_t g_UploadBuf[CAMPUS_HTTP_UPLOAD_CHUNK_SIZE];
static char g_UploadUrl[CAMPUS_HTTP_PATH_MAX_LEN];
static char g_SubPayload[RX_BUF_MAX_LEN];

// 生成毫秒级时间戳文本；时间未同步时由Time模块给出兜底格式。
static void Campus_BuildTimestamp(char *out, uint32_t outSize)
{
	if ((out == 0) || (outSize == 0U)) {
		return;
	}
	(void)Time_GetTimestampMsText(out, outSize);
}

// 生成MQTT消息ID：优先使用真实时间戳，未校时时使用运行毫秒数。
static void Campus_BuildMsgId(char *out, uint32_t outSize)
{
	char ts[24];
	uint32_t nowMs;
	uint8_t hasTime;

	if ((out == 0) || (outSize == 0U)) {
		return;
	}
	g_MsgCounter++;
	if (g_MsgCounter > 999UL) {
		g_MsgCounter = 1UL;
	}

	hasTime = Time_GetTimestampMsText(ts, sizeof(ts));
	if (hasTime) {
		(void)snprintf(out, outSize, "%s-%03lu-%s",
			ts,
			(unsigned long)g_MsgCounter,
			CAMPUS_DEVICE_ID);
	} else {
		nowMs = App_Millis();
		(void)snprintf(out, outSize, "u%010lu-%03lu-%s",
			(unsigned long)nowMs,
			(unsigned long)g_MsgCounter,
			CAMPUS_DEVICE_ID);
	}
}

// 从简单JSON对象中读取字符串字段；当前协议字段不包含转义引号。
static uint8_t Campus_JsonGetString(const char *json, const char *key, char *out, uint32_t outSize)
{
	char pattern[40];
	const char *p;
	const char *e;
	uint32_t len;

	if ((json == 0) || (key == 0) || (out == 0) || (outSize == 0U)) {
		return 0;
	}

	(void)snprintf(pattern, sizeof(pattern), "\"%s\":\"", key);
	p = strstr(json, pattern);
	if (p == 0) {
		out[0] = '\0';
		return 0;
	}
	p += strlen(pattern);
	e = strchr(p, '"');
	if (e == 0) {
		out[0] = '\0';
		return 0;
	}

	len = (uint32_t)(e - p);
	if (len >= outSize) {
		len = outSize - 1U;
	}
	(void)memcpy(out, p, len);
	out[len] = '\0';
	return 1;
}

// 从ESP8266的+MQTTSUBRECV原始回包里提取指定topic的payload。
static uint8_t Campus_ExtractMqttSubPayload(const char *raw, const char *topic, char *out, uint32_t outSize)
{
	const char *p;
	const char *topicStart;
	const char *topicEnd;
	const char *lenStart;
	const char *payloadStart;
	uint32_t topicLen;
	uint32_t payloadLen;
	uint32_t copied = 0U;
	uint32_t available;
	char topicBuf[160];

	if ((raw == 0) || (topic == 0) || (out == 0) || (outSize == 0U)) {
		return 0;
	}
	out[0] = '\0';

	p = raw;
	while ((p = strstr(p, "+MQTTSUBRECV:")) != 0) {
		topicStart = strchr(p, '"');
		if (topicStart == 0) {
			break;
		}
		topicStart++;
		topicEnd = strchr(topicStart, '"');
		if (topicEnd == 0) {
			break;
		}

		topicLen = (uint32_t)(topicEnd - topicStart);
		if (topicLen >= sizeof(topicBuf)) {
			topicLen = sizeof(topicBuf) - 1U;
		}
		(void)memcpy(topicBuf, topicStart, topicLen);
		topicBuf[topicLen] = '\0';

		lenStart = strchr(topicEnd + 1, ',');
		if (lenStart == 0) {
			break;
		}
		lenStart++;
		payloadLen = (uint32_t)atoi(lenStart);
		payloadStart = strchr(lenStart, ',');
		if ((payloadStart == 0) || (payloadLen == 0U)) {
			break;
		}
		payloadStart++;

		available = (uint32_t)strlen(payloadStart);
		if (payloadLen > available) {
			payloadLen = available;
		}

		if (strcmp(topicBuf, topic) == 0) {
			if ((copied + payloadLen) >= outSize) {
				payloadLen = outSize - copied - 1U;
			}
			(void)memcpy(out + copied, payloadStart, payloadLen);
			copied += payloadLen;
			out[copied] = '\0';
			if ((copied + 1U) >= outSize) {
				break;
			}
		}

		p = payloadStart + payloadLen;
	}

	return (copied != 0U) ? 1U : 0U;
}

// 发布MQTT时可能夹带订阅回包，这里先缓存起来，交给主循环统一处理。
static void Campus_SaveSubRecvFromRaw(const char *raw)
{
	uint32_t oldLen;
	uint32_t copyLen;

	if ((raw == 0) || (strstr(raw, "+MQTTSUBRECV") == 0)) {
		return;
	}

	oldLen = g_MqttSubRecvPending ? (uint32_t)strlen(g_MqttSubRecvBuf) : 0U;
	copyLen = (uint32_t)strlen(raw);
	if ((oldLen + copyLen) >= RX_BUF_MAX_LEN) {
		oldLen = 0U;
		if (copyLen >= RX_BUF_MAX_LEN) {
			copyLen = RX_BUF_MAX_LEN - 1U;
		}
	}

	(void)memcpy(g_MqttSubRecvBuf + oldLen, raw, copyLen);
	g_MqttSubRecvBuf[oldLen + copyLen] = '\0';
	g_MqttSubRecvPending = 1U;
}

// 使用ESP8266的MQTTPUBRAW指令发送JSON，确保payload长度不受AT命令行限制。
static bool Campus_MQTT_PublishJson(const char *topic, const char *payload)
{
	int written;
	u32 payloadLen;
	bool ok = false;

	if ((topic == 0) || (payload == 0)) {
		return false;
	}

	payloadLen = (u32)strlen(payload);
	written = snprintf(g_MqttCmdBuf, sizeof(g_MqttCmdBuf),
		"AT+MQTTPUBRAW=0,\"%s\",%lu,%u,0",
		topic,
		(unsigned long)payloadLen,
		(unsigned int)CAMPUS_MQTT_QOS);
	if ((written <= 0) || ((uint32_t)written >= sizeof(g_MqttCmdBuf))) {
		printf("MQTT raw cmd too long\r\n");
		return false;
	}

	g_ESP8266RawBusy = 1U;
	strEsp8266_Fram_Record.InfBit.FramLength = 0;
	strEsp8266_Fram_Record.InfBit.FramFinishFlag = 0;
	macESP8266_Usart("%s\r\n", g_MqttCmdBuf);
	if (!ESP8266_WaitResponse("OK", "ERROR", 3000)) {
		printf("MQTT raw command timeout\r\n");
		printf("%s\r\n", strEsp8266_Fram_Record.Data_RX_BUF);
		Campus_SaveSubRecvFromRaw(strEsp8266_Fram_Record.Data_RX_BUF);
		g_MqttConnected = 0U;
		goto EXIT_PUBLISH;
	}
	if (strstr(strEsp8266_Fram_Record.Data_RX_BUF, "ERROR") != 0) {
		printf("MQTT raw command rejected\r\n");
		Campus_SaveSubRecvFromRaw(strEsp8266_Fram_Record.Data_RX_BUF);
		g_MqttConnected = 0U;
		goto EXIT_PUBLISH;
	}
	if (strstr(strEsp8266_Fram_Record.Data_RX_BUF, ">") == 0) {
		if (!ESP8266_WaitResponse(">", "ERROR", 5000)) {
			printf("MQTT raw prompt timeout\r\n");
			printf("%s\r\n", strEsp8266_Fram_Record.Data_RX_BUF);
			Campus_SaveSubRecvFromRaw(strEsp8266_Fram_Record.Data_RX_BUF);
			g_MqttConnected = 0U;
			goto EXIT_PUBLISH;
		}
		if (strstr(strEsp8266_Fram_Record.Data_RX_BUF, "ERROR") != 0) {
			printf("MQTT raw prompt rejected\r\n");
			Campus_SaveSubRecvFromRaw(strEsp8266_Fram_Record.Data_RX_BUF);
			g_MqttConnected = 0U;
			goto EXIT_PUBLISH;
		}
	}

	strEsp8266_Fram_Record.InfBit.FramLength = 0;
	strEsp8266_Fram_Record.InfBit.FramFinishFlag = 0;
	ESP8266_SendRawBuffer((const uint8_t *)payload, payloadLen);
	if (!ESP8266_WaitResponse("+MQTTPUB:OK", "+MQTTPUB:FAIL", 15000)) {
		printf("MQTT raw publish timeout\r\n");
		printf("%s\r\n", strEsp8266_Fram_Record.Data_RX_BUF);
		Campus_SaveSubRecvFromRaw(strEsp8266_Fram_Record.Data_RX_BUF);
		g_MqttConnected = 0U;
		goto EXIT_PUBLISH;
	}
	Campus_SaveSubRecvFromRaw(strEsp8266_Fram_Record.Data_RX_BUF);

	if (strstr(strEsp8266_Fram_Record.Data_RX_BUF, "+MQTTPUB:FAIL") != 0) {
		printf("MQTT raw publish failed\r\n");
		g_MqttConnected = 0U;
		goto EXIT_PUBLISH;
	}
	ok = true;

EXIT_PUBLISH:
	g_ESP8266RawBusy = 0U;
	return ok;
}

// MQTT连接成功后的入口，当前只发布一次上线状态。
void Campus_MQTT_OnConnected(void)
{
	(void)Campus_MQTT_PublishOnline();
}

// 组装并发布设备在线状态，用于上线通知和周期心跳。
bool Campus_MQTT_PublishOnline(void)
{
	char msgId[48];
	char timestamp[24];

	Campus_BuildMsgId(msgId, sizeof(msgId));
	Campus_BuildTimestamp(timestamp, sizeof(timestamp));
	(void)snprintf(g_PayloadBuf, sizeof(g_PayloadBuf),
		"{\"mqtt_msg_id\":\"%s\",\"product_type\":\"%s\",\"device_id\":\"%s\",\"status\":\"online\",\"timestamp\":%s}",
		msgId,
		CAMPUS_PRODUCT_TYPE,
		CAMPUS_DEVICE_ID,
		timestamp);

	return Campus_MQTT_PublishJson(CAMPUS_TOPIC_STATUS_ONLINE, g_PayloadBuf);
}

// 告警发生时先保存事件类型和文案，等待录音文件生成后再上报文件信息。
bool Campus_MQTT_SetPendingAlarm(const char *eventType, const char *alarmInfo)
{
	if ((eventType == 0) || (alarmInfo == 0)) {
		return false;
	}

	(void)memset(&g_AlarmCtx, 0, sizeof(g_AlarmCtx));
	(void)strncpy(g_AlarmCtx.event_type, eventType, sizeof(g_AlarmCtx.event_type) - 1U);
	(void)strncpy(g_AlarmCtx.alarm_info, alarmInfo, sizeof(g_AlarmCtx.alarm_info) - 1U);
	g_AlarmCtx.active = 1U;
	return true;
}

// 上报告警录音文件元数据；后端收到后会通过订阅topic返回预签名上传URL。
bool Campus_MQTT_PostAlarmForFile(const char *wavPath, uint32_t fileSize)
{
	char msgId[48];
	char timestamp[24];
	const char *fileName;
	int written;

	if ((wavPath == 0) || (wavPath[0] == '\0') || (g_AlarmCtx.active == 0U)) {
		return false;
	}

	fileName = strrchr(wavPath, '/');
	if (fileName != 0) {
		fileName++;
	} else {
		fileName = strrchr(wavPath, '\\');
		fileName = (fileName != 0) ? (fileName + 1) : wavPath;
	}

	Campus_BuildMsgId(msgId, sizeof(msgId));
	Campus_BuildTimestamp(timestamp, sizeof(timestamp));
	(void)strncpy(g_AlarmCtx.post_msg_id, msgId, sizeof(g_AlarmCtx.post_msg_id) - 1U);
	(void)strncpy(g_AlarmCtx.wav_path, wavPath, sizeof(g_AlarmCtx.wav_path) - 1U);
	g_AlarmCtx.file_size = fileSize;

	written = snprintf(g_PayloadBuf, sizeof(g_PayloadBuf),
		"{\"mqtt_msg_id\":\"%s\",\"product_type\":\"%s\",\"device_id\":\"%s\",\"event_type\":\"%s\",\"alarm_info\":\"%s\",\"file_name\":\"%s\",\"file_size\":%lu,\"content_type\":\"%s\",\"timestamp\":%s}",
		msgId,
		CAMPUS_PRODUCT_TYPE,
		CAMPUS_DEVICE_ID,
		g_AlarmCtx.event_type,
		g_AlarmCtx.alarm_info,
		fileName,
		(unsigned long)fileSize,
		CAMPUS_ALARM_CONTENT_TYPE,
		timestamp);
	if ((written <= 0) || ((uint32_t)written >= sizeof(g_PayloadBuf))) {
		printf("alarm/post payload too long\r\n");
		g_AlarmCtx.active = 0U;
		return false;
	}

	if (!Campus_MQTT_PublishJson(CAMPUS_TOPIC_ALARM_POST, g_PayloadBuf)) {
		printf("alarm/post failed\r\n");
		return false;
	}

	printf("alarm/post ok\r\n");
	return true;
}

// 在去重缓存中查找已处理或正在处理的上传指令。
static CAMPUS_DEDUP_ENTRY *Campus_DedupFind(const char *mqttMsgId, const char *eventId)
{
	uint8_t i;

	for (i = 0; i < CAMPUS_DEDUP_CACHE_SIZE; i++) {
		if ((g_DedupCache[i].used != 0U) &&
			(strcmp(g_DedupCache[i].mqtt_msg_id, mqttMsgId) == 0) &&
			(strcmp(g_DedupCache[i].event_id, eventId) == 0)) {
			return &g_DedupCache[i];
		}
	}
	return 0;
}

// 记住一条上传指令；缓存满后按环形游标覆盖最旧槽位。
static CAMPUS_DEDUP_ENTRY *Campus_DedupRemember(const char *mqttMsgId, const char *eventId, const char *objectKey)
{
	CAMPUS_DEDUP_ENTRY *entry;

	/* Ring-cache dedup: mqtt_msg_id + event_id identifies one upload command. */
	entry = Campus_DedupFind(mqttMsgId, eventId);
	if (entry != 0) {
		return entry;
	}

	entry = &g_DedupCache[g_DedupCursor];
	g_DedupCursor++;
	if (g_DedupCursor >= CAMPUS_DEDUP_CACHE_SIZE) {
		g_DedupCursor = 0U;
	}

	(void)memset(entry, 0, sizeof(*entry));
	entry->used = 1U;
	(void)strncpy(entry->mqtt_msg_id, mqttMsgId, sizeof(entry->mqtt_msg_id) - 1U);
	(void)strncpy(entry->event_id, eventId, sizeof(entry->event_id) - 1U);
	(void)strncpy(entry->object_key, objectKey, sizeof(entry->object_key) - 1U);
	return entry;
}

// 解析后端返回的HTTP上传URL，拆出主机、端口和请求路径。
static uint8_t Campus_ParseHttpUrl(const char *url, const char *objectKey)
{
	const char *p;
	const char *hostStart;
	const char *pathStart;
	const char *colon;
	uint32_t hostLen;
	uint32_t pathLen;

	if ((url == 0) || (strncmp(url, "http://", 7) != 0)) {
		printf("only http upload url supported\r\n");
		return 0;
	}

	hostStart = url + 7;
	pathStart = strchr(hostStart, '/');
	if (pathStart == 0) {
		return 0;
	}

	colon = 0;
	for (p = hostStart; p < pathStart; p++) {
		if (*p == ':') {
			colon = p;
			break;
		}
	}

	if (colon != 0) {
		hostLen = (uint32_t)(colon - hostStart);
		p = colon + 1;
		pathLen = (uint32_t)(pathStart - p);
		if ((pathLen == 0U) || (pathLen >= sizeof(g_UploadPort))) {
			return 0;
		}
		(void)memcpy(g_UploadPort, p, pathLen);
		g_UploadPort[pathLen] = '\0';
	} else {
		hostLen = (uint32_t)(pathStart - hostStart);
		(void)strncpy(g_UploadPort, "80", sizeof(g_UploadPort) - 1U);
		g_UploadPort[sizeof(g_UploadPort) - 1U] = '\0';
	}

	if ((hostLen == 0U) || (hostLen >= sizeof(g_UploadHost))) {
		return 0;
	}

	(void)memcpy(g_UploadHost, hostStart, hostLen);
	g_UploadHost[hostLen] = '\0';

	(void)objectKey;
	pathLen = (uint32_t)strlen(pathStart);
	if ((pathLen == 0U) || (pathLen >= sizeof(g_UploadPath))) {
		return 0;
	}
	(void)memcpy(g_UploadPath, pathStart, pathLen);
	g_UploadPath[pathLen] = '\0';
	return 1;
}

// 打印上传URL的关键字段，便于现场排查预签名URL是否完整。
static void Campus_PrintUploadHeadInfo(const char *objectKey, uint32_t fileSize, int headLen)
{
	const char *query;
	uint32_t requestLineLen;

	query = strchr(g_UploadPath, '?');
	requestLineLen = (uint32_t)strlen(g_UploadPath) + 13U;
	printf("upload host=%s:%s, head=%d, line=%lu, file=%lu\r\n",
		g_UploadHost,
		g_UploadPort,
		headLen,
		(unsigned long)requestLineLen,
		(unsigned long)fileSize);
	printf("upload path_len=%lu, key=%u, query=%u, alg=%u, cred=%u, signed=%u, sig=%u\r\n",
		(unsigned long)strlen(g_UploadPath),
		((objectKey != 0) && (strstr(g_UploadPath, objectKey) != 0)) ? 1U : 0U,
		(query != 0) ? 1U : 0U,
		(strstr(g_UploadPath, "X-Amz-Algorithm=") != 0) ? 1U : 0U,
		(strstr(g_UploadPath, "X-Amz-Credential=") != 0) ? 1U : 0U,
		(strstr(g_UploadPath, "X-Amz-SignedHeaders=") != 0) ? 1U : 0U,
		(strstr(g_UploadPath, "X-Amz-Signature=") != 0) ? 1U : 0U);
}

// 发送HTTP请求头后快速检查服务器是否提前返回错误。
static uint8_t Campus_HttpHasEarlyError(void)
{
	strEsp8266_Fram_Record.Data_RX_BUF[strEsp8266_Fram_Record.InfBit.FramLength] = '\0';
	if ((strstr(strEsp8266_Fram_Record.Data_RX_BUF, "HTTP/1.1 400") != 0) ||
		(strstr(strEsp8266_Fram_Record.Data_RX_BUF, "HTTP/1.1 403") != 0) ||
		(strstr(strEsp8266_Fram_Record.Data_RX_BUF, "HTTP/1.1 500") != 0) ||
		(strstr(strEsp8266_Fram_Record.Data_RX_BUF, "HTTP/1.1 503") != 0)) {
		return 1U;
	}
	return 0U;
}

// 进入ESP8266透传模式，按HTTP PUT格式把WAV文件分块上传到对象存储。
static uint8_t Campus_HttpPutFileTransparent(FIL *fp, int headLen, uint32_t fileSize)
{
	FRESULT fr;
	UINT br;
	uint8_t ok = 0U;
	uint8_t streamStarted = 0U;
	uint8_t transparentModeEnabled = 0U;
	uint32_t sentBytes = 0U;

	printf("upload transparent start\r\n");
	ucTcpClosedFlag = 0;
	g_ESP8266RawBusy = 1;
	(void)ESP8266_Cmd("AT+CIPCLOSE", "OK", "ERROR", 1000);
	(void)ESP8266_Cmd("AT+CIPMUX=0", "OK", 0, 1000);
	(void)ESP8266_Cmd("AT+CIPMODE=0", "OK", 0, 1000);
	(void)ESP8266_WaitResponse("OK", "ERROR", 100);

	if (!ESP8266_Link_Server(enumTCP, g_UploadHost, g_UploadPort, Single_ID_0)) {
		printf("upload tcp link fail\r\n");
		goto EXIT_TRANSPARENT;
	}

	if (!ESP8266_UnvarnishSend()) {
		printf("upload transparent mode fail\r\n");
		goto EXIT_TRANSPARENT;
	}
	transparentModeEnabled = 1U;

	if (!ESP8266_Cmd("AT+CIPSEND", ">", 0, 5000)) {
		printf("upload stream start fail\r\n");
		goto EXIT_TRANSPARENT;
	}
	streamStarted = 1U;
	Delay_ms(150);

	strEsp8266_Fram_Record.InfBit.FramLength = 0;
	strEsp8266_Fram_Record.InfBit.FramFinishFlag = 0;
	ESP8266_SendRawBuffer((const uint8_t *)g_HttpHead, (u32)headLen);
	Delay_ms(300);
	if (Campus_HttpHasEarlyError()) {
		printf("upload header rejected\r\n");
		printf("%s\r\n", strEsp8266_Fram_Record.Data_RX_BUF);
		goto EXIT_TRANSPARENT;
	}

	while (1) {
		fr = f_read(fp, g_UploadBuf, sizeof(g_UploadBuf), &br);
		if (fr != FR_OK) {
			printf("upload read fail fr=%d\r\n", (int)fr);
			goto EXIT_TRANSPARENT;
		}
		if (br == 0U) {
			break;
		}

		ESP8266_SendRawBuffer(g_UploadBuf, (u32)br);
		sentBytes += (uint32_t)br;
		if (((sentBytes & 0x3FFFU) == 0U) || (sentBytes >= fileSize)) {
			printf("upload sent %lu/%lu\r\n", (unsigned long)sentBytes, (unsigned long)fileSize);
		}
	}

	if (!ESP8266_WaitResponse("HTTP/1.1 200", "HTTP/1.1 201", CAMPUS_HTTP_UPLOAD_RESPONSE_TIMEOUT_MS)) {
		if (ucTcpClosedFlag) {
			printf("upload http closed\r\n");
		} else {
			printf("upload http timeout\r\n");
		}
		printf("%s\r\n", strEsp8266_Fram_Record.Data_RX_BUF);
		goto EXIT_TRANSPARENT;
	}

	if ((strstr((char *)strEsp8266_Fram_Record.Data_RX_BUF, "HTTP/1.1 200") == 0) &&
		(strstr((char *)strEsp8266_Fram_Record.Data_RX_BUF, "HTTP/1.1 201") == 0)) {
		printf("upload http reject\r\n");
		goto EXIT_TRANSPARENT;
	}

	ok = 1U;

EXIT_TRANSPARENT:
	if (streamStarted) {
		ESP8266_ExitUnvarnishSend();
	}
	(void)ESP8266_Cmd("AT+CIPCLOSE", "OK", "ERROR", 2000);
	if (transparentModeEnabled) {
		(void)ESP8266_Cmd("AT+CIPMODE=0", "OK", 0, 1000);
	}
	g_ESP8266RawBusy = 0;
	return ok;
}

// 打开本地WAV文件，生成HTTP PUT请求头，并调用透传上传流程。
static uint8_t Campus_HttpPutFile(const char *url, const char *path, const char *objectKey)
{
	FIL fp;
	FRESULT fr;
	uint32_t fileSize;
	int headLen;
	uint8_t ok = 0;

	if ((url == 0) || (path == 0) || (path[0] == '\0')) {
		return 0;
	}
	if (!Campus_ParseHttpUrl(url, objectKey)) {
		return 0;
	}
	if (strcmp(g_UploadPort, "80") == 0) {
		(void)strncpy(g_UploadHostHeader, g_UploadHost, sizeof(g_UploadHostHeader) - 1U);
		g_UploadHostHeader[sizeof(g_UploadHostHeader) - 1U] = '\0';
	} else {
		(void)snprintf(g_UploadHostHeader, sizeof(g_UploadHostHeader), "%s:%s", g_UploadHost, g_UploadPort);
	}

	fr = f_open(&fp, path, FA_READ);
	if (fr != FR_OK) {
		printf("upload open fail fr=%d\r\n", (int)fr);
		return 0;
	}

	fileSize = (uint32_t)f_size(&fp);
	if (fileSize == 0U) {
		(void)f_close(&fp);
		return 0;
	}

	headLen = snprintf(g_HttpHead, sizeof(g_HttpHead),
		"PUT %s HTTP/1.1\r\n"
		"Host: %s\r\n"
		"Content-Type: " CAMPUS_ALARM_CONTENT_TYPE "\r\n"
		"Content-Length: %lu\r\n"
		"Connection: close\r\n"
		"\r\n",
		g_UploadPath,
		g_UploadHostHeader,
		(unsigned long)fileSize);
	if ((headLen <= 0) || ((uint32_t)headLen >= sizeof(g_HttpHead))) {
		printf("upload header too long\r\n");
		(void)f_close(&fp);
		return 0;
	}
	Campus_PrintUploadHeadInfo(objectKey, fileSize, headLen);
	if ((strstr(g_UploadPath, "X-Amz-Algorithm=") == 0) ||
		(strstr(g_UploadPath, "X-Amz-Credential=") == 0) ||
		(strstr(g_UploadPath, "X-Amz-SignedHeaders=") == 0) ||
		(strstr(g_UploadPath, "X-Amz-Signature=") == 0)) {
		printf("upload url missing signed query\r\n");
		(void)f_close(&fp);
		return 0;
	}

	ok = Campus_HttpPutFileTransparent(&fp, headLen, fileSize);

	strEsp8266_Fram_Record.InfBit.FramLength = 0;
	strEsp8266_Fram_Record.InfBit.FramFinishFlag = 0;
	(void)f_close(&fp);
	LED0_OFF();
	return ok;
}

// 将本次上传结果通过MQTT回传给后端，后端据此标记告警附件状态。
static bool Campus_MQTT_PublishConfirm(const char *eventId, const char *objectKey, uint8_t success)
{
	char msgId[48];
	int written;

	Campus_BuildMsgId(msgId, sizeof(msgId));
	written = snprintf(g_PayloadBuf, sizeof(g_PayloadBuf),
		"{\"mqtt_msg_id\":\"%s\",\"product_type\":\"%s\",\"device_id\":\"%s\",\"event_id\":\"%s\",\"object_key\":\"%s\",\"upload_status\":\"%s\"}",
		msgId,
		CAMPUS_PRODUCT_TYPE,
		CAMPUS_DEVICE_ID,
		eventId,
		objectKey,
		(success != 0U) ? "success" : "failed");
	if ((written <= 0) || ((uint32_t)written >= sizeof(g_PayloadBuf))) {
		printf("confirm payload too long\r\n");
		return false;
	}

	return Campus_MQTT_PublishJson(CAMPUS_TOPIC_ALARM_CONFIRM, g_PayloadBuf);
}

// 处理后端下发的上传指令：解析payload、去重、上传WAV、发送确认回执。
void Campus_MQTT_ProcessSubRecv(void)
{
	char *json;
	char mqttMsgId[48];
	char eventId[64];
	char objectKey[160];
	CAMPUS_DEDUP_ENTRY *entry;
	uint8_t uploadOk;

	if (g_MqttSubRecvPending == 0U) {
		return;
	}

	if (strstr(g_MqttSubRecvBuf, CAMPUS_TOPIC_ALARM_UPLOAD) == 0) {
		goto EXIT_PROCESS;
	}

	if (!Campus_ExtractMqttSubPayload(g_MqttSubRecvBuf, CAMPUS_TOPIC_ALARM_UPLOAD,
			g_SubPayload, sizeof(g_SubPayload))) {
		printf("alarm/upload payload extract fail\r\n");
		goto EXIT_PROCESS;
	}

	json = strchr(g_SubPayload, '{');
	if (json == 0) {
		printf("alarm/upload json start fail\r\n");
		goto EXIT_PROCESS;
	}

	if (!Campus_JsonGetString(json, "mqtt_msg_id", mqttMsgId, sizeof(mqttMsgId)) ||
		!Campus_JsonGetString(json, "event_id", eventId, sizeof(eventId)) ||
		!Campus_JsonGetString(json, "object_key", objectKey, sizeof(objectKey)) ||
		!Campus_JsonGetString(json, "upload_url", g_UploadUrl, sizeof(g_UploadUrl))) {
		printf("alarm/upload parse fail\r\n");
		goto EXIT_PROCESS;
	}

	entry = Campus_DedupFind(mqttMsgId, eventId);
	if ((entry != 0) && (entry->finished != 0U)) {
		printf("alarm/upload duplicate, confirm again\r\n");
		(void)Campus_MQTT_PublishConfirm(entry->event_id, entry->object_key, entry->success);
		goto EXIT_PROCESS;
	}

	entry = Campus_DedupRemember(mqttMsgId, eventId, objectKey);
	if ((g_AlarmCtx.active == 0U) || (g_AlarmCtx.wav_path[0] == '\0')) {
		printf("alarm/upload no local file\r\n");
		entry->finished = 1U;
		entry->success = 0U;
		(void)Campus_MQTT_PublishConfirm(eventId, objectKey, 0U);
		LED0_OFF();
		goto EXIT_PROCESS;
	}

	uploadOk = Campus_HttpPutFile(g_UploadUrl, g_AlarmCtx.wav_path, objectKey);
	entry->finished = 1U;
	entry->success = uploadOk ? 1U : 0U;
	(void)Campus_MQTT_PublishConfirm(eventId, objectKey, entry->success);
	g_AlarmCtx.active = 0U;

EXIT_PROCESS:
	g_MqttSubRecvBuf[0] = '\0';
	g_MqttSubRecvPending = 0U;
}

// 主循环周期调用：在MQTT空闲时按间隔发布在线心跳，失败后缩短重试周期。
void Campus_MQTT_Service(uint32_t nowMs)
{
	static uint32_t s_nextOnlineMs = 0;
	uint32_t interval;

	if (g_MqttConnected == 0U || g_ESP8266RawBusy != 0U) {
		return;
	}

	if (s_nextOnlineMs == 0U) {
		s_nextOnlineMs = nowMs + CAMPUS_ONLINE_INTERVAL_MS;
		return;
	}

	if ((int32_t)(nowMs - s_nextOnlineMs) < 0) {
		return;
	}

	interval = Campus_MQTT_PublishOnline() ? CAMPUS_ONLINE_INTERVAL_MS : CAMPUS_ONLINE_RETRY_MS;
	s_nextOnlineMs = nowMs + interval;
}
