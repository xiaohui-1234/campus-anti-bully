#include "stm32f10x.h"                  // Device header
#include "System/Delay.h"
#include "System/bsp_timer.h"
	//定义变量，默认键码值为0

#define KEY_BIND_DEBOUNCE_MS 20U

/**
  * 函    数：按键初始化
  * 参    数：无
  * 返 回 值：无
  */
void Key_Init(void)
{
	/*开启时钟*/
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOB | RCC_APB2Periph_AFIO, ENABLE);		//开启GPIOB和AFIO时钟
	GPIO_PinRemapConfig(GPIO_Remap_SWJ_JTAGDisable, ENABLE);	//释放PB3，保留SWD调试
	
	/*GPIO初始化*/
	GPIO_InitTypeDef GPIO_InitStructure;
	GPIO_InitStructure.GPIO_Mode = GPIO_Mode_IPU;
	GPIO_InitStructure.GPIO_Pin = GPIO_Pin_3 | GPIO_Pin_5 | GPIO_Pin_9;
	GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;
	GPIO_Init(GPIOB, &GPIO_InitStructure);						//将PB3/PB5/PB9初始化为上拉输入
}

/**
  * 函    数：按键获取键码
  * 参    数：无
  * 返 回 值：按下按键的键码值，范围：0~2，返回0代表没有按键按下
  * 注意事项：此函数是阻塞式操作，当按键按住不放时，函数会卡住，直到按键松手
  */
uint8_t Key_GetNum(void)
{
	 uint8_t KeyNum = 0;	
	if (GPIO_ReadInputDataBit(GPIOB, GPIO_Pin_5) == 0)			//读PB5输入寄存器的状态，如果为0，则代表按键1按下
	{//透传模式
		Delay_ms(20);										//延时消抖
		while (GPIO_ReadInputDataBit(GPIOB, GPIO_Pin_5) == 0);	//等待按键松手
		Delay_ms(20);										//延时消抖
		KeyNum = 1;										//置键码为1
	}
  
  	if (GPIO_ReadInputDataBit(GPIOB, GPIO_Pin_9) == 0)			//读PB9输入寄存器的状态，如果为0，则代表按键1按下
	{//报警按钮
		Delay_ms(20);										//延时消抖
		while (GPIO_ReadInputDataBit(GPIOB, GPIO_Pin_9) == 0);	//等待按键松手
		Delay_ms(20);										//延时消抖
		KeyNum = 2;										//置键码为1
	}
	return KeyNum;			//返回键码值，如果没有按键按下，所有if都不成立，则键码为默认值0
}

uint8_t Key_GetBindPressed(void)
{
	static uint8_t lastRaw = 1U;
	static uint8_t stableRaw = 1U;
	static uint32_t lastChangeMs = 0U;
	uint8_t raw;
	uint32_t nowMs;

	raw = (uint8_t)GPIO_ReadInputDataBit(GPIOB, GPIO_Pin_3);
	nowMs = App_Millis();
	if (raw != lastRaw) {
		lastRaw = raw;
		lastChangeMs = nowMs;
	}

	if (((int32_t)(nowMs - lastChangeMs) >= (int32_t)KEY_BIND_DEBOUNCE_MS) &&
		(raw != stableRaw)) {
		stableRaw = raw;
		if (stableRaw == 0U) {
			return 1U;
		}
	}
	return 0U;
}
