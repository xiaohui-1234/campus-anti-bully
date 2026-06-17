#include "MyI2C.h"

#if MYI2C_ANY_ENABLE

typedef void (*MyI2C_ClockCmdFunc)(uint32_t RCC_Periph, FunctionalState NewState);

/**
  * @brief  单路 I2C 的硬件配置表项。
  */
typedef struct
{
    I2C_TypeDef *I2Cx;                      /* I2C 外设基地址，如 I2C1。 */
    uint32_t I2CClock;                      /* I2C 外设时钟。 */
    MyI2C_ClockCmdFunc I2CClockCmd;         /* I2C 时钟控制函数。 */
    uint32_t GPIOClock;                     /* SCL/SDA 所在 GPIO 端口时钟。 */
    GPIO_TypeDef *SCLPort;                  /* SCL 引脚所在端口。 */
    uint16_t SCLPin;                        /* SCL 引脚。 */
    GPIO_TypeDef *SDAPort;                  /* SDA 引脚所在端口。 */
    uint16_t SDAPin;                        /* SDA 引脚。 */
    uint32_t ClockSpeed;                    /* I2C 通信速度。 */
    uint16_t DutyCycle;                     /* 快速模式占空比。 */
    uint16_t Ack;                           /* 应答使能。 */
    uint16_t AckAddress;                    /* 应答地址长度。 */
} MyI2C_ConfigTypeDef;

/* 已启用 I2C 的配置表。 */
static const MyI2C_ConfigTypeDef MyI2C_ConfigList[] =
{
#if MYI2C1_ENABLE
    {
        MYI2C1_I2C,
        MYI2C1_I2C_CLK,
        MYI2C1_I2C_CLK_CMD,
        MYI2C1_GPIO_CLK,
        MYI2C1_SCL_PORT,
        MYI2C1_SCL_PIN,
        MYI2C1_SDA_PORT,
        MYI2C1_SDA_PIN,
        MYI2C1_SPEED,
        MYI2C1_DUTY_CYCLE,
        MYI2C1_ACK,
        MYI2C1_ACK_ADDRESS
    },
#endif
#if MYI2C2_ENABLE
    {
        MYI2C2_I2C,
        MYI2C2_I2C_CLK,
        MYI2C2_I2C_CLK_CMD,
        MYI2C2_GPIO_CLK,
        MYI2C2_SCL_PORT,
        MYI2C2_SCL_PIN,
        MYI2C2_SDA_PORT,
        MYI2C2_SDA_PIN,
        MYI2C2_SPEED,
        MYI2C2_DUTY_CYCLE,
        MYI2C2_ACK,
        MYI2C2_ACK_ADDRESS
    },
#endif
};

#define MYI2C_CONFIG_COUNT ((uint8_t)(sizeof(MyI2C_ConfigList) / sizeof(MyI2C_ConfigList[0])))

/**
  * @brief  根据 I2C 外设指针查找对应配置。
  */
static const MyI2C_ConfigTypeDef *MyI2C_GetConfig(I2C_TypeDef *I2Cx)
{
    uint8_t Index;

    for (Index = 0; Index < MYI2C_CONFIG_COUNT; Index++)
    {
        if (MyI2C_ConfigList[Index].I2Cx == I2Cx)
        {
            return &MyI2C_ConfigList[Index];
        }
    }

    return 0;
}

/**
  * @brief  等待指定 I2C 事件发生。
  */
static MyI2C_StatusTypeDef MyI2C_WaitEvent(I2C_TypeDef *I2Cx, uint32_t Event)
{
    uint32_t Timeout;

    Timeout = MYI2C_TIMEOUT_COUNT;
    while (I2C_CheckEvent(I2Cx, Event) != SUCCESS)
    {
        if (I2C_GetFlagStatus(I2Cx, I2C_FLAG_AF) == SET)
        {
            I2C_ClearFlag(I2Cx, I2C_FLAG_AF);
            I2C_GenerateSTOP(I2Cx, ENABLE);
            return MYI2C_ERROR;
        }

        if (Timeout-- == 0)
        {
            I2C_GenerateSTOP(I2Cx, ENABLE);
            return MYI2C_TIMEOUT;
        }
    }

    return MYI2C_OK;
}

/**
  * @brief  等待指定 I2C 标志位达到目标状态。
  */
static MyI2C_StatusTypeDef MyI2C_WaitFlag(I2C_TypeDef *I2Cx, uint32_t Flag, FlagStatus TargetStatus)
{
    uint32_t Timeout;

    Timeout = MYI2C_TIMEOUT_COUNT;
    while (I2C_GetFlagStatus(I2Cx, Flag) != TargetStatus)
    {
        if (I2C_GetFlagStatus(I2Cx, I2C_FLAG_AF) == SET)
        {
            I2C_ClearFlag(I2Cx, I2C_FLAG_AF);
            I2C_GenerateSTOP(I2Cx, ENABLE);
            return MYI2C_ERROR;
        }

        if (Timeout-- == 0)
        {
            I2C_GenerateSTOP(I2Cx, ENABLE);
            return MYI2C_TIMEOUT;
        }
    }

    return MYI2C_OK;
}

/**
  * @brief  等待总线空闲。
  */
static MyI2C_StatusTypeDef MyI2C_WaitBusFree(I2C_TypeDef *I2Cx)
{
    return MyI2C_WaitFlag(I2Cx, I2C_FLAG_BUSY, RESET);
}

/**
  * @brief  按宏配置 I2C1 重映射。
  */
static void MyI2C_ConfigRemap(I2C_TypeDef *I2Cx)
{
#if (MYI2C1_ENABLE && MYI2C1_REMAP)
    if (I2Cx == MYI2C1_I2C)
    {
        RCC_APB2PeriphClockCmd(RCC_APB2Periph_AFIO, ENABLE);
        GPIO_PinRemapConfig(MYI2C1_GPIO_REMAP, ENABLE);
    }
#else
    (void)I2Cx;
#endif
}

/**
  * @brief  初始化指定 I2C。
  */
void MyI2C_Init(I2C_TypeDef *I2Cx)
{
    const MyI2C_ConfigTypeDef *Config;
    GPIO_InitTypeDef GPIO_InitStructure;
    I2C_InitTypeDef I2C_InitStructure;

    Config = MyI2C_GetConfig(I2Cx);
    if (Config == 0)
    {
        return;
    }

    Config->I2CClockCmd(Config->I2CClock, ENABLE);
    RCC_APB2PeriphClockCmd(Config->GPIOClock, ENABLE);
    MyI2C_ConfigRemap(I2Cx);

    GPIO_InitStructure.GPIO_Pin = Config->SCLPin;
    GPIO_InitStructure.GPIO_Mode = GPIO_Mode_AF_OD;
    GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;
    GPIO_Init(Config->SCLPort, &GPIO_InitStructure);

    GPIO_InitStructure.GPIO_Pin = Config->SDAPin;
    GPIO_InitStructure.GPIO_Mode = GPIO_Mode_AF_OD;
    GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;
    GPIO_Init(Config->SDAPort, &GPIO_InitStructure);

    I2C_DeInit(Config->I2Cx);
    I2C_InitStructure.I2C_Mode = I2C_Mode_I2C;
    I2C_InitStructure.I2C_ClockSpeed = Config->ClockSpeed;
    I2C_InitStructure.I2C_DutyCycle = Config->DutyCycle;
    I2C_InitStructure.I2C_OwnAddress1 = MYI2C_OWN_ADDRESS;
    I2C_InitStructure.I2C_Ack = Config->Ack;
    I2C_InitStructure.I2C_AcknowledgedAddress = Config->AckAddress;
    I2C_Init(Config->I2Cx, &I2C_InitStructure);
    I2C_Cmd(Config->I2Cx, ENABLE);
}

/**
  * @brief  关闭指定 I2C。
  */
void MyI2C_DeInit(I2C_TypeDef *I2Cx)
{
    const MyI2C_ConfigTypeDef *Config;

    Config = MyI2C_GetConfig(I2Cx);
    if (Config == 0)
    {
        return;
    }

    I2C_Cmd(Config->I2Cx, DISABLE);
    I2C_DeInit(Config->I2Cx);
    Config->I2CClockCmd(Config->I2CClock, DISABLE);
}

/**
  * @brief  软件复位指定 I2C。
  */
void MyI2C_SoftwareReset(I2C_TypeDef *I2Cx)
{
    if (MyI2C_GetConfig(I2Cx) == 0)
    {
        return;
    }

    I2C_SoftwareResetCmd(I2Cx, ENABLE);
    I2C_SoftwareResetCmd(I2Cx, DISABLE);
    I2C_Cmd(I2Cx, ENABLE);
}

/**
  * @brief  检测设备是否应答。
  */
MyI2C_StatusTypeDef MyI2C_IsDeviceReady(I2C_TypeDef *I2Cx, uint8_t DevAddress7Bit)
{
    MyI2C_StatusTypeDef Status;

    if (MyI2C_GetConfig(I2Cx) == 0)
    {
        return MYI2C_INVALID_PARAM;
    }

    Status = MyI2C_WaitBusFree(I2Cx);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    I2C_GenerateSTART(I2Cx, ENABLE);
    Status = MyI2C_WaitEvent(I2Cx, I2C_EVENT_MASTER_MODE_SELECT);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    I2C_Send7bitAddress(I2Cx, MYI2C_7BIT_ADDR_TO_8BIT(DevAddress7Bit), I2C_Direction_Transmitter);
    Status = MyI2C_WaitEvent(I2Cx, I2C_EVENT_MASTER_TRANSMITTER_MODE_SELECTED);
    I2C_GenerateSTOP(I2Cx, ENABLE);

    return Status;
}

/**
  * @brief  向设备连续写入多个字节。
  */
MyI2C_StatusTypeDef MyI2C_WriteBytes(I2C_TypeDef *I2Cx, uint8_t DevAddress7Bit, const uint8_t *Data, uint16_t Length)
{
    MyI2C_StatusTypeDef Status;
    uint16_t Index;

    if ((MyI2C_GetConfig(I2Cx) == 0) || (Data == 0))
    {
        return MYI2C_INVALID_PARAM;
    }

    if (Length == 0)
    {
        return MYI2C_OK;
    }

    Status = MyI2C_WaitBusFree(I2Cx);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    I2C_GenerateSTART(I2Cx, ENABLE);
    Status = MyI2C_WaitEvent(I2Cx, I2C_EVENT_MASTER_MODE_SELECT);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    I2C_Send7bitAddress(I2Cx, MYI2C_7BIT_ADDR_TO_8BIT(DevAddress7Bit), I2C_Direction_Transmitter);
    Status = MyI2C_WaitEvent(I2Cx, I2C_EVENT_MASTER_TRANSMITTER_MODE_SELECTED);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    for (Index = 0; Index < Length; Index++)
    {
        I2C_SendData(I2Cx, Data[Index]);
        Status = MyI2C_WaitEvent(I2Cx, I2C_EVENT_MASTER_BYTE_TRANSMITTED);
        if (Status != MYI2C_OK)
        {
            return Status;
        }
    }

    I2C_GenerateSTOP(I2Cx, ENABLE);
    return MYI2C_OK;
}

/**
  * @brief  从设备连续读取多个字节。
  */
MyI2C_StatusTypeDef MyI2C_ReadBytes(I2C_TypeDef *I2Cx, uint8_t DevAddress7Bit, uint8_t *Data, uint16_t Length)
{
    MyI2C_StatusTypeDef Status;
    uint16_t Index;

    if ((MyI2C_GetConfig(I2Cx) == 0) || (Data == 0))
    {
        return MYI2C_INVALID_PARAM;
    }

    if (Length == 0)
    {
        return MYI2C_OK;
    }

    Status = MyI2C_WaitBusFree(I2Cx);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    I2C_AcknowledgeConfig(I2Cx, ENABLE);
    I2C_GenerateSTART(I2Cx, ENABLE);
    Status = MyI2C_WaitEvent(I2Cx, I2C_EVENT_MASTER_MODE_SELECT);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    I2C_Send7bitAddress(I2Cx, MYI2C_7BIT_ADDR_TO_8BIT(DevAddress7Bit), I2C_Direction_Receiver);
    Status = MyI2C_WaitEvent(I2Cx, I2C_EVENT_MASTER_RECEIVER_MODE_SELECTED);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    for (Index = 0; Index < Length; Index++)
    {
        if (Index == (uint16_t)(Length - 1))
        {
            I2C_AcknowledgeConfig(I2Cx, DISABLE);
            I2C_GenerateSTOP(I2Cx, ENABLE);
        }

        Status = MyI2C_WaitFlag(I2Cx, I2C_FLAG_RXNE, SET);
        if (Status != MYI2C_OK)
        {
            I2C_AcknowledgeConfig(I2Cx, ENABLE);
            return Status;
        }

        Data[Index] = I2C_ReceiveData(I2Cx);
    }

    I2C_AcknowledgeConfig(I2Cx, ENABLE);
    return MYI2C_OK;
}

/**
  * @brief  向设备寄存器写入多个字节。
  */
MyI2C_StatusTypeDef MyI2C_WriteMem(I2C_TypeDef *I2Cx, uint8_t DevAddress7Bit, uint8_t MemAddress, const uint8_t *Data, uint16_t Length)
{
    MyI2C_StatusTypeDef Status;
    uint16_t Index;

    if ((MyI2C_GetConfig(I2Cx) == 0) || ((Length > 0) && (Data == 0)))
    {
        return MYI2C_INVALID_PARAM;
    }

    Status = MyI2C_WaitBusFree(I2Cx);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    I2C_GenerateSTART(I2Cx, ENABLE);
    Status = MyI2C_WaitEvent(I2Cx, I2C_EVENT_MASTER_MODE_SELECT);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    I2C_Send7bitAddress(I2Cx, MYI2C_7BIT_ADDR_TO_8BIT(DevAddress7Bit), I2C_Direction_Transmitter);
    Status = MyI2C_WaitEvent(I2Cx, I2C_EVENT_MASTER_TRANSMITTER_MODE_SELECTED);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    I2C_SendData(I2Cx, MemAddress);
    Status = MyI2C_WaitEvent(I2Cx, I2C_EVENT_MASTER_BYTE_TRANSMITTED);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    for (Index = 0; Index < Length; Index++)
    {
        I2C_SendData(I2Cx, Data[Index]);
        Status = MyI2C_WaitEvent(I2Cx, I2C_EVENT_MASTER_BYTE_TRANSMITTED);
        if (Status != MYI2C_OK)
        {
            return Status;
        }
    }

    I2C_GenerateSTOP(I2Cx, ENABLE);
    return MYI2C_OK;
}

/**
  * @brief  从设备寄存器读取多个字节。
  */
MyI2C_StatusTypeDef MyI2C_ReadMem(I2C_TypeDef *I2Cx, uint8_t DevAddress7Bit, uint8_t MemAddress, uint8_t *Data, uint16_t Length)
{
    MyI2C_StatusTypeDef Status;
    uint16_t Index;

    if ((MyI2C_GetConfig(I2Cx) == 0) || (Data == 0))
    {
        return MYI2C_INVALID_PARAM;
    }

    if (Length == 0)
    {
        return MYI2C_OK;
    }

    Status = MyI2C_WaitBusFree(I2Cx);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    I2C_GenerateSTART(I2Cx, ENABLE);
    Status = MyI2C_WaitEvent(I2Cx, I2C_EVENT_MASTER_MODE_SELECT);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    I2C_Send7bitAddress(I2Cx, MYI2C_7BIT_ADDR_TO_8BIT(DevAddress7Bit), I2C_Direction_Transmitter);
    Status = MyI2C_WaitEvent(I2Cx, I2C_EVENT_MASTER_TRANSMITTER_MODE_SELECTED);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    I2C_SendData(I2Cx, MemAddress);
    Status = MyI2C_WaitEvent(I2Cx, I2C_EVENT_MASTER_BYTE_TRANSMITTED);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    I2C_AcknowledgeConfig(I2Cx, ENABLE);
    I2C_GenerateSTART(I2Cx, ENABLE);
    Status = MyI2C_WaitEvent(I2Cx, I2C_EVENT_MASTER_MODE_SELECT);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    I2C_Send7bitAddress(I2Cx, MYI2C_7BIT_ADDR_TO_8BIT(DevAddress7Bit), I2C_Direction_Receiver);
    Status = MyI2C_WaitEvent(I2Cx, I2C_EVENT_MASTER_RECEIVER_MODE_SELECTED);
    if (Status != MYI2C_OK)
    {
        return Status;
    }

    for (Index = 0; Index < Length; Index++)
    {
        if (Index == (uint16_t)(Length - 1))
        {
            I2C_AcknowledgeConfig(I2Cx, DISABLE);
            I2C_GenerateSTOP(I2Cx, ENABLE);
        }

        Status = MyI2C_WaitFlag(I2Cx, I2C_FLAG_RXNE, SET);
        if (Status != MYI2C_OK)
        {
            I2C_AcknowledgeConfig(I2Cx, ENABLE);
            return Status;
        }

        Data[Index] = I2C_ReceiveData(I2Cx);
    }

    I2C_AcknowledgeConfig(I2Cx, ENABLE);
    return MYI2C_OK;
}

/**
  * @brief  扫描当前 I2C 总线上的设备。
  */
uint8_t MyI2C_Scan(I2C_TypeDef *I2Cx, uint8_t *FoundAddress, uint8_t MaxCount)
{
    uint8_t Address;
    uint8_t Count;

    Count = 0;
    for (Address = 1; Address < 0x7F; Address++)
    {
        if (MyI2C_IsDeviceReady(I2Cx, Address) == MYI2C_OK)
        {
            if ((FoundAddress != 0) && (Count < MaxCount))
            {
                FoundAddress[Count] = Address;
            }
            Count++;
        }
    }

    return Count;
}

#if MYI2C1_ENABLE
void MyI2C1_Init(void)
{
    MyI2C_Init(MYI2C1_I2C);
}

void MyI2C1_DeInit(void)
{
    MyI2C_DeInit(MYI2C1_I2C);
}

MyI2C_StatusTypeDef MyI2C1_IsDeviceReady(uint8_t DevAddress7Bit)
{
    return MyI2C_IsDeviceReady(MYI2C1_I2C, DevAddress7Bit);
}

MyI2C_StatusTypeDef MyI2C1_WriteBytes(uint8_t DevAddress7Bit, const uint8_t *Data, uint16_t Length)
{
    return MyI2C_WriteBytes(MYI2C1_I2C, DevAddress7Bit, Data, Length);
}

MyI2C_StatusTypeDef MyI2C1_ReadBytes(uint8_t DevAddress7Bit, uint8_t *Data, uint16_t Length)
{
    return MyI2C_ReadBytes(MYI2C1_I2C, DevAddress7Bit, Data, Length);
}

MyI2C_StatusTypeDef MyI2C1_WriteMem(uint8_t DevAddress7Bit, uint8_t MemAddress, const uint8_t *Data, uint16_t Length)
{
    return MyI2C_WriteMem(MYI2C1_I2C, DevAddress7Bit, MemAddress, Data, Length);
}

MyI2C_StatusTypeDef MyI2C1_ReadMem(uint8_t DevAddress7Bit, uint8_t MemAddress, uint8_t *Data, uint16_t Length)
{
    return MyI2C_ReadMem(MYI2C1_I2C, DevAddress7Bit, MemAddress, Data, Length);
}

uint8_t MyI2C1_Scan(uint8_t *FoundAddress, uint8_t MaxCount)
{
    return MyI2C_Scan(MYI2C1_I2C, FoundAddress, MaxCount);
}
#endif

#if MYI2C2_ENABLE
void MyI2C2_Init(void)
{
    MyI2C_Init(MYI2C2_I2C);
}

void MyI2C2_DeInit(void)
{
    MyI2C_DeInit(MYI2C2_I2C);
}

MyI2C_StatusTypeDef MyI2C2_IsDeviceReady(uint8_t DevAddress7Bit)
{
    return MyI2C_IsDeviceReady(MYI2C2_I2C, DevAddress7Bit);
}

MyI2C_StatusTypeDef MyI2C2_WriteBytes(uint8_t DevAddress7Bit, const uint8_t *Data, uint16_t Length)
{
    return MyI2C_WriteBytes(MYI2C2_I2C, DevAddress7Bit, Data, Length);
}

MyI2C_StatusTypeDef MyI2C2_ReadBytes(uint8_t DevAddress7Bit, uint8_t *Data, uint16_t Length)
{
    return MyI2C_ReadBytes(MYI2C2_I2C, DevAddress7Bit, Data, Length);
}

MyI2C_StatusTypeDef MyI2C2_WriteMem(uint8_t DevAddress7Bit, uint8_t MemAddress, const uint8_t *Data, uint16_t Length)
{
    return MyI2C_WriteMem(MYI2C2_I2C, DevAddress7Bit, MemAddress, Data, Length);
}

MyI2C_StatusTypeDef MyI2C2_ReadMem(uint8_t DevAddress7Bit, uint8_t MemAddress, uint8_t *Data, uint16_t Length)
{
    return MyI2C_ReadMem(MYI2C2_I2C, DevAddress7Bit, MemAddress, Data, Length);
}

uint8_t MyI2C2_Scan(uint8_t *FoundAddress, uint8_t MaxCount)
{
    return MyI2C_Scan(MYI2C2_I2C, FoundAddress, MaxCount);
}
#endif

#endif /* MYI2C_ANY_ENABLE 配置结束 */
