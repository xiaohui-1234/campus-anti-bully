#include "bsp_gpio.h"

/* 根据传入端口开启 APB2 上对应的 GPIO 时钟 */
void BSP_GPIO_EnableClock(GPIO_TypeDef *GPIOx)
{
    if (GPIOx == GPIOA)
    {
        RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOA, ENABLE);
    }
    else if (GPIOx == GPIOB)
    {
        RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOB, ENABLE);
    }
    else if (GPIOx == GPIOC)
    {
        RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOC, ENABLE);
    }
    else if (GPIOx == GPIOD)
    {
        RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOD, ENABLE);
    }
    else if (GPIOx == GPIOE)
    {
        RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOE, ENABLE);
    }
#ifdef GPIOF
    else if (GPIOx == GPIOF)
    {
        RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOF, ENABLE);
    }
#endif
#ifdef GPIOG
    else if (GPIOx == GPIOG)
    {
        RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOG, ENABLE);
    }
#endif
}

/* GPIO 基础初始化入口，调用前无需单独开启 GPIO 时钟 */
void BSP_GPIO_InitPin(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin, GPIOMode_TypeDef GPIO_Mode, GPIOSpeed_TypeDef GPIO_Speed)
{
    GPIO_InitTypeDef GPIO_InitStructure;

    BSP_GPIO_EnableClock(GPIOx);

    GPIO_InitStructure.GPIO_Pin = GPIO_Pin;
    GPIO_InitStructure.GPIO_Mode = GPIO_Mode;
    GPIO_InitStructure.GPIO_Speed = GPIO_Speed;
    GPIO_Init(GPIOx, &GPIO_InitStructure);
}

/* 常用推挽输出初始化，并立即写入默认电平 */
void BSP_GPIO_InitOutput(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin, BitAction DefaultLevel)
{
    BSP_GPIO_InitPin(GPIOx, GPIO_Pin, GPIO_Mode_Out_PP, GPIO_Speed_50MHz);
    GPIO_WriteBit(GPIOx, GPIO_Pin, DefaultLevel);
}

/* 常用输入初始化，输入模式由调用方指定 */
void BSP_GPIO_InitInput(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin, GPIOMode_TypeDef GPIO_Mode)
{
    BSP_GPIO_InitPin(GPIOx, GPIO_Pin, GPIO_Mode, GPIO_Speed_50MHz);
}

/* 输出高电平 */
void BSP_GPIO_Set(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin)
{
    GPIO_SetBits(GPIOx, GPIO_Pin);
}

/* 输出低电平 */
void BSP_GPIO_Reset(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin)
{
    GPIO_ResetBits(GPIOx, GPIO_Pin);
}

/* 按传入 Level 写电平 */
void BSP_GPIO_Write(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin, BitAction Level)
{
    GPIO_WriteBit(GPIOx, GPIO_Pin, Level);
}

/* 根据当前输出锁存状态翻转电平 */
void BSP_GPIO_Toggle(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin)
{
    if (GPIO_ReadOutputDataBit(GPIOx, GPIO_Pin) == Bit_RESET)
    {
        GPIO_SetBits(GPIOx, GPIO_Pin);
    }
    else
    {
        GPIO_ResetBits(GPIOx, GPIO_Pin);
    }
}

/* 读取外部输入电平 */
uint8_t BSP_GPIO_ReadInput(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin)
{
    return (uint8_t)GPIO_ReadInputDataBit(GPIOx, GPIO_Pin);
}

/* 读取输出锁存电平 */
uint8_t BSP_GPIO_ReadOutput(GPIO_TypeDef *GPIOx, uint16_t GPIO_Pin)
{
    return (uint8_t)GPIO_ReadOutputDataBit(GPIOx, GPIO_Pin);
}
