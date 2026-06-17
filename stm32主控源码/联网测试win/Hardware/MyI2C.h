#ifndef __MYI2C_H
#define __MYI2C_H

#ifdef __cplusplus
extern "C" {
#endif

#include "stm32f10x.h"

/**
  ******************************************************************************
  * @file    MyI2C.h
  * @brief   STM32F103C8T6 硬件 I2C 驱动配置文件与函数声明。
  * @details
  *          本模块基于 STM32F10x 标准外设库编写，支持 I2C1、I2C2。
  *
  *          STM32F103C8T6 可用 I2C 引脚：
  *          - I2C1 默认映射：SCL=PB6，  SDA=PB7。
  *          - I2C1 重映射：  SCL=PB8，  SDA=PB9。
  *          - I2C2 固定映射：SCL=PB10， SDA=PB11。
  *
  *          使用说明：
  *          1. 通过 MYI2C1_ENABLE、MYI2C2_ENABLE 控制对应 I2C 是否参与编译。
  *          2. 通过 MYI2C1_REMAP 控制 I2C1 是否使用 PB8/PB9。
  *          3. 所有设备地址均使用 7 位地址，例如 OLED 常见地址为 0x3C。
  *          4. 发送函数内部会自动将 7 位地址左移 1 位，以适配标准外设库。
  *          5. 本模块使用阻塞式通信，带超时返回，适合 OLED、EEPROM、传感器等入门场景。
  ******************************************************************************
  */

/* ============================= 1. 总开关配置 ============================= */

/**
  * @brief  I2C1 编译开关。
  * @note   1：启用 I2C1 宏、配置和 MyI2C1_* 快捷函数。
  *         0：不编译 I2C1 相关内容。
  */
#ifndef MYI2C1_ENABLE
#define MYI2C1_ENABLE                      1
#endif

/**
  * @brief  I2C2 编译开关。
  * @note   1：启用 I2C2 宏、配置和 MyI2C2_* 快捷函数。
  *         0：不编译 I2C2 相关内容。
  */
#ifndef MYI2C2_ENABLE
#define MYI2C2_ENABLE                      0
#endif

/**
  * @brief  默认 I2C 通信速度。
  * @note   标准模式常用 100000，快速模式常用 400000。
  *         部分 OLED 模块走线较长或上拉电阻较大时，建议先用 100000。
  */
#ifndef MYI2C_DEFAULT_SPEED
#define MYI2C_DEFAULT_SPEED                100000
#endif

/**
  * @brief  I2C 等待超时时间。
  * @note   该值是软件循环计数，不是精确毫秒。通信异常时函数会返回超时，避免死等。
  */
#ifndef MYI2C_TIMEOUT_COUNT
#define MYI2C_TIMEOUT_COUNT                100000
#endif

/**
  * @brief  本机地址。
  * @note   当前模块主要做主机通信，该地址通常无需关心，只要不为 0 即可。
  */
#ifndef MYI2C_OWN_ADDRESS
#define MYI2C_OWN_ADDRESS                  0x30
#endif

#define MYI2C_ANY_ENABLE                   (MYI2C1_ENABLE || MYI2C2_ENABLE)

/**
  * @brief  7 位地址转标准外设库使用的地址格式。
  * @note   标准外设库 I2C_Send7bitAddress() 需要传入左移后的地址。
  */
#define MYI2C_7BIT_ADDR_TO_8BIT(addr)      ((uint8_t)((addr) << 1))

/* ============================= 2. I2C1 宏定义 ============================= */
#if MYI2C1_ENABLE

/**
  * @brief  I2C1 引脚重映射选择。
  * @note   0：SCL=PB6，SDA=PB7。
  *         1：SCL=PB8，SDA=PB9，需要开启 AFIO 重映射。
  */
#ifndef MYI2C1_REMAP
#define MYI2C1_REMAP                       0
#endif

#if ((MYI2C1_REMAP != 0) && (MYI2C1_REMAP != 1))
#error "MYI2C1_REMAP must be 0 or 1."
#endif

/* I2C1 外设、时钟和中断宏。 */
#define MYI2C1_I2C                         I2C1
#define MYI2C1_I2C_CLK                     RCC_APB1Periph_I2C1
#define MYI2C1_I2C_CLK_CMD                 RCC_APB1PeriphClockCmd
#define MYI2C1_EV_IRQ_CHANNEL              I2C1_EV_IRQn
#define MYI2C1_ER_IRQ_CHANNEL              I2C1_ER_IRQn

#if MYI2C1_REMAP
/* I2C1 重映射引脚：PB8/PB9。 */
#define MYI2C1_GPIO_REMAP                  GPIO_Remap_I2C1
#define MYI2C1_SCL_GPIO_CLK                RCC_APB2Periph_GPIOB
#define MYI2C1_SCL_PORT                    GPIOB
#define MYI2C1_SCL_PIN                     GPIO_Pin_8
#define MYI2C1_SCL_PIN_SOURCE              GPIO_PinSource8
#define MYI2C1_SCL_PORT_SOURCE             GPIO_PortSourceGPIOB
#define MYI2C1_SDA_GPIO_CLK                RCC_APB2Periph_GPIOB
#define MYI2C1_SDA_PORT                    GPIOB
#define MYI2C1_SDA_PIN                     GPIO_Pin_9
#define MYI2C1_SDA_PIN_SOURCE              GPIO_PinSource9
#define MYI2C1_SDA_PORT_SOURCE             GPIO_PortSourceGPIOB
#else
/* I2C1 默认引脚：PB6/PB7。 */
#define MYI2C1_SCL_GPIO_CLK                RCC_APB2Periph_GPIOB
#define MYI2C1_SCL_PORT                    GPIOB
#define MYI2C1_SCL_PIN                     GPIO_Pin_6
#define MYI2C1_SCL_PIN_SOURCE              GPIO_PinSource6
#define MYI2C1_SCL_PORT_SOURCE             GPIO_PortSourceGPIOB
#define MYI2C1_SDA_GPIO_CLK                RCC_APB2Periph_GPIOB
#define MYI2C1_SDA_PORT                    GPIOB
#define MYI2C1_SDA_PIN                     GPIO_Pin_7
#define MYI2C1_SDA_PIN_SOURCE              GPIO_PinSource7
#define MYI2C1_SDA_PORT_SOURCE             GPIO_PortSourceGPIOB
#endif

#define MYI2C1_GPIO_CLK                    (MYI2C1_SCL_GPIO_CLK | MYI2C1_SDA_GPIO_CLK)

/* I2C1 通信参数。 */
#ifndef MYI2C1_SPEED
#define MYI2C1_SPEED                       MYI2C_DEFAULT_SPEED
#endif
#ifndef MYI2C1_DUTY_CYCLE
#define MYI2C1_DUTY_CYCLE                  I2C_DutyCycle_2
#endif
#ifndef MYI2C1_ACK
#define MYI2C1_ACK                         I2C_Ack_Enable
#endif
#ifndef MYI2C1_ACK_ADDRESS
#define MYI2C1_ACK_ADDRESS                 I2C_AcknowledgedAddress_7bit
#endif

#endif /* I2C1 宏定义结束 */

/* ============================= 3. I2C2 宏定义 ============================= */
#if MYI2C2_ENABLE

/* I2C2 外设、时钟和中断宏。 */
#define MYI2C2_I2C                         I2C2
#define MYI2C2_I2C_CLK                     RCC_APB1Periph_I2C2
#define MYI2C2_I2C_CLK_CMD                 RCC_APB1PeriphClockCmd
#define MYI2C2_EV_IRQ_CHANNEL              I2C2_EV_IRQn
#define MYI2C2_ER_IRQ_CHANNEL              I2C2_ER_IRQn

/* I2C2 固定引脚：PB10/PB11。 */
#define MYI2C2_SCL_GPIO_CLK                RCC_APB2Periph_GPIOB
#define MYI2C2_SCL_PORT                    GPIOB
#define MYI2C2_SCL_PIN                     GPIO_Pin_10
#define MYI2C2_SCL_PIN_SOURCE              GPIO_PinSource10
#define MYI2C2_SCL_PORT_SOURCE             GPIO_PortSourceGPIOB
#define MYI2C2_SDA_GPIO_CLK                RCC_APB2Periph_GPIOB
#define MYI2C2_SDA_PORT                    GPIOB
#define MYI2C2_SDA_PIN                     GPIO_Pin_11
#define MYI2C2_SDA_PIN_SOURCE              GPIO_PinSource11
#define MYI2C2_SDA_PORT_SOURCE             GPIO_PortSourceGPIOB
#define MYI2C2_GPIO_CLK                    (MYI2C2_SCL_GPIO_CLK | MYI2C2_SDA_GPIO_CLK)

/* I2C2 通信参数。 */
#ifndef MYI2C2_SPEED
#define MYI2C2_SPEED                       MYI2C_DEFAULT_SPEED
#endif
#ifndef MYI2C2_DUTY_CYCLE
#define MYI2C2_DUTY_CYCLE                  I2C_DutyCycle_2
#endif
#ifndef MYI2C2_ACK
#define MYI2C2_ACK                         I2C_Ack_Enable
#endif
#ifndef MYI2C2_ACK_ADDRESS
#define MYI2C2_ACK_ADDRESS                 I2C_AcknowledgedAddress_7bit
#endif

#endif /* I2C2 宏定义结束 */

#if MYI2C_ANY_ENABLE

/* ============================= 4. 类型定义 ============================= */

/**
  * @brief  MyI2C 函数返回值。
  */
typedef enum
{
    MYI2C_OK = 0,                           /* 操作成功。 */
    MYI2C_ERROR,                            /* 操作失败。 */
    MYI2C_TIMEOUT,                          /* 等待标志位或事件超时。 */
    MYI2C_INVALID_PARAM                     /* 参数错误，或对应 I2C 未启用。 */
} MyI2C_StatusTypeDef;

/* ============================= 5. 通用函数声明 ============================= */

/**
  * @brief  初始化指定 I2C。
  * @param  I2Cx: 传入 MYI2C1_I2C 或 MYI2C2_I2C。
  */
void MyI2C_Init(I2C_TypeDef *I2Cx);

/**
  * @brief  反初始化指定 I2C。
  * @param  I2Cx: 需要关闭的 I2C 外设。
  */
void MyI2C_DeInit(I2C_TypeDef *I2Cx);

/**
  * @brief  软件复位指定 I2C。
  * @param  I2Cx: 需要复位的 I2C 外设。
  * @note   总线异常或设备无响应后，可调用该函数尝试恢复。
  */
void MyI2C_SoftwareReset(I2C_TypeDef *I2Cx);

/**
  * @brief  检测设备是否应答。
  * @param  I2Cx: 使用的 I2C 外设。
  * @param  DevAddress7Bit: 设备 7 位地址，例如 0x3C。
  */
MyI2C_StatusTypeDef MyI2C_IsDeviceReady(I2C_TypeDef *I2Cx, uint8_t DevAddress7Bit);

/**
  * @brief  向设备连续写入多个字节。
  * @param  I2Cx: 使用的 I2C 外设。
  * @param  DevAddress7Bit: 设备 7 位地址。
  * @param  Data: 待发送数据首地址。
  * @param  Length: 待发送字节数。
  */
MyI2C_StatusTypeDef MyI2C_WriteBytes(I2C_TypeDef *I2Cx, uint8_t DevAddress7Bit, const uint8_t *Data, uint16_t Length);

/**
  * @brief  从设备连续读取多个字节。
  * @param  I2Cx: 使用的 I2C 外设。
  * @param  DevAddress7Bit: 设备 7 位地址。
  * @param  Data: 接收数据保存地址。
  * @param  Length: 需要读取的字节数。
  */
MyI2C_StatusTypeDef MyI2C_ReadBytes(I2C_TypeDef *I2Cx, uint8_t DevAddress7Bit, uint8_t *Data, uint16_t Length);

/**
  * @brief  向设备的 8 位寄存器地址写入多个字节。
  * @param  MemAddress: 设备内部寄存器地址。
  */
MyI2C_StatusTypeDef MyI2C_WriteMem(I2C_TypeDef *I2Cx, uint8_t DevAddress7Bit, uint8_t MemAddress, const uint8_t *Data, uint16_t Length);

/**
  * @brief  从设备的 8 位寄存器地址读取多个字节。
  * @param  MemAddress: 设备内部寄存器地址。
  */
MyI2C_StatusTypeDef MyI2C_ReadMem(I2C_TypeDef *I2Cx, uint8_t DevAddress7Bit, uint8_t MemAddress, uint8_t *Data, uint16_t Length);

/**
  * @brief  扫描 7 位地址设备。
  * @param  FoundAddress: 保存扫描到的地址。
  * @param  MaxCount: FoundAddress 数组最大容量。
  * @retval 实际扫描到的设备数量，可能大于 MaxCount。
  */
uint8_t MyI2C_Scan(I2C_TypeDef *I2Cx, uint8_t *FoundAddress, uint8_t MaxCount);

/* ============================= 6. I2Cx 快捷函数声明 ============================= */

#if MYI2C1_ENABLE
void MyI2C1_Init(void);
void MyI2C1_DeInit(void);
MyI2C_StatusTypeDef MyI2C1_IsDeviceReady(uint8_t DevAddress7Bit);
MyI2C_StatusTypeDef MyI2C1_WriteBytes(uint8_t DevAddress7Bit, const uint8_t *Data, uint16_t Length);
MyI2C_StatusTypeDef MyI2C1_ReadBytes(uint8_t DevAddress7Bit, uint8_t *Data, uint16_t Length);
MyI2C_StatusTypeDef MyI2C1_WriteMem(uint8_t DevAddress7Bit, uint8_t MemAddress, const uint8_t *Data, uint16_t Length);
MyI2C_StatusTypeDef MyI2C1_ReadMem(uint8_t DevAddress7Bit, uint8_t MemAddress, uint8_t *Data, uint16_t Length);
uint8_t MyI2C1_Scan(uint8_t *FoundAddress, uint8_t MaxCount);
#endif

#if MYI2C2_ENABLE
void MyI2C2_Init(void);
void MyI2C2_DeInit(void);
MyI2C_StatusTypeDef MyI2C2_IsDeviceReady(uint8_t DevAddress7Bit);
MyI2C_StatusTypeDef MyI2C2_WriteBytes(uint8_t DevAddress7Bit, const uint8_t *Data, uint16_t Length);
MyI2C_StatusTypeDef MyI2C2_ReadBytes(uint8_t DevAddress7Bit, uint8_t *Data, uint16_t Length);
MyI2C_StatusTypeDef MyI2C2_WriteMem(uint8_t DevAddress7Bit, uint8_t MemAddress, const uint8_t *Data, uint16_t Length);
MyI2C_StatusTypeDef MyI2C2_ReadMem(uint8_t DevAddress7Bit, uint8_t MemAddress, uint8_t *Data, uint16_t Length);
uint8_t MyI2C2_Scan(uint8_t *FoundAddress, uint8_t MaxCount);
#endif

#endif /* MYI2C_ANY_ENABLE 配置结束 */

#ifdef __cplusplus
}
#endif

#endif /* __MYI2C_H 结束 */
