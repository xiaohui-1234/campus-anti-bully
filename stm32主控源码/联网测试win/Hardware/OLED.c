#include "OLED.h"

#if OLED_ENABLE

#include <string.h>

/* SSD1306 控制字节：0x00 表示后续是命令，0x40 表示后续是显示数据。 */
#define OLED_CONTROL_COMMAND               0x00
#define OLED_CONTROL_DATA                  0x40

/* OLED 显存缓冲区。每页 8 行，共 OLED_PAGE_COUNT 页，每页 OLED_WIDTH 字节。 */
static uint8_t OLED_Buffer[OLED_PAGE_COUNT][OLED_WIDTH];
static uint8_t OLED_DeviceAddress = OLED_ADDRESS;

/*
 * 5x7 ASCII 字模，范围 0x20~0x7F。
 * 每个字符 5 列，每列低 7 位对应从上到下的 7 个像素。
 */
static const uint8_t OLED_Font5x7[96][5] =
{
    {0x00,0x00,0x00,0x00,0x00}, {0x00,0x00,0x5F,0x00,0x00}, {0x00,0x07,0x00,0x07,0x00}, {0x14,0x7F,0x14,0x7F,0x14},
    {0x24,0x2A,0x7F,0x2A,0x12}, {0x23,0x13,0x08,0x64,0x62}, {0x36,0x49,0x55,0x22,0x50}, {0x00,0x05,0x03,0x00,0x00},
    {0x00,0x1C,0x22,0x41,0x00}, {0x00,0x41,0x22,0x1C,0x00}, {0x14,0x08,0x3E,0x08,0x14}, {0x08,0x08,0x3E,0x08,0x08},
    {0x00,0x50,0x30,0x00,0x00}, {0x08,0x08,0x08,0x08,0x08}, {0x00,0x60,0x60,0x00,0x00}, {0x20,0x10,0x08,0x04,0x02},
    {0x3E,0x51,0x49,0x45,0x3E}, {0x00,0x42,0x7F,0x40,0x00}, {0x42,0x61,0x51,0x49,0x46}, {0x21,0x41,0x45,0x4B,0x31},
    {0x18,0x14,0x12,0x7F,0x10}, {0x27,0x45,0x45,0x45,0x39}, {0x3C,0x4A,0x49,0x49,0x30}, {0x01,0x71,0x09,0x05,0x03},
    {0x36,0x49,0x49,0x49,0x36}, {0x06,0x49,0x49,0x29,0x1E}, {0x00,0x36,0x36,0x00,0x00}, {0x00,0x56,0x36,0x00,0x00},
    {0x08,0x14,0x22,0x41,0x00}, {0x14,0x14,0x14,0x14,0x14}, {0x00,0x41,0x22,0x14,0x08}, {0x02,0x01,0x51,0x09,0x06},
    {0x32,0x49,0x79,0x41,0x3E}, {0x7E,0x11,0x11,0x11,0x7E}, {0x7F,0x49,0x49,0x49,0x36}, {0x3E,0x41,0x41,0x41,0x22},
    {0x7F,0x41,0x41,0x22,0x1C}, {0x7F,0x49,0x49,0x49,0x41}, {0x7F,0x09,0x09,0x09,0x01}, {0x3E,0x41,0x49,0x49,0x7A},
    {0x7F,0x08,0x08,0x08,0x7F}, {0x00,0x41,0x7F,0x41,0x00}, {0x20,0x40,0x41,0x3F,0x01}, {0x7F,0x08,0x14,0x22,0x41},
    {0x7F,0x40,0x40,0x40,0x40}, {0x7F,0x02,0x0C,0x02,0x7F}, {0x7F,0x04,0x08,0x10,0x7F}, {0x3E,0x41,0x41,0x41,0x3E},
    {0x7F,0x09,0x09,0x09,0x06}, {0x3E,0x41,0x51,0x21,0x5E}, {0x7F,0x09,0x19,0x29,0x46}, {0x46,0x49,0x49,0x49,0x31},
    {0x01,0x01,0x7F,0x01,0x01}, {0x3F,0x40,0x40,0x40,0x3F}, {0x1F,0x20,0x40,0x20,0x1F}, {0x3F,0x40,0x38,0x40,0x3F},
    {0x63,0x14,0x08,0x14,0x63}, {0x07,0x08,0x70,0x08,0x07}, {0x61,0x51,0x49,0x45,0x43}, {0x00,0x7F,0x41,0x41,0x00},
    {0x02,0x04,0x08,0x10,0x20}, {0x00,0x41,0x41,0x7F,0x00}, {0x04,0x02,0x01,0x02,0x04}, {0x40,0x40,0x40,0x40,0x40},
    {0x00,0x01,0x02,0x04,0x00}, {0x20,0x54,0x54,0x54,0x78}, {0x7F,0x48,0x44,0x44,0x38}, {0x38,0x44,0x44,0x44,0x20},
    {0x38,0x44,0x44,0x48,0x7F}, {0x38,0x54,0x54,0x54,0x18}, {0x08,0x7E,0x09,0x01,0x02}, {0x0C,0x52,0x52,0x52,0x3E},
    {0x7F,0x08,0x04,0x04,0x78}, {0x00,0x44,0x7D,0x40,0x00}, {0x20,0x40,0x44,0x3D,0x00}, {0x7F,0x10,0x28,0x44,0x00},
    {0x00,0x41,0x7F,0x40,0x00}, {0x7C,0x04,0x18,0x04,0x78}, {0x7C,0x08,0x04,0x04,0x78}, {0x38,0x44,0x44,0x44,0x38},
    {0x7C,0x14,0x14,0x14,0x08}, {0x08,0x14,0x14,0x18,0x7C}, {0x7C,0x08,0x04,0x04,0x08}, {0x48,0x54,0x54,0x54,0x20},
    {0x04,0x3F,0x44,0x40,0x20}, {0x3C,0x40,0x40,0x20,0x7C}, {0x1C,0x20,0x40,0x20,0x1C}, {0x3C,0x40,0x30,0x40,0x3C},
    {0x44,0x28,0x10,0x28,0x44}, {0x0C,0x50,0x50,0x50,0x3C}, {0x44,0x64,0x54,0x4C,0x44}, {0x00,0x08,0x36,0x41,0x00},
    {0x00,0x00,0x7F,0x00,0x00}, {0x00,0x41,0x36,0x08,0x00}, {0x08,0x08,0x2A,0x1C,0x08}, {0x00,0x00,0x00,0x00,0x00}
};

/**
  * @brief  简单延时，用于 OLED 上电稳定等待。
  */
static void OLED_Delay(volatile uint32_t Count)
{
    while (Count--)
    {
    }
}

/**
  * @brief  求无符号整数幂。
  */
static uint32_t OLED_Pow(uint32_t X, uint8_t Y)
{
    uint32_t Result;

    Result = 1;
    while (Y--)
    {
        Result *= X;
    }

    return Result;
}

/**
  * @brief  获取指定字号的字符宽度。
  */
static uint8_t OLED_GetCharWidth(uint8_t Size)
{
    if (Size >= 16)
    {
        return 12;
    }

    return 6;
}

/**
  * @brief  获取指定字号的字符高度。
  */
static uint8_t OLED_GetCharHeight(uint8_t Size)
{
    if (Size >= 16)
    {
        return 16;
    }

    return 8;
}

/**
  * @brief  写入一组初始化命令。
  */
static MyI2C_StatusTypeDef OLED_WriteCommandList(const uint8_t *CommandList, uint16_t Length)
{
    uint16_t Index;
    MyI2C_StatusTypeDef Status;

    for (Index = 0; Index < Length; Index++)
    {
        Status = OLED_WriteCommand(CommandList[Index]);
        if (Status != MYI2C_OK)
        {
            return Status;
        }
    }

    return MYI2C_OK;
}

/**
  * @brief  修改显存中的一个像素，不立即刷新。
  */
static void OLED_DrawPointNoUpdate(uint8_t X, uint8_t Y, uint8_t Color)
{
    if ((X >= OLED_WIDTH) || (Y >= OLED_HEIGHT))
    {
        return;
    }

    if (Color)
    {
        OLED_Buffer[Y / 8][X] |= (uint8_t)(1 << (Y % 8));
    }
    else
    {
        OLED_Buffer[Y / 8][X] &= (uint8_t)~(1 << (Y % 8));
    }
}

/**
  * @brief  清空显存中的一个矩形区域，不立即刷新。
  */
static void OLED_ClearAreaNoUpdate(uint8_t X, uint8_t Y, uint8_t Width, uint8_t Height)
{
    uint8_t i;
    uint8_t j;

    for (j = Y; (j < (uint8_t)(Y + Height)) && (j < OLED_HEIGHT); j++)
    {
        for (i = X; (i < (uint8_t)(X + Width)) && (i < OLED_WIDTH); i++)
        {
            OLED_DrawPointNoUpdate(i, j, OLED_COLOR_OFF);
        }
    }
}

/**
  * @brief  绘制一个字符到显存，不立即刷新。
  */
static void OLED_ShowCharNoUpdate(uint8_t X, uint8_t Y, char Char, uint8_t Size)
{
    uint8_t FontIndex;
    uint8_t Column;
    uint8_t Row;
    uint8_t Scale;
    uint8_t dx;
    uint8_t dy;
    uint8_t PixelOn;

    if ((Char < ' ') || (Char > 0x7F))
    {
        Char = '?';
    }

    FontIndex = (uint8_t)(Char - ' ');
    Scale = (Size >= 16) ? 2 : 1;

    OLED_ClearAreaNoUpdate(X, Y, OLED_GetCharWidth(Size), OLED_GetCharHeight(Size));

    for (Column = 0; Column < 5; Column++)
    {
        for (Row = 0; Row < 7; Row++)
        {
            PixelOn = (uint8_t)((OLED_Font5x7[FontIndex][Column] >> Row) & 0x01);
            if (PixelOn)
            {
                for (dx = 0; dx < Scale; dx++)
                {
                    for (dy = 0; dy < Scale; dy++)
                    {
                        OLED_DrawPointNoUpdate((uint8_t)(X + Column * Scale + dx),
                                               (uint8_t)(Y + Row * Scale + dy),
                                               OLED_COLOR_ON);
                    }
                }
            }
        }
    }
}

/**
  * @brief  绘制一条线到显存，不立即刷新。
  */
static void OLED_DrawLineNoUpdate(uint8_t X0, uint8_t Y0, uint8_t X1, uint8_t Y1, uint8_t Color)
{
    int16_t x0;
    int16_t y0;
    int16_t x1;
    int16_t y1;
    int16_t dx;
    int16_t sx;
    int16_t dy;
    int16_t sy;
    int16_t err;
    int16_t e2;

    x0 = X0;
    y0 = Y0;
    x1 = X1;
    y1 = Y1;
    dx = (x0 < x1) ? (int16_t)(x1 - x0) : (int16_t)(x0 - x1);
    sx = (x0 < x1) ? 1 : -1;
    dy = (y0 < y1) ? (int16_t)(y0 - y1) : (int16_t)(y1 - y0);
    sy = (y0 < y1) ? 1 : -1;
    err = (int16_t)(dx + dy);

    while (1)
    {
        OLED_DrawPointNoUpdate((uint8_t)x0, (uint8_t)y0, Color);
        if ((x0 == x1) && (y0 == y1))
        {
            break;
        }

        e2 = (int16_t)(2 * err);
        if (e2 >= dy)
        {
            err = (int16_t)(err + dy);
            x0 = (int16_t)(x0 + sx);
        }
        if (e2 <= dx)
        {
            err = (int16_t)(err + dx);
            y0 = (int16_t)(y0 + sy);
        }
    }
}

/**
  * @brief  执行 SSD1306 初始化命令序列。
  */
static MyI2C_StatusTypeDef OLED_RunInitSequence(void)
{
    static const uint8_t InitCommands[] =
    {
        0xAE,       /* 显示关闭。 */
        0x20, 0x00, /* 水平地址模式。 */
        0xB0,       /* 页地址起始页。 */
        0xC8,       /* COM 扫描方向。 */
        0x00,       /* 低列地址。 */
        0x10,       /* 高列地址。 */
        0x40,       /* 显示起始行。 */
        0x81, 0x7F, /* 对比度。 */
        0xA1,       /* 段重映射。 */
        0xA6,       /* 正常显示。 */
        0xA8, 0x3F, /* 多路复用率，64 行。 */
        0xA4,       /* 显示来自显存内容。 */
        0xD3, 0x00, /* 显示偏移。 */
        0xD5, 0x80, /* 显示时钟。 */
        0xD9, 0xF1, /* 预充电周期。 */
        0xDA, 0x12, /* COM 引脚配置。 */
        0xDB, 0x40, /* VCOMH 电平。 */
        0x8D, 0x14, /* 电荷泵开启。 */
        0xAF        /* 显示开启。 */
    };
    return OLED_WriteCommandList(InitCommands, (uint16_t)sizeof(InitCommands));
}

/**
  * @brief  使用 OLED_ADDRESS 宏初始化 OLED。
  */
MyI2C_StatusTypeDef OLED_Init(void)
{
    return OLED_InitWithAddress(OLED_ADDRESS);
}

/**
  * @brief  自动尝试 0x3C 和 0x3D 地址并初始化 OLED。
  * @note   常见 0.96 寸 OLED 地址通常是这两个之一。
  */
MyI2C_StatusTypeDef OLED_InitAuto(void)
{
    MyI2C_StatusTypeDef Status;

    MyI2C_Init(OLED_I2C);
    OLED_Delay(100000);

    Status = MyI2C_IsDeviceReady(OLED_I2C, 0x3C);
    if (Status == MYI2C_OK)
    {
        return OLED_InitWithAddress(0x3C);
    }

    MyI2C_SoftwareReset(OLED_I2C);
    Status = MyI2C_IsDeviceReady(OLED_I2C, 0x3D);
    if (Status == MYI2C_OK)
    {
        return OLED_InitWithAddress(0x3D);
    }

    OLED_DeviceAddress = OLED_ADDRESS;
    return Status;
}

/**
  * @brief  使用指定 7 位地址初始化 OLED。
  */
MyI2C_StatusTypeDef OLED_InitWithAddress(uint8_t Address7Bit)
{
    MyI2C_StatusTypeDef Status;

    OLED_DeviceAddress = Address7Bit;
    MyI2C_Init(OLED_I2C);
    OLED_Delay(100000);

    Status = OLED_IsReady();
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    Status = OLED_RunInitSequence();
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    OLED_Clear();
    return MYI2C_OK;
}

/**
  * @brief  检测 OLED 是否应答。
  */
MyI2C_StatusTypeDef OLED_IsReady(void)
{
    return MyI2C_IsDeviceReady(OLED_I2C, OLED_DeviceAddress);
}

/**
  * @brief  设置 OLED 当前使用的 7 位地址。
  */
void OLED_SetAddress(uint8_t Address7Bit)
{
    OLED_DeviceAddress = Address7Bit;
}

/**
  * @brief  获取 OLED 当前使用的 7 位地址。
  */
uint8_t OLED_GetAddress(void)
{
    return OLED_DeviceAddress;
}

/**
  * @brief  写入 OLED 命令。
  */
MyI2C_StatusTypeDef OLED_WriteCommand(uint8_t Command)
{
    uint8_t Buffer[2];

    Buffer[0] = OLED_CONTROL_COMMAND;
    Buffer[1] = Command;
    return MyI2C_WriteBytes(OLED_I2C, OLED_DeviceAddress, Buffer, 2);
}

/**
  * @brief  写入 OLED 显示数据。
  */
MyI2C_StatusTypeDef OLED_WriteData(const uint8_t *Data, uint16_t Length)
{
    uint8_t Buffer[OLED_WIDTH + 1];
    uint16_t Offset;
    uint16_t ChunkLength;
    uint16_t Index;
    MyI2C_StatusTypeDef Status;

    if (Data == 0)
    {
        return MYI2C_INVALID_PARAM;
    }

    Offset = 0;
    while (Offset < Length)
    {
        ChunkLength = (uint16_t)(Length - Offset);
        if (ChunkLength > OLED_WIDTH)
        {
            ChunkLength = OLED_WIDTH;
        }

        Buffer[0] = OLED_CONTROL_DATA;
        for (Index = 0; Index < ChunkLength; Index++)
        {
            Buffer[Index + 1] = Data[Offset + Index];
        }

        Status = MyI2C_WriteBytes(OLED_I2C, OLED_DeviceAddress, Buffer, (uint16_t)(ChunkLength + 1));
        if (Status != MYI2C_OK)
        {
            return Status;
        }

        Offset = (uint16_t)(Offset + ChunkLength);
    }

    return MYI2C_OK;
}

/**
  * @brief  设置 OLED 页地址和列地址。
  */
void OLED_SetCursor(uint8_t X, uint8_t Page)
{
    if ((X >= OLED_WIDTH) || (Page >= OLED_PAGE_COUNT))
    {
        return;
    }

    OLED_WriteCommand((uint8_t)(0xB0 + Page));
    OLED_WriteCommand((uint8_t)(0x00 + (X & 0x0F)));
    OLED_WriteCommand((uint8_t)(0x10 + ((X >> 4) & 0x0F)));
}

/**
  * @brief  将全部显存刷新到 OLED。
  */
void OLED_Update(void)
{
    uint8_t Page;

    for (Page = 0; Page < OLED_PAGE_COUNT; Page++)
    {
        OLED_SetCursor(0, Page);
        OLED_WriteData(OLED_Buffer[Page], OLED_WIDTH);
    }
}

/**
  * @brief  刷新指定区域。
  */
void OLED_UpdateArea(uint8_t X, uint8_t Y, uint8_t Width, uint8_t Height)
{
    uint8_t PageStart;
    uint8_t PageEnd;
    uint8_t Page;

    if ((X >= OLED_WIDTH) || (Y >= OLED_HEIGHT) || (Width == 0) || (Height == 0))
    {
        return;
    }

    if ((uint16_t)X + Width > OLED_WIDTH)
    {
        Width = (uint8_t)(OLED_WIDTH - X);
    }
    if ((uint16_t)Y + Height > OLED_HEIGHT)
    {
        Height = (uint8_t)(OLED_HEIGHT - Y);
    }

    PageStart = (uint8_t)(Y / 8);
    PageEnd = (uint8_t)((Y + Height - 1) / 8);

    for (Page = PageStart; Page <= PageEnd; Page++)
    {
        OLED_SetCursor(X, Page);
        OLED_WriteData(&OLED_Buffer[Page][X], Width);
    }
}

/**
  * @brief  清屏。
  */
void OLED_Clear(void)
{
    memset(OLED_Buffer, 0x00, sizeof(OLED_Buffer));
    OLED_Update();
}

/**
  * @brief  清除指定区域。
  */
void OLED_ClearArea(uint8_t X, uint8_t Y, uint8_t Width, uint8_t Height)
{
    OLED_ClearAreaNoUpdate(X, Y, Width, Height);
#if OLED_AUTO_UPDATE
    OLED_UpdateArea(X, Y, Width, Height);
#endif
}

/**
  * @brief  开启显示。
  */
void OLED_DisplayOn(void)
{
    OLED_WriteCommand(0x8D);
    OLED_WriteCommand(0x14);
    OLED_WriteCommand(0xAF);
}

/**
  * @brief  关闭显示。
  */
void OLED_DisplayOff(void)
{
    OLED_WriteCommand(0x8D);
    OLED_WriteCommand(0x10);
    OLED_WriteCommand(0xAE);
}

/**
  * @brief  设置亮度。
  */
void OLED_SetBrightness(uint8_t Brightness)
{
    OLED_WriteCommand(0x81);
    OLED_WriteCommand(Brightness);
}

/**
  * @brief  显示一个 ASCII 字符。
  */
void OLED_ShowChar(uint8_t X, uint8_t Y, char Char, uint8_t Size)
{
    OLED_ShowCharNoUpdate(X, Y, Char, Size);
#if OLED_AUTO_UPDATE
    OLED_UpdateArea(X, Y, OLED_GetCharWidth(Size), OLED_GetCharHeight(Size));
#endif
}

/**
  * @brief  显示 ASCII 字符串。
  */
void OLED_ShowString(uint8_t X, uint8_t Y, const char *String, uint8_t Size)
{
    uint8_t StartX;
    uint8_t CharWidth;
    uint8_t CharHeight;
    uint8_t Width;

    if (String == 0)
    {
        return;
    }

    StartX = X;
    CharWidth = OLED_GetCharWidth(Size);
    CharHeight = OLED_GetCharHeight(Size);
    Width = 0;

    while ((*String != '\0') && ((uint16_t)X + CharWidth <= OLED_WIDTH))
    {
        OLED_ShowCharNoUpdate(X, Y, *String, Size);
        X = (uint8_t)(X + CharWidth);
        Width = (uint8_t)(Width + CharWidth);
        String++;
    }

#if OLED_AUTO_UPDATE
    if (Width > 0)
    {
        OLED_UpdateArea(StartX, Y, Width, CharHeight);
    }
#endif
}

/**
  * @brief  显示固定长度无符号十进制数字。
  */
void OLED_ShowNum(uint8_t X, uint8_t Y, uint32_t Number, uint8_t Length, uint8_t Size)
{
    char String[11];
    uint8_t Index;

    if (Length > 10)
    {
        Length = 10;
    }

    for (Index = 0; Index < Length; Index++)
    {
        String[Index] = (char)(Number / OLED_Pow(10, (uint8_t)(Length - Index - 1)) % 10 + '0');
    }
    String[Length] = '\0';

    OLED_ShowString(X, Y, String, Size);
}

/**
  * @brief  显示固定长度有符号十进制数字。
  */
void OLED_ShowSignedNum(uint8_t X, uint8_t Y, int32_t Number, uint8_t Length, uint8_t Size)
{
    uint32_t AbsNumber;

    if (Number >= 0)
    {
        OLED_ShowChar(X, Y, '+', Size);
        AbsNumber = (uint32_t)Number;
    }
    else
    {
        OLED_ShowChar(X, Y, '-', Size);
        AbsNumber = (uint32_t)(-Number);
    }

    OLED_ShowNum((uint8_t)(X + OLED_GetCharWidth(Size)), Y, AbsNumber, Length, Size);
}

/**
  * @brief  显示固定长度十六进制数字。
  */
void OLED_ShowHexNum(uint8_t X, uint8_t Y, uint32_t Number, uint8_t Length, uint8_t Size)
{
    char String[9];
    uint8_t Index;
    uint8_t Digit;

    if (Length > 8)
    {
        Length = 8;
    }

    for (Index = 0; Index < Length; Index++)
    {
        Digit = (uint8_t)(Number / OLED_Pow(16, (uint8_t)(Length - Index - 1)) % 16);
        if (Digit < 10)
        {
            String[Index] = (char)(Digit + '0');
        }
        else
        {
            String[Index] = (char)(Digit - 10 + 'A');
        }
    }
    String[Length] = '\0';

    OLED_ShowString(X, Y, String, Size);
}

/**
  * @brief  显示固定长度二进制数字。
  */
void OLED_ShowBinNum(uint8_t X, uint8_t Y, uint32_t Number, uint8_t Length, uint8_t Size)
{
    char String[33];
    uint8_t Index;

    if (Length > 32)
    {
        Length = 32;
    }

    for (Index = 0; Index < Length; Index++)
    {
        String[Index] = (char)(Number / OLED_Pow(2, (uint8_t)(Length - Index - 1)) % 2 + '0');
    }
    String[Length] = '\0';

    OLED_ShowString(X, Y, String, Size);
}

/**
  * @brief  绘制一个像素点。
  */
void OLED_DrawPoint(uint8_t X, uint8_t Y, uint8_t Color)
{
    OLED_DrawPointNoUpdate(X, Y, Color);
#if OLED_AUTO_UPDATE
    OLED_UpdateArea(X, Y, 1, 1);
#endif
}

/**
  * @brief  读取显存中的一个像素点。
  */
uint8_t OLED_GetPoint(uint8_t X, uint8_t Y)
{
    if ((X >= OLED_WIDTH) || (Y >= OLED_HEIGHT))
    {
        return 0;
    }

    return (uint8_t)((OLED_Buffer[Y / 8][X] >> (Y % 8)) & 0x01);
}

/**
  * @brief  绘制直线。
  */
void OLED_DrawLine(uint8_t X0, uint8_t Y0, uint8_t X1, uint8_t Y1, uint8_t Color)
{
    uint8_t XMin;
    uint8_t XMax;
    uint8_t YMin;
    uint8_t YMax;

    OLED_DrawLineNoUpdate(X0, Y0, X1, Y1, Color);

#if OLED_AUTO_UPDATE
    XMin = (X0 < X1) ? X0 : X1;
    XMax = (X0 > X1) ? X0 : X1;
    YMin = (Y0 < Y1) ? Y0 : Y1;
    YMax = (Y0 > Y1) ? Y0 : Y1;
    OLED_UpdateArea(XMin, YMin, (uint8_t)(XMax - XMin + 1), (uint8_t)(YMax - YMin + 1));
#else
    (void)XMin;
    (void)XMax;
    (void)YMin;
    (void)YMax;
#endif
}

/**
  * @brief  绘制矩形。
  */
void OLED_DrawRectangle(uint8_t X, uint8_t Y, uint8_t Width, uint8_t Height, uint8_t IsFilled, uint8_t Color)
{
    uint8_t i;
    uint8_t j;

    if ((Width == 0) || (Height == 0))
    {
        return;
    }

    if (IsFilled)
    {
        for (j = Y; (j < (uint8_t)(Y + Height)) && (j < OLED_HEIGHT); j++)
        {
            for (i = X; (i < (uint8_t)(X + Width)) && (i < OLED_WIDTH); i++)
            {
                OLED_DrawPointNoUpdate(i, j, Color);
            }
        }
    }
    else
    {
        OLED_DrawLineNoUpdate(X, Y, (uint8_t)(X + Width - 1), Y, Color);
        OLED_DrawLineNoUpdate(X, (uint8_t)(Y + Height - 1), (uint8_t)(X + Width - 1), (uint8_t)(Y + Height - 1), Color);
        OLED_DrawLineNoUpdate(X, Y, X, (uint8_t)(Y + Height - 1), Color);
        OLED_DrawLineNoUpdate((uint8_t)(X + Width - 1), Y, (uint8_t)(X + Width - 1), (uint8_t)(Y + Height - 1), Color);
    }

#if OLED_AUTO_UPDATE
    OLED_UpdateArea(X, Y, Width, Height);
#endif
}

/**
  * @brief  绘制图片。
  * @note   图片数据格式：按页存储，每字节纵向 8 个像素，和 OLED 显存格式一致。
  */
void OLED_DrawImage(uint8_t X, uint8_t Y, uint8_t Width, uint8_t Height, const uint8_t *Image)
{
    uint8_t i;
    uint8_t j;
    uint16_t ByteIndex;
    uint8_t PixelOn;

    if (Image == 0)
    {
        return;
    }

    for (j = 0; j < Height; j++)
    {
        for (i = 0; i < Width; i++)
        {
            ByteIndex = (uint16_t)((j / 8) * Width + i);
            PixelOn = (uint8_t)((Image[ByteIndex] >> (j % 8)) & 0x01);
            OLED_DrawPointNoUpdate((uint8_t)(X + i), (uint8_t)(Y + j), PixelOn);
        }
    }

#if OLED_AUTO_UPDATE
    OLED_UpdateArea(X, Y, Width, Height);
#endif
}

#endif /* OLED_ENABLE 配置结束 */
