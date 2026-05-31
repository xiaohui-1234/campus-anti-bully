#include "stm32f10x.h"
#include "alarm.h"
#include "Hardware/campus_mqtt.h"
#include "Hardware/LED.h"

static void Alarm_StartRecord(const char *eventType, const char *alarmInfo)
{
	if (App_IsRecording()) {
		return;
	}

	if (Campus_MQTT_SetPendingAlarm(eventType, alarmInfo) && App_TryStartAlarmRecording()) {
		LED0_ON();
	}
}

void Alarm_ButtomDown(uint8_t key)
{
	if (key == 2U) {
		Alarm_StartRecord("BUTTON", "button_alarm");
	}
}

void Alarm_Voice(void)
{
	Alarm_StartRecord("VOICE", "voice_help");
}

void Alarm_Fire(void)
{
	Alarm_StartRecord("VOICE", "fire");
}

void Alarm_Kill(void)
{
	Alarm_StartRecord("VOICE", "kill");
}

void Alarm_Fight(void)
{
	Alarm_StartRecord("VOICE", "fight");
}

void Alarm_Kidnap(void)
{
	Alarm_StartRecord("VOICE", "kidnap");
}

void Alarm_Explosion(void)
{
	Alarm_StartRecord("VOICE", "explosion");
}

void Alarm_Blood(void)
{
	Alarm_StartRecord("VOICE", "blood");
}

void Alarm_Faint(void)
{
	Alarm_StartRecord("VOICE", "faint");
}

void Alarm_StopHit(void)
{
	Alarm_StartRecord("VOICE", "stop_hit");
}

void Alarm_Robbery(void)
{
	Alarm_StartRecord("VOICE", "robbery");
}

void Alarm_HelpMe(void)
{
	Alarm_StartRecord("VOICE", "help_me");
}

void Alarm_CallPeople(void)
{
	Alarm_StartRecord("VOICE", "call_people");
}

void Alarm_GroupFight(void)
{
	Alarm_StartRecord("VOICE", "group_fight");
}

void Alarm_DontMove(void)
{
	Alarm_StartRecord("VOICE", "dont_move");
}

void General_alarm(void)
{
	Alarm_StartRecord("VOICE", "general_alarm");
}
