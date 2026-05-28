

/*****************辰哥单片机设计*****************
											STM32
 * 项目			:	ESP8266模块通信实验                     
 * 版本			: V1.0
 * 日期			: 2024.9.30
 * MCU			:	STM32F103C8T6
 * 接口			:	串口2					
 * BILIBILI	:	辰哥单片机设计 * CSDN			:	辰哥单片机设计 * 作者		:	辰哥 

**********************BEGIN***********************/

#include "esp8266.h"
#include "common.h"
#include <stdio.h>  
#include <string.h>  
#include <stdbool.h>
#include "System/Delay.h"
#include "Hardware/campus_mqtt.h"
#include "Time.h"

void Get_STM32_UID(char *uid_str);

volatile uint8_t ucTcpClosedFlag = 0;
volatile uint8_t g_ESP8266RawBusy = 0;
volatile uint8_t g_MqttConnected = 0;
volatile uint8_t g_MqttSubRecvPending = 0;
volatile uint8_t g_MqttTimRecvPending = 0;
char g_MqttSubRecvBuf[RX_BUF_MAX_LEN] = { 0 };
char g_MqttTimeRecvBuf[RX_BUF_MAX_LEN] = { 0 };
static char g_MqttTimeStrBuf[32] = { 0 };

char cStr [ 1500 ] = { 0 };

//static void                   ESP8266_GPIO_Config                 ( void );
static void                   ESP8266_USART_Config                ( void );
static void                   ESP8266_USART_NVIC_Configuration    ( void );
static void                   ESP8266_SendRawBytes                ( const uint8_t * pBuf, u32 ulLength );
static uint8_t                ESP8266_WaitRxQuiet                 ( u32 quiettime, u32 waittime );
static bool                   ESP8266_MQTTConnectReady            ( u32 waittime );
struct  STRUCT_USARTx_Fram strEsp8266_Fram_Record = { 0 };

/**
  * @brief  ESP8266英文月份转数字月份  * @param  month_str 英文月份字符串  * @retval 数字月份
  */
int Month_Str_To_Num(char *month_str)
{
if(strcmp(month_str,"Jan")==0)return 1;
else if(strcmp(month_str,"Feb")==0)return 2;
else if(strcmp(month_str,"Mar")==0)return 3;
else if(strcmp(month_str,"Apr")==0)return 4;
else if(strcmp(month_str,"May")==0)return 5;
else if(strcmp(month_str,"Jun")==0)return 6;
else if(strcmp(month_str,"Jul")==0)return 7;
else if(strcmp(month_str,"Aug")==0)return 8;
else if(strcmp(month_str,"Sep")==0)return 9;
else if(strcmp(month_str,"Oct")==0)return 10;
else if(strcmp(month_str,"Nov")==0)return 11;
else if(strcmp(month_str,"Dec")==0)return 12;
return 0;

}

/**
  * @brief   获取实时时间字符串，格式：YYYY-MM-DD HH:MM:SS
  * @param  time_str 时间字符串指针  * @retval 无  */
void ESP8266_Get_RealTime(char *time_str)
{
	char *p;
	char month_str[4];
	int year, month, day, hour, minute, second;

	if (time_str != 0) {
		time_str[0] = '\0';
	}

	memset(strEsp8266_Fram_Record.Data_RX_BUF, 0, sizeof(strEsp8266_Fram_Record.Data_RX_BUF));
	strEsp8266_Fram_Record.InfBit.FramLength = 0;
	strEsp8266_Fram_Record.InfBit.FramFinishFlag = 0;

	if (!ESP8266_Cmd(NTP_GET_TIME_CMD, "+CIPSNTPTIME:", "OK", 3000)) {
		return;
	}
	(void)ESP8266_WaitRxQuiet(50, 500);

	p = strstr((char *)strEsp8266_Fram_Record.Data_RX_BUF, "+CIPSNTPTIME:");
	if (p == 0) {
		return;
	}

	p += strlen("+CIPSNTPTIME:");
	if (sscanf(p, "%*s %3s %d %d:%d:%d %d", month_str, &day, &hour, &minute, &second, &year) != 6) {
		return;
	}

	month = Month_Str_To_Num(month_str);
	if (month <= 0 || year < 2024) {
		return;
	}

	sprintf(time_str, "%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, minute, second);
	strncpy(g_MqttTimeRecvBuf, time_str, RX_BUF_MAX_LEN - 1);
	g_MqttTimeRecvBuf[RX_BUF_MAX_LEN - 1] = '\0';
	(void)Time_UpdateFromString(time_str);
	g_MqttTimRecvPending = 1;
}
/**
  * @brief   发送设备标识符
  * @retval 无  */

bool ESP8266_SyncTime(void)
{
	static uint8_t s_ntp_configured = 0;
	static uint8_t s_fail_printed = 0;
	static uint8_t s_wait_printed = 0;
	char time_str[32];

	if (s_ntp_configured == 0U)
	{
		if ( ! ESP8266_Cmd ( NTP_CONFIG_CMD, "OK", NULL, 3000 ) )
		{
			printf ( "NTP cfg fail\r\n" );
			return false;
		}
		s_ntp_configured = 1U;
		Delay_ms ( 2500 );
	}

	time_str[0] = '\0';
	ESP8266_Get_RealTime ( time_str );
	if ( Time_IsValid() && time_str[0] != '\0' )
	{
		s_fail_printed = 0U;
		s_wait_printed = 0U;
		printf ( "NTP sync ok: %s\r\n", time_str );
		return true;
	}

	if (s_fail_printed == 0U) {
		printf ( "NTP not ready, timestamp fallback active\r\n" );
		s_fail_printed = 1U;
	}
	if (s_wait_printed == 0U) {
		printf ( "NTP wait valid time, retry later\r\n" );
		s_wait_printed = 1U;
	}
	return false;
}
void ESP8266_Send_DivceID(void)
{
char device_id[32]={0};
 Get_STM32_UID(device_id);
 printf("STM32 UID: %s\r\n", device_id);
}

/**
 * @brief  发送带实时时间的MQTT指令
 */
bool ESP8266_Send_Alarm_Time(void)
{    
	   //获取实时时间
	   ESP8266_Get_RealTime(g_MqttTimeStrBuf);
	   if (g_MqttTimeStrBuf[0] == '\0')
	   {
		   printf("Send time failed");
       return false;
	   }
  printf("Current Time:%s\r\n",g_MqttTimeStrBuf);
  return true;
}

/**
  * @brief  ESP8266 （Sta Tcp Client）透传
  * @param  无  * @retval 无  */
void ESP8266_StaTcpClient ( void )
{
  printf ( "\r\n Configuring TCP ESP8266 ......\r\n" );

  ESP8266_AT_Test ();
  ESP8266_Net_Mode_Choose ( STA );

  while ( ! ESP8266_JoinAP ( macUser_ESP8266_ApSsid, macUser_ESP8266_ApPwd ) )
  {
    Delay_ms ( 2000 );
  }

  ( void ) ESP8266_SyncTime ();

  printf ( "\r\n Configurating MQTT ESP8266 ......\r\n" );

  g_MqttConnected = 0;
  ( void ) ESP8266_Cmd ( "AT+MQTTCLEAN=0", "OK", "ERROR", 1000 );
  Delay_ms ( 1500 );

  {
    uint8_t mqttTry;

    for ( mqttTry = 0; mqttTry < 3U; mqttTry++ )
    {
      if ( ! ESP8266_Cmd ( MQTT_USER, "OK", "ERROR", 3000 ) )
        printf ( "MQTT user cfg timeout\r\n" );

      if ( ESP8266_MQTTConnectReady ( 12000 ) )
        break;

      printf ( "MQTT conn retry %u\r\n", ( unsigned int ) ( mqttTry + 1U ) );
      Delay_ms ( 1500 );
    }
  }

  if ( ! g_MqttConnected )
    printf ( "MQTT conn fail\r\n" );
  else
  {
    if ( ! ESP8266_Cmd ( MQTT_SUB_CMD, "OK", 0, 5000 ) )
      printf ( "MQTT sub alarm upload fail\r\n" );
    Delay_ms ( 300 );
    Campus_MQTT_OnConnected();
  }
}

/**
  * @brief  初始化ESP8266用到的GPIO引脚
  * @param  无  * @retval 无  */



/**
  * @brief  初始化ESP8266用到的USART
  * @param  无  * @retval 无  */
/**
  * @brief  ESP8266 init
  */
void ESP8266_Init ( void )
{
  ESP8266_USART_Config ();
}
static void ESP8266_USART_Config ( void )
{
	GPIO_InitTypeDef GPIO_InitStructure;
	USART_InitTypeDef USART_InitStructure;
	
	
	/* config USART clock */
	macESP8266_USART_APBxClock_FUN ( macESP8266_USART_CLK, ENABLE );
	macESP8266_USART_GPIO_APBxClock_FUN ( macESP8266_USART_GPIO_CLK, ENABLE );
	
	/* USART GPIO config */
	/* Configure USART Tx as alternate function push-pull */
	GPIO_InitStructure.GPIO_Pin =  macESP8266_USART_TX_PIN;
	GPIO_InitStructure.GPIO_Mode = GPIO_Mode_AF_PP;
	GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;
	GPIO_Init(macESP8266_USART_TX_PORT, &GPIO_InitStructure);  
  
	/* Configure USART Rx as input floating */
	GPIO_InitStructure.GPIO_Pin = macESP8266_USART_RX_PIN;
	GPIO_InitStructure.GPIO_Mode = GPIO_Mode_IN_FLOATING;
	GPIO_Init(macESP8266_USART_RX_PORT, &GPIO_InitStructure);
	
	/* USART1 mode config */
	USART_InitStructure.USART_BaudRate = macESP8266_USART_BAUD_RATE;
	USART_InitStructure.USART_WordLength = USART_WordLength_8b;
	USART_InitStructure.USART_StopBits = USART_StopBits_1;
	USART_InitStructure.USART_Parity = USART_Parity_No ;
	USART_InitStructure.USART_HardwareFlowControl = USART_HardwareFlowControl_None;
	USART_InitStructure.USART_Mode = USART_Mode_Rx | USART_Mode_Tx;
	USART_Init(macESP8266_USARTx, &USART_InitStructure);
	
	
	/* 中断配置 */
	USART_ITConfig ( macESP8266_USARTx, USART_IT_RXNE, ENABLE ); //使能串口接收中断 
	USART_ITConfig ( macESP8266_USARTx, USART_IT_IDLE, ENABLE ); //使能串口总线空闲中断 	

	ESP8266_USART_NVIC_Configuration ();
	USART_Cmd(macESP8266_USARTx, ENABLE);
	
}


/**
  * @brief  配置 ESP8266 USART 的NVIC 中断
  * @param  无  * @retval 无  */
static void ESP8266_USART_NVIC_Configuration ( void )
{
	NVIC_InitTypeDef NVIC_InitStructure; 
	
	
	/* Configure the NVIC Preemption Priority Bits */  
	NVIC_PriorityGroupConfig ( macNVIC_PriorityGroup_x );

	/* Enable the USART2 Interrupt */
	NVIC_InitStructure.NVIC_IRQChannel = macESP8266_USART_IRQ;	 
	NVIC_InitStructure.NVIC_IRQChannelPreemptionPriority = 0;
	NVIC_InitStructure.NVIC_IRQChannelSubPriority = 0;
	NVIC_InitStructure.NVIC_IRQChannelCmd = ENABLE;
	NVIC_Init(&NVIC_InitStructure);

}


/*
 * 函数名：ESP8266_Rst
 * 描述  ：重启WF-ESP8266模块
 * 输入  ：无
 * 返回  : 无 * 调用  ：被 ESP8266_AT_Test 调用
 */
void ESP8266_Rst ( void )
{
	#if 0
	 ESP8266_Cmd ( "AT+RST", "OK", "ready", 2500 );   	
	
	#else
	 macESP8266_RST_LOW_LEVEL();
	 Delay_ms ( 500 ); 
	 macESP8266_RST_HIGH_LEVEL();
	#endif

}


/*
 * 函数名：ESP8266_Cmd
 * 描述  ：对WF-ESP8266模块发送AT指令
 * 输入  ：cmd，待发送的指令
 *         reply1，reply2，期待的响应，为NULL表不需响应，两者为或逻辑关系
 *         waittime，等待响应的时间
 * 返回  : 1，指令发送成功 *         0，指令发送失败 * 调用  ：被外部调用
 */
bool ESP8266_Cmd ( char * cmd, char * reply1, char * reply2, u32 waittime )
{
    strEsp8266_Fram_Record.InfBit.FramLength = 0;
    strEsp8266_Fram_Record.InfBit.FramFinishFlag = 0;
    //将接收到的数据通过uart2发送给esp8266
    macESP8266_Usart("%s\r\n", cmd);
  
    if ( ( reply1 == 0 ) && ( reply2 == 0 ) )
        return true;
  
    if (!ESP8266_WaitResponse(reply1, reply2, waittime))
        return false;

    strEsp8266_Fram_Record.Data_RX_BUF[strEsp8266_Fram_Record.InfBit.FramLength] = '\0';
    return true;
}


static uint8_t ESP8266_WaitRxQuiet ( u32 quiettime, u32 waittime )
{
	u32 elapsed = 0;
	u32 quiet = 0;
	u16 lastLength = strEsp8266_Fram_Record.InfBit.FramLength;

	while ( 1 )
	{
		u16 currentLength = strEsp8266_Fram_Record.InfBit.FramLength;

		strEsp8266_Fram_Record.Data_RX_BUF [ currentLength ] = '\0';

		if ( currentLength != lastLength )
		{
			lastLength = currentLength;
			quiet = 0;
		}
		else if ( quiet >= quiettime )
		{
			return 1;
		}
		else
		{
			quiet++;
		}

		if ( elapsed >= waittime )
			break;

		Delay_ms ( 1 );
		elapsed++;
	}

	return ( quiet >= quiettime ) ? 1 : 0;
}


static bool ESP8266_MQTTConnectReady ( u32 waittime )
{
	u32 elapsed = 0;

	if ( g_MqttConnected )
		return true;

	strEsp8266_Fram_Record.InfBit.FramLength = 0;
	strEsp8266_Fram_Record.InfBit.FramFinishFlag = 0;

	macESP8266_Usart ( "%s\r\n", MQTT_CONN_CMD );

	while ( elapsed < waittime )
	{
		strEsp8266_Fram_Record.Data_RX_BUF [ strEsp8266_Fram_Record.InfBit.FramLength ] = '\0';
		if ( g_MqttConnected || strstr ( strEsp8266_Fram_Record.Data_RX_BUF, "+MQTTCONNECTED" ) )
		{
			g_MqttConnected = 1;
			break;
		}
		Delay_ms ( 1 );
		elapsed++;
	}

	if ( ! g_MqttConnected )
		return false;

	( void ) ESP8266_WaitRxQuiet ( 50, 200 );

	strEsp8266_Fram_Record.Data_RX_BUF [ strEsp8266_Fram_Record.InfBit.FramLength ] = '\0';
	return true;
}


uint8_t ESP8266_WaitResponse ( const char * reply1, const char * reply2, u32 waittime )
{
	u32 elapsed = 0;

	while ( 1 )
	{
		strEsp8266_Fram_Record.Data_RX_BUF[strEsp8266_Fram_Record.InfBit.FramLength] = '\0';

		if ( ( reply1 != 0 ) && strstr ( strEsp8266_Fram_Record.Data_RX_BUF, reply1 ) )
			return 1;

		if ( ( reply2 != 0 ) && strstr ( strEsp8266_Fram_Record.Data_RX_BUF, reply2 ) )
			return 1;

		if ( elapsed >= waittime )
			break;

		Delay_ms ( 1 );
		elapsed++;
	}

	strEsp8266_Fram_Record.Data_RX_BUF[strEsp8266_Fram_Record.InfBit.FramLength] = '\0';
	return 0;
}
/*
 * 函数名：ESP8266_AT_Test
 * 描述  ：对WF-ESP8266模块进行AT测试启动
 * 输入  ：无
 * 返回  : 无 * 调用  ：被外部调用
 */
bool ESP8266_AT_Ready(void)
{
	uint8_t retry;

	Delay_ms ( 3000 );
	for ( retry = 0; retry < 20U; retry++ )
	{
		if ( ESP8266_Cmd ( "AT", "OK", "ready", 1000 ) )
		{
			Delay_ms ( 300 );
			(void)ESP8266_Cmd ( "ATE0", "OK", 0, 1000 );
			return true;
		}
		Delay_ms ( 500 );
	}

	printf ( "ESP8266 not ready, check 3.3V power/EN/RST\r\n" );
	return false;
}

void ESP8266_AT_Test ( void )
{
	(void)ESP8266_AT_Ready();
}
/*
 * 函数名：ESP8266_Net_Mode_Choose
 * 描述  ：WF-ESP8266模块选择工作模式
 * 输入  ：enumMode，工作模式 * 返回  : 1，选择成功
 *         0，选择失败
 * 调用  ：被外部调用
 */
bool ESP8266_Net_Mode_Choose ( ENUM_Net_ModeTypeDef enumMode )
{
	switch ( enumMode )
	{
		case STA:
			return ESP8266_Cmd ( "AT+CWMODE=1", "OK", 0, 500 );
		
		case AP:
			return ESP8266_Cmd ( "AT+CWMODE=2", "OK", 0, 500 );
		
		case STA_AP:
			return ESP8266_Cmd ( "AT+CWMODE=3", "OK", 0, 500 );
		
		default:
			return false;
	}
}


/*
 * 函数名：ESP8266_JoinAP
 * 描述  ：WF-ESP8266模块连接外部WiFi热点
 * 输入  ：pSSID，WiFi名称字符串 *       ：pPassWord，WiFi密码字符串 * 返回  : 1，连接成功 *         0，连接失败 * 调用  ：被外部调用
 */
bool ESP8266_JoinAP ( char * pSSID, char * pPassWord )
{
	char cCmd [140];
	uint8_t retry;

	if ((pSSID == 0) || (pPassWord == 0)) {
		return false;
	}

	(void)ESP8266_Cmd ( "AT+CWQAP", "OK", "ERROR", 3000 );
	Delay_ms ( 500 );
	(void)ESP8266_Cmd ( "AT+CWAUTOCONN=0", "OK", 0, 1000 );
	Delay_ms ( 300 );
	(void)ESP8266_Cmd ( "AT+CWMODE=1", "OK", 0, 1000 );
	Delay_ms ( 300 );

	for (retry = 0; retry < 3U; retry++) {
		sprintf ( cCmd, "AT+CWJAP=\"%s\",\"%s\"", pSSID, pPassWord );
		strEsp8266_Fram_Record.InfBit.FramLength = 0;
		strEsp8266_Fram_Record.InfBit.FramFinishFlag = 0;
		macESP8266_Usart ( "%s\r\n", cCmd );

		if (ESP8266_WaitResponse ( "WIFI GOT IP", "+CWJAP:", 25000 )) {
			strEsp8266_Fram_Record.Data_RX_BUF[strEsp8266_Fram_Record.InfBit.FramLength] = '\0';
			if (strstr(strEsp8266_Fram_Record.Data_RX_BUF, "WIFI GOT IP") != 0) {
				(void)ESP8266_WaitRxQuiet ( 100, 1000 );
				printf ( "WiFi joined: %s\r\n", pSSID );
				return true;
			}
		}

		printf ( "WiFi join fail, retry=%u\r\n", (unsigned int)(retry + 1U) );
		printf ( "%s\r\n", strEsp8266_Fram_Record.Data_RX_BUF );
		Delay_ms ( 2000 );
	}

	printf ( "WiFi AP scan start, target=%s\r\n", pSSID );
	(void)ESP8266_Cmd ( "AT+CWLAP", "OK", 0, 15000 );
	printf ( "%s\r\n", strEsp8266_Fram_Record.Data_RX_BUF );
	printf ( "WiFi AP scan end\r\n" );
	return false;
}
/*
 * 函数名：ESP8266_Link_Server
 * 描述  ：WF-ESP8266模块连接外部服务器 * 输入  ：enumE，网络协议 *       ：ip，服务器IP字符串 *       ：ComNum，服务器端口字符串 *       ：id，模块连接服务器的ID
 * 返回  : 1，连接成功 *         0，连接失败 * 调用  ：被外部调用
 */
bool ESP8266_Link_Server ( ENUM_NetPro_TypeDef enumE, char * ip, char * ComNum, ENUM_ID_NO_TypeDef id )
{
	char cStr [100] = { 0 }, cCmd [120];

  switch (  enumE )
  {
		case enumTCP:
		  sprintf ( cStr, "\"%s\",\"%s\",%s", "TCP", ip, ComNum );
		  break;
		
		case enumUDP:
		  sprintf ( cStr, "\"%s\",\"%s\",%s", "UDP", ip, ComNum );
		  break;
		
		default:
			break;
  }

  if ( id < 5 )
    sprintf ( cCmd, "AT+CIPSTART=%d,%s", id, cStr);

  else
	  sprintf ( cCmd, "AT+CIPSTART=%s", cStr );

	return ESP8266_Cmd ( cCmd, "OK", "ALREAY CONNECT", 4000 );
}


static void ESP8266_SendRawBytes ( const uint8_t * pBuf, u32 ulLength )
{
	u32 ulIndex;

	for ( ulIndex = 0; ulIndex < ulLength; ulIndex ++ )
	{
 		USART_SendData ( macESP8266_USARTx, pBuf [ ulIndex ] );
 		while ( USART_GetFlagStatus ( macESP8266_USARTx, USART_FLAG_TXE ) == RESET );
 	}
 }
 
 
/*
 * 函数名：ESP8266_SendRawBuffer
 * 描述  ：发送原始数据缓冲区
 * 输入  ：pBuf，待发送的数据缓冲区 *         ulLength，待发送的数据长度
 * 返回  : 无 * 调用  ：被外部调用
 */
void ESP8266_SendRawBuffer ( const uint8_t * pBuf, u32 ulLength )
{
	if ( ( pBuf == 0 ) || ( ulLength == 0 ) )
		return;

	ESP8266_SendRawBytes ( pBuf, ulLength );
 }
 
 
/*
 * 函数名：ESP8266_SendBuffer
 * 描述  ：发送数据缓冲区
 * 输入  ：pBuf，待发送的数据缓冲区 *         ulLength，待发送的数据长度
 *         ucId，模块连接服务器的ID
 *         waittime，等待响应的时间
 * 返回  : 1，发送成功 *         0，发送失败 * 调用  ：被外部调用
 */
bool ESP8266_SendBuffer ( const uint8_t * pBuf, u32 ulLength, ENUM_ID_NO_TypeDef ucId, u32 waittime )
{
	char cCmd [ 32 ];
	uint8_t retry;
 
	if ( ( pBuf == 0 ) || ( ulLength == 0 ) )
		return false;

	if ( ucId < 5 )
		sprintf ( cCmd, "AT+CIPSEND=%d,%lu", ucId, ulLength );
	else
		sprintf ( cCmd, "AT+CIPSEND=%lu", ulLength );

	for ( retry = 0; retry < 3U; retry++ )
	{
		(void)ESP8266_WaitRxQuiet ( 80, 1000 );
		strEsp8266_Fram_Record .InfBit .FramLength = 0;
		strEsp8266_Fram_Record .InfBit .FramFinishFlag = 0;
		macESP8266_Usart ( "%s\r\n", cCmd );

		if ( ! ESP8266_WaitResponse ( ">", "ERROR", waittime ) )
		{
			printf ( "ESP send wait prompt timeout, len=%lu\r\n", (unsigned long)ulLength );
			printf ( "%s\r\n", strEsp8266_Fram_Record .Data_RX_BUF );
			Delay_ms ( 300 );
			continue;
		}

		if ( strstr ( strEsp8266_Fram_Record .Data_RX_BUF, "ERROR" ) )
		{
			printf ( "ESP send prompt error, len=%lu, retry=%u\r\n", (unsigned long)ulLength, (unsigned int)(retry + 1U) );
			printf ( "%s\r\n", strEsp8266_Fram_Record .Data_RX_BUF );
			Delay_ms ( 500 );
			continue;
		}

		strEsp8266_Fram_Record .InfBit .FramLength = 0;
		strEsp8266_Fram_Record .InfBit .FramFinishFlag = 0;
		ESP8266_SendRawBytes ( pBuf, ulLength );

		if ( ! ESP8266_WaitResponse ( "SEND OK", "SEND FAIL", waittime ) )
		{
			if ( strstr ( strEsp8266_Fram_Record .Data_RX_BUF, "HTTP/1.1 4" ) ||
				strstr ( strEsp8266_Fram_Record .Data_RX_BUF, "HTTP/1.1 5" ) )
			{
				printf ( "ESP send got http error, len=%lu\r\n", (unsigned long)ulLength );
				printf ( "%s\r\n", strEsp8266_Fram_Record .Data_RX_BUF );
				return false;
			}
			printf ( "ESP send wait sendok timeout, len=%lu\r\n", (unsigned long)ulLength );
			printf ( "%s\r\n", strEsp8266_Fram_Record .Data_RX_BUF );
			return false;
		}

		if ( strstr ( strEsp8266_Fram_Record .Data_RX_BUF, "SEND FAIL" ) )
		{
			printf ( "ESP send fail, len=%lu\r\n", (unsigned long)ulLength );
			return false;
		}

		(void)ESP8266_WaitRxQuiet ( 80, 1000 );
		return true;
	}

	return false;
 }
 
 
/*
 * 
 */bool ESP8266_UnvarnishSend ( void )
{
	return ESP8266_Cmd ( "AT+CIPMODE=1", "OK", 0, 1000 );
}
 
 
/*
 * 函数名：ESP8266_ExitUnvarnishSend
 * 描述  ：配置WF-ESP8266模块退出透传模式
 * 输入  ：无
 * 返回  : 无 * 调用  ：被外部调用
 */
void ESP8266_ExitUnvarnishSend ( void )
{
	Delay_ms( 1000 );
	
	macESP8266_Usart ( "+++" );
	
	Delay_ms( 500 ); 
	
}


/*
 * 函数名：ESP8266_SendString
 * 描述  ：WF-ESP8266模块发送字符串
 * 输入  ：enumEnUnvarnishTx，声明是否已使能了透传模式
 *       ：pStr，要发送的字符串 *       ：ulStrLength，要发送的字符串的字节数 *       ：ucId，哪个ID发送的字符串 * 返回  : 1，发送成功 *         0，发送失败 * 调用  ：被外部调用
 */
bool ESP8266_SendString ( FunctionalState enumEnUnvarnishTx, char * pStr, u32 ulStrLength, ENUM_ID_NO_TypeDef ucId )
{
	char cStr [20];
	bool bRet = false;
	
		
	if ( enumEnUnvarnishTx )
	{
		macESP8266_Usart ( "%s", pStr );
		
		bRet = true;
		
	}

	else
	{
		if ( ucId < 5 )
			sprintf ( cStr, "AT+CIPSEND=%d,%d", ucId, ulStrLength + 2 );

		else
			sprintf ( cStr, "AT+CIPSEND=%d", ulStrLength + 2 );
		
		ESP8266_Cmd ( cStr, "> ", 0, 1000 );

		bRet = ESP8266_Cmd ( pStr, "SEND OK", 0, 1000 );
  }
	
	return bRet;

}


/*
 * 函数名：ESP8266_ReceiveString
 * 描述  ：WF-ESP8266模块接收字符串 * 输入  ：enumEnUnvarnishTx，声明是否已使能了透传模式
 * 返回  : 接收到的字符串首地址
 * 调用  ：被外部调用
 */
char * ESP8266_ReceiveString ( FunctionalState enumEnUnvarnishTx )
{
	char * pRecStr = 0;
	
	
	strEsp8266_Fram_Record .InfBit .FramLength = 0;
	strEsp8266_Fram_Record .InfBit .FramFinishFlag = 0;
	
	while ( ! strEsp8266_Fram_Record .InfBit .FramFinishFlag );
	strEsp8266_Fram_Record .Data_RX_BUF [ strEsp8266_Fram_Record .InfBit .FramLength ] = '\0';
	
	if ( enumEnUnvarnishTx )
		pRecStr = strEsp8266_Fram_Record .Data_RX_BUF;
	
	else 
	{
		if ( strstr ( strEsp8266_Fram_Record .Data_RX_BUF, "+IPD" ) )
			pRecStr = strEsp8266_Fram_Record .Data_RX_BUF;

	}

	return pRecStr;
	
}

/*
 * 函数名：ProcessMQTTSubRecv
 * 描述  ：处理MQTT订阅消息，特别是property/set消息
 * 输入  ：无
 * 返回  : 无 * 调用  ：被串口中断处理函数调用
 */
void ProcessMQTTSubRecv(void)
{
	Campus_MQTT_ProcessSubRecv();
}
