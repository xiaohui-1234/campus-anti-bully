#ifndef __OLED_H
#define __OLED_H

#ifdef __cplusplus
extern "C" {
#endif

#include "MyI2C.h"

/**
  ******************************************************************************
  * @file    OLED.h
  * @brief   0.96 寸 I2C OLED 驱动库，默认适配 SSD1306 128x64。
  * @details
  *          常见 0.96 寸 OLED 模块参数：
  *          - 控制芯片：SSD1306。
  *          - 分辨率：128x64。
  *          - 接口：I2C。
  *          - 常见 7 位地址：0x3C，少数模块为 0x3D。
  *
  *          默认接线：
  *          - OLED VCC -> 3.3V 或 5V，按模块丝印决定。
  *          - OLED GND -> GND。
  *          - OLED SCL -> I2C1 SCL，默认 PB6。
  *          - OLED SDA -> I2C1 SDA，默认 PB7。
  *
  *          使用示例：
  *          @code
  *          #include "OLED.h"
  *
  *          int main(void)
  *          {
  *              OLED_Init();
  *              OLED_Clear();
  *              OLED_ShowString(0, 0, "Hello", 8);
  *              OLED_ShowNum(0, 16, 1234, 4, 16);
  *
  *              while (1)
  *              {
  *              }
  *          }
  *          @endcode
  ******************************************************************************
  */

/* ============================= 1. 用户配置区 ============================= */

/**
  * @brief  OLED 驱动编译开关。
  */
#ifndef OLED_ENABLE
#define OLED_ENABLE                        1
#endif

#if OLED_ENABLE

/**
  * @brief  OLED 使用的 I2C 外设。
  * @note   默认使用 I2C1。若要改用 I2C2，请改为 MYI2C2_I2C，并确认 MYI2C2_ENABLE 为 1。
  */
#ifndef OLED_I2C
#define OLED_I2C                           MYI2C1_I2C
#endif

/**
  * @brief  OLED 7 位 I2C 地址。
  * @note   常见模块为 0x3C；如果屏幕无响应，可尝试 0x3D 或用 MyI2C_Scan() 扫描。
  */
#ifndef OLED_ADDRESS
#define OLED_ADDRESS                       0x3C
#endif

/**
  * @brief  OLED 屏幕宽度和高度。
  */
#ifndef OLED_WIDTH
#define OLED_WIDTH                         128
#endif

#ifndef OLED_HEIGHT
#define OLED_HEIGHT                        64
#endif

/**
  * @brief  绘图函数是否自动刷新到屏幕。
  * @note   1：OLED_ShowString()、OLED_DrawLine() 等函数执行后自动刷新对应区域。
  *         0：只改显存，需要用户手动调用 OLED_Update() 或 OLED_UpdateArea()。
  */
#ifndef OLED_AUTO_UPDATE
#define OLED_AUTO_UPDATE                   1
#endif

#define OLED_PAGE_COUNT                    (OLED_HEIGHT / 8)
#define OLED_COLOR_OFF                     0
#define OLED_COLOR_ON                      1

/* ============================= 2. 基础控制函数 ============================= */

MyI2C_StatusTypeDef OLED_Init(void);
MyI2C_StatusTypeDef OLED_InitAuto(void);
MyI2C_StatusTypeDef OLED_InitWithAddress(uint8_t Address7Bit);
MyI2C_StatusTypeDef OLED_IsReady(void);
void OLED_SetAddress(uint8_t Address7Bit);
uint8_t OLED_GetAddress(void);
MyI2C_StatusTypeDef OLED_WriteCommand(uint8_t Command);
MyI2C_StatusTypeDef OLED_WriteData(const uint8_t *Data, uint16_t Length);
void OLED_SetCursor(uint8_t X, uint8_t Page);
void OLED_Update(void);
void OLED_UpdateArea(uint8_t X, uint8_t Y, uint8_t Width, uint8_t Height);
void OLED_Clear(void);
void OLED_ClearArea(uint8_t X, uint8_t Y, uint8_t Width, uint8_t Height);
void OLED_DisplayOn(void);
void OLED_DisplayOff(void);
void OLED_SetBrightness(uint8_t Brightness);

/* ============================= 3. 显示字符和数字 ============================= */

void OLED_ShowChar(uint8_t X, uint8_t Y, char Char, uint8_t Size);
void OLED_ShowString(uint8_t X, uint8_t Y, const char *String, uint8_t Size);
void OLED_ShowNum(uint8_t X, uint8_t Y, uint32_t Number, uint8_t Length, uint8_t Size);
void OLED_ShowSignedNum(uint8_t X, uint8_t Y, int32_t Number, uint8_t Length, uint8_t Size);
void OLED_ShowHexNum(uint8_t X, uint8_t Y, uint32_t Number, uint8_t Length, uint8_t Size);
void OLED_ShowBinNum(uint8_t X, uint8_t Y, uint32_t Number, uint8_t Length, uint8_t Size);

/* ============================= 4. 基础绘图函数 ============================= */

void OLED_DrawPoint(uint8_t X, uint8_t Y, uint8_t Color);
uint8_t OLED_GetPoint(uint8_t X, uint8_t Y);
void OLED_DrawLine(uint8_t X0, uint8_t Y0, uint8_t X1, uint8_t Y1, uint8_t Color);
void OLED_DrawRectangle(uint8_t X, uint8_t Y, uint8_t Width, uint8_t Height, uint8_t IsFilled, uint8_t Color);
void OLED_DrawImage(uint8_t X, uint8_t Y, uint8_t Width, uint8_t Height, const uint8_t *Image);

#endif /* OLED_ENABLE 配置结束 */

#ifdef __cplusplus
}
#endif

#endif /* __OLED_H 结束 */
