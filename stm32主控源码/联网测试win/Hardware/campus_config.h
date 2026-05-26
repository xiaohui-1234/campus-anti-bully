#ifndef __CAMPUS_CONFIG_H
#define __CAMPUS_CONFIG_H

/* Server and device configuration. Replace placeholders before flashing. */
#define CAMPUS_WIFI_SSID                 "xiaohui"
#define CAMPUS_WIFI_PASSWORD             "1122334455"

#define CAMPUS_EMQX_HOST                 "47.116.107.124"
#define CAMPUS_EMQX_PORT                 1883
#define CAMPUS_EMQX_PORT_STR             "1883"

#define CAMPUS_PRODUCT_TYPE              "anti_bullying"
#define CAMPUS_DEVICE_ID                 "dev001"
#define CAMPUS_DEVICE_SECRET             "dev-secret"

#define CAMPUS_MQTT_CLIENT_ID            CAMPUS_DEVICE_ID
#define CAMPUS_MQTT_USERNAME             "anti_bullying"
#define CAMPUS_MQTT_PASSWORD             "dev-secret"
#define CAMPUS_MQTT_QOS                  1

#define CAMPUS_ALARM_FILE_NAME           "alarm.wav"
#define CAMPUS_ALARM_CONTENT_TYPE        "audio/wav"

#define CAMPUS_HTTP_UPLOAD_CHUNK_SIZE    512U
#define CAMPUS_HTTP_HOST_MAX_LEN         96U
#define CAMPUS_HTTP_PATH_MAX_LEN         768U
#define CAMPUS_MQTT_PAYLOAD_MAX_LEN      512U
#define CAMPUS_MQTT_CMD_MAX_LEN          1024U
#define CAMPUS_DEDUP_CACHE_SIZE          6U

#define CAMPUS_ONLINE_INTERVAL_MS        60000UL
#define CAMPUS_ONLINE_RETRY_MS           10000UL
#define CAMPUS_NTP_SYNC_INTERVAL_MS      21600000UL
#define CAMPUS_NTP_RETRY_MS              5000UL
#define CAMPUS_NTP_TIMEZONE_HOURS        8

#define CAMPUS_TOPIC_ALARM_POST          "device/" CAMPUS_PRODUCT_TYPE "/" CAMPUS_DEVICE_ID "/alarm/post"
#define CAMPUS_TOPIC_ALARM_CONFIRM       "device/" CAMPUS_PRODUCT_TYPE "/" CAMPUS_DEVICE_ID "/alarm/confirm"
#define CAMPUS_TOPIC_STATUS_ONLINE       "device/" CAMPUS_PRODUCT_TYPE "/" CAMPUS_DEVICE_ID "/status/online"
#define CAMPUS_TOPIC_ALARM_UPLOAD        "device/" CAMPUS_PRODUCT_TYPE "/" CAMPUS_DEVICE_ID "/alarm/upload"

#define CAMPUS_MQTT_USER_CMD             "AT+MQTTUSERCFG=0,1,\"" CAMPUS_MQTT_CLIENT_ID "\",\"" CAMPUS_MQTT_USERNAME "\",\"" CAMPUS_MQTT_PASSWORD "\",0,0,\"\""
#define CAMPUS_MQTT_CONN_CMD             "AT+MQTTCONN=0,\"" CAMPUS_EMQX_HOST "\"," CAMPUS_EMQX_PORT_STR ",0"
#define CAMPUS_MQTT_SUB_UPLOAD_CMD       "AT+MQTTSUB=0,\"" CAMPUS_TOPIC_ALARM_UPLOAD "\"," "1"

#define CAMPUS_NTP_CONFIG_CMD            "AT+CIPSNTPCFG=1,8,\"ntp1.aliyun.com\",\"ntp.ntsc.ac.cn\",\"time.windows.com\""
#define CAMPUS_NTP_GET_TIME_CMD          "AT+CIPSNTPTIME?"

#endif
