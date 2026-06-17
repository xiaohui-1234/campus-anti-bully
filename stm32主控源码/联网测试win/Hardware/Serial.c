#include "stm32f10x.h"                  // Device header
#include "Library/stm32f10x_usart.h"
#include "Library/misc.h"
#include <stdio.h>
#include <stdarg.h>

#define VOICE_CMD_MAX_LEN       20U
#define VOICE_CMD_QUEUE_SIZE    8U

static volatile char g_VoiceRxLine[VOICE_CMD_MAX_LEN];
static volatile uint8_t g_VoiceRxIndex = 0;
static volatile char g_VoiceCmdQueue[VOICE_CMD_QUEUE_SIZE][VOICE_CMD_MAX_LEN];
static volatile uint8_t g_VoiceCmdHead = 0;
static volatile uint8_t g_VoiceCmdTail = 0;
static volatile uint8_t g_VoiceCmdCount = 0;

static void Serial_NVICConfig(void);
static void Serial_QueueVoiceLine(void);
void Serial_ClearVoiceCommands(void);

static void Serial_NVICConfig(void)
{
	NVIC_InitTypeDef NVIC_InitStructure;

	NVIC_PriorityGroupConfig(NVIC_PriorityGroup_2);
	NVIC_InitStructure.NVIC_IRQChannel = USART3_IRQn;
	NVIC_InitStructure.NVIC_IRQChannelPreemptionPriority = 1;
	NVIC_InitStructure.NVIC_IRQChannelSubPriority = 2;
	NVIC_InitStructure.NVIC_IRQChannelCmd = ENABLE;
	NVIC_Init(&NVIC_InitStructure);
}

static void Serial_QueueVoiceLine(void)
{
	uint8_t i;

	if (g_VoiceRxIndex == 0U) {
		return;
	}
	if (g_VoiceCmdCount >= VOICE_CMD_QUEUE_SIZE) {
		return;
	}

	for (i = 0; i < g_VoiceRxIndex; i++) {
		g_VoiceCmdQueue[g_VoiceCmdTail][i] = g_VoiceRxLine[i];
	}
	g_VoiceCmdQueue[g_VoiceCmdTail][g_VoiceRxIndex] = '\0';
	g_VoiceCmdTail++;
	if (g_VoiceCmdTail >= VOICE_CMD_QUEUE_SIZE) {
		g_VoiceCmdTail = 0U;
	}
	g_VoiceCmdCount++;
}

/**
  * 函    数：语音串口初始化
  * 参    数：无
  * 返 回 值：无
  */
void Serial_Init(void)
{
	/*开启时钟*/
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOB, ENABLE);	//开启GPIOB的时钟
	RCC_APB1PeriphClockCmd(RCC_APB1Periph_USART3, ENABLE);	//开启USART3的时钟，注意USART3在APB1总线上
	
	/*GPIO初始化*/
	GPIO_InitTypeDef GPIO_InitStructure;
	
	// PB10 - USART3_TX 复用推挽输出
	GPIO_InitStructure.GPIO_Mode = GPIO_Mode_AF_PP;
	GPIO_InitStructure.GPIO_Pin = GPIO_Pin_10;
	GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;
	GPIO_Init(GPIOB, &GPIO_InitStructure);				//将PB10引脚初始化为复用推挽输出
	
	// PB11 - USART3_RX 上拉输入
	GPIO_InitStructure.GPIO_Mode = GPIO_Mode_IPU;
	GPIO_InitStructure.GPIO_Pin = GPIO_Pin_11;
	GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;
	GPIO_Init(GPIOB, &GPIO_InitStructure);				//将PB11引脚初始化为上拉输入
	
	/*USART初始化*/
	USART_InitTypeDef USART_InitStructure;					//定义结构体变量
	USART_InitStructure.USART_BaudRate = 9600;				//波特率
	USART_InitStructure.USART_HardwareFlowControl = USART_HardwareFlowControl_None;	//硬件流控制，不需要
	USART_InitStructure.USART_Mode = USART_Mode_Tx|USART_Mode_Rx;			//模式，选择为发送模式
	USART_InitStructure.USART_Parity = USART_Parity_No;		//奇偶校验，不需要
	USART_InitStructure.USART_StopBits = USART_StopBits_1;	//停止位，选择1位
	USART_InitStructure.USART_WordLength = USART_WordLength_8b;		//字长，选择8位
	USART_Init(USART3, &USART_InitStructure);			//将结构体变量交给USART_Init，配置USART3
	
	Serial_ClearVoiceCommands();
	Serial_NVICConfig();
	USART_ITConfig(USART3, USART_IT_RXNE, ENABLE);

	/*USART使能*/
	USART_Cmd(USART3, ENABLE);					//使能USART3，串口开始运行
	
	
}

void Serial_VoiceRxIRQHandler(void)
{
	uint8_t data;

	if (USART_GetITStatus(USART3, USART_IT_RXNE) != RESET) {
		data = (uint8_t)USART_ReceiveData(USART3);
		if ((data == '\r') || (data == '\n')) {
			Serial_QueueVoiceLine();
			g_VoiceRxIndex = 0U;
		} else {
			if (g_VoiceRxIndex < (VOICE_CMD_MAX_LEN - 1U)) {
				g_VoiceRxLine[g_VoiceRxIndex++] = (char)data;
			} else {
				g_VoiceRxIndex = 0U;
			}
		}
	}
}

uint8_t Serial_ReadVoiceCommand(char *buffer, uint8_t bufferSize)
{
	uint8_t i;
	uint8_t hasCommand = 0U;

	if ((buffer == 0) || (bufferSize == 0U)) {
		return 0U;
	}

	USART_ITConfig(USART3, USART_IT_RXNE, DISABLE);
	if (g_VoiceCmdCount > 0U) {
		for (i = 0; i < (uint8_t)(bufferSize - 1U); i++) {
			buffer[i] = (char)g_VoiceCmdQueue[g_VoiceCmdHead][i];
			if (buffer[i] == '\0') {
				break;
			}
		}
		buffer[bufferSize - 1U] = '\0';
		g_VoiceCmdHead++;
		if (g_VoiceCmdHead >= VOICE_CMD_QUEUE_SIZE) {
			g_VoiceCmdHead = 0U;
		}
		g_VoiceCmdCount--;
		hasCommand = 1U;
	}
	USART_ITConfig(USART3, USART_IT_RXNE, ENABLE);

	return hasCommand;
}

void Serial_ClearVoiceCommands(void)
{
	USART_ITConfig(USART3, USART_IT_RXNE, DISABLE);
	g_VoiceRxIndex = 0U;
	g_VoiceCmdHead = 0U;
	g_VoiceCmdTail = 0U;
	g_VoiceCmdCount = 0U;
	USART_ITConfig(USART3, USART_IT_RXNE, ENABLE);
}

/**
  * 函    数：串口发送一个字节
  * 参    数：Byte 要发送的一个字节
  * 返 回 值：无
  */
void Serial_SendByte(uint8_t Byte)
{
	USART_SendData(USART3, Byte);		//将字节数据写入数据寄存器，写入后USART自动生成时序波形
	while (USART_GetFlagStatus(USART3, USART_FLAG_TXE) == RESET);	//等待发送完成
	/*下次写入数据寄存器会自动清除发送完成标志位，故此循环后，无需清除标志位*/
}

/**
  * 函    数：串口发送一个数组
  * 参    数：Array 要发送数组的首地址
  * 参    数：Length 要发送数组的长度
  * 返 回 值：无
  */
void Serial_SendArray(uint8_t *Array, uint16_t Length)
{
	uint16_t i;
	for (i = 0; i < Length; i ++)		//遍历数组
	{
		Serial_SendByte(Array[i]);		//依次调用Serial_SendByte发送每个字节数据
	}
}

/**
  * 函    数：串口发送一个字符串
  * 参    数：String 要发送字符串的首地址
  * 返 回 值：无
  */
void Serial_SendString(char *String)
{
	uint8_t i;
	for (i = 0; String[i] != '\0'; i ++)//遍历字符数组（字符串），遇到字符串结束标志位后停止
	{
		Serial_SendByte(String[i]);		//依次调用Serial_SendByte发送每个字节数据
	}
}

/**
  * 函    数：次方函数（内部使用）
  * 返 回 值：返回值等于X的Y次方
  */
uint32_t Serial_Pow(uint32_t X, uint32_t Y)
{
	uint32_t Result = 1;	//设置结果初值为1
	while (Y --)			//执行Y次
	{
		Result *= X;		//将X累乘到结果
	}
	return Result;
}

/**
  * 函    数：串口发送数字
  * 参    数：Number 要发送的数字，范围：0~4294967295
  * 参    数：Length 要发送数字的长度，范围：0~10
  * 返 回 值：无
  */
void Serial_SendNumber(uint32_t Number, uint8_t Length)
{
	uint8_t i;
	for (i = 0; i < Length; i ++)		//根据数字长度遍历数字的每一位
	{
		Serial_SendByte(Number / Serial_Pow(10, Length - i - 1) % 10 + '0');	//依次调用Serial_SendByte发送每位数字
	}
}

/**
  * 函    数：使用printf需要重定向的底层函数
  * 参    数：保持原始格式即可，无需变动
  * 返 回 值：保持原始格式即可，无需变动
  */
int fputc(int ch, FILE *f)
{
	USART_SendData(USART1, (uint8_t)ch);
	while (USART_GetFlagStatus(USART1, USART_FLAG_TXE) == RESET);
	return ch;
}

/**
  * 函    数：发送UTF-8字符串
  * 参    数：str 要发送的UTF-8字符串
  * 返 回 值：无
  */
void Serial_SendUTF8String(char *str)
{
	while (*str)
	{
		USART_SendData(USART1, (uint8_t)*str++);
		while (USART_GetFlagStatus(USART1, USART_FLAG_TXE) == RESET);
	}
}

