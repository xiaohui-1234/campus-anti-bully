#ifndef BSP_GPIO_H
#define BSP_GPIO_H

#include "stm32f10x.h"

/* 根据 GPIO 端口自动开启对应时钟 */
void BSP_GPIO_EnableClock(GPIO_TypeDef *GPIOx);

/* 通用 GPIO 初始化，可指定端口、引脚、模式和速度 */
void BSP_GPIO_InitPin(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin, GPIOMode_TypeDef GPIO_Mode, GPIOSpeed_TypeDef GPIO_Speed);

/* 初始化为推挽输出，并设置默认输出电平 */
void BSP_GPIO_InitOutput(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin, BitAction DefaultLevel);

/* 初始化为输入模式，GPIO_Mode 可传 GPIO_Mode_IPU、GPIO_Mode_IPD、GPIO_Mode_IN_FLOATING 等 */
void BSP_GPIO_InitInput(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin, GPIOMode_TypeDef GPIO_Mode);

/* 设置指定引脚为高电平 */
void BSP_GPIO_Set(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin);

/* 设置指定引脚为低电平 */
void BSP_GPIO_Reset(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin);

/* 写入指定电平 */
void BSP_GPIO_Write(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin, BitAction Level);

/* 翻转指定引脚当前输出电平 */
void BSP_GPIO_Toggle(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin);

/* 读取指定引脚输入电平 */
uint8_t BSP_GPIO_ReadInput(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin);

/* 读取指定引脚输出锁存电平 */
uint8_t BSP_GPIO_ReadOutput(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin);

#endif
