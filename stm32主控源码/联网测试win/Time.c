#include "stm32f10x.h"
#include "Time.h"
#include "Hardware/esp8266.h"
#include "Hardware/campus_config.h"
#include "System/bsp_timer.h"
#include <stdio.h>
#include <string.h>

static uint8_t s_time_valid = 0;
static uint32_t s_epoch_seconds = 0;
static uint32_t s_epoch_base_ms = 0;

static uint8_t Time_IsLeapYear(uint16_t year)
{
  return (uint8_t)(((year % 4U) == 0U && (year % 100U) != 0U) || ((year % 400U) == 0U));
}

static uint16_t Time_DaysBeforeMonth(uint16_t year, uint8_t month)
{
  static const uint16_t days_before_month[12] = {
    0U, 31U, 59U, 90U, 120U, 151U, 181U, 212U, 243U, 273U, 304U, 334U
  };
  uint16_t days;

  if (month == 0U || month > 12U) {
    return 0U;
  }

  days = days_before_month[month - 1U];
  if (month > 2U && Time_IsLeapYear(year)) {
    days++;
  }
  return days;
}

static uint8_t Time_ToEpochSeconds(uint16_t year, uint8_t month, uint8_t day,
  uint8_t hour, uint8_t minute, uint8_t second, uint32_t *epoch)
{
  uint16_t y;
  uint32_t days = 0;
  int64_t local_seconds;

  if (epoch == 0 || year < 2024U || month < 1U || month > 12U || day < 1U || day > 31U ||
      hour > 23U || minute > 59U || second > 59U) {
    return 0;
  }

  for (y = 1970U; y < year; y++) {
    days += Time_IsLeapYear(y) ? 366UL : 365UL;
  }
  days += Time_DaysBeforeMonth(year, month);
  days += (uint32_t)(day - 1U);

  local_seconds = ((int64_t)days * 86400) + ((int64_t)hour * 3600) +
    ((int64_t)minute * 60) + second;
  local_seconds -= ((int64_t)CAMPUS_NTP_TIMEZONE_HOURS * 3600);
  if (local_seconds < 0) {
    return 0;
  }

  *epoch = (uint32_t)local_seconds;
  return 1;
}

static void Time_Uint64ToText(uint64_t value, char *out, uint32_t outSize)
{
  char tmp[24];
  uint8_t i = 0;
  uint8_t j = 0;

  if (out == 0 || outSize == 0U) {
    return;
  }

  if (value == 0U) {
    if (outSize > 1U) {
      out[0] = '0';
      out[1] = '\0';
    }
    return;
  }

  while (value != 0U && i < sizeof(tmp)) {
    tmp[i++] = (char)('0' + (value % 10U));
    value /= 10U;
  }

  while (i > 0U && (j + 1U) < outSize) {
    out[j++] = tmp[--i];
  }
  out[j] = '\0';
}

uint8_t Time_UpdateFromString(const char *timeStr)
{
  int year;
  int month;
  int day;
  int hour;
  int minute;
  int second;
  uint32_t epoch;

  if (timeStr == 0 || timeStr[0] == '\0') {
    return 0;
  }

  if (sscanf(timeStr, "%d-%d-%d %d:%d:%d", &year, &month, &day, &hour, &minute, &second) != 6) {
    return 0;
  }

  if (!Time_ToEpochSeconds((uint16_t)year, (uint8_t)month, (uint8_t)day,
      (uint8_t)hour, (uint8_t)minute, (uint8_t)second, &epoch)) {
    return 0;
  }

  s_epoch_seconds = epoch;
  s_epoch_base_ms = App_Millis();
  s_time_valid = 1U;
  return 1;
}

uint8_t Time_IsValid(void)
{
  return s_time_valid;
}

uint8_t Time_GetTimestampMsText(char *out, uint32_t outSize)
{
  uint32_t elapsed;
  uint64_t timestamp;

  if (out == 0 || outSize == 0U) {
    return 0;
  }

  if (!s_time_valid) {
    out[0] = '0';
    if (outSize > 1U) {
      out[1] = '\0';
    }
    return 0;
  }

  elapsed = App_Millis() - s_epoch_base_ms;
  timestamp = ((uint64_t)s_epoch_seconds * 1000U) + (uint64_t)elapsed;
  Time_Uint64ToText(timestamp, out, outSize);
  return 1;
}

uint8_t Time_GenerateRecordPath(char *path, uint32_t pathSize)
{
  static uint16_t record_index = 0;
  static char last_time_fmt[32] = "record";
  char time_fmt[32];
  uint8_t i;
  int written;

  if (path == 0 || pathSize == 0U) {
    return 0;
  }

  if (g_MqttTimRecvPending) {
    g_MqttTimRecvPending = 0;
    record_index = 0;
    if (g_MqttTimeRecvBuf[0] != '\0') {
      strncpy(last_time_fmt, g_MqttTimeRecvBuf, sizeof(last_time_fmt) - 1U);
      last_time_fmt[sizeof(last_time_fmt) - 1U] = '\0';
    }
  }

  strncpy(time_fmt, last_time_fmt, sizeof(time_fmt) - 1U);
  time_fmt[sizeof(time_fmt) - 1U] = '\0';
  for (i = 0; time_fmt[i] != '\0'; ++i) {
    if (time_fmt[i] == ' ' || time_fmt[i] == ':' || time_fmt[i] == '/' || time_fmt[i] == '\\') {
      time_fmt[i] = '_';
    }
  }

  written = snprintf(path, pathSize, "0:/%s_%u.wav", time_fmt, record_index);
  if (written < 0 || (uint32_t)written >= pathSize) {
    return 0;
  }

  record_index++;
  return 1;
}
