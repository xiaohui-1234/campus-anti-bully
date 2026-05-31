#include "stm32f10x.h"
#include "System/Delay.h"
#include "System/bsp_timer.h"
#include "Hardware/Serial.h"
#include "Hardware/esp8266.h"
#include "Hardware/campus_config.h"
#include "Hardware/campus_mqtt.h"
#include "Hardware/usart.h"
#include "Hardware/Key.h"
#include "Hardware/LED.h"
#include "Hardware/tf_card.h"
#include "Alarm/alarm.h"
#include "Time.h"
#include "Library/stm32f10x_adc.h"
#include "Library/stm32f10x_dma.h"
#include "Library/stm32f10x_tim.h"
#include "Library/misc.h"
#include <stdio.h>
#include <string.h>

volatile uint8_t flag;
uint8_t g_PassthroughMode = 0;
char uart_buffer[20];
uint8_t uart_index = 0;

#define AUDIO_SAMPLE_RATE_HZ        8000U
#define AUDIO_DMA_BUF_SIZE          800U
#define AUDIO_DMA_HALF_SIZE         (AUDIO_DMA_BUF_SIZE / 2U)
#define AUDIO_RECORD_SECONDS        15U
#define AUDIO_WAV_BYTES_TARGET      (AUDIO_SAMPLE_RATE_HZ * AUDIO_RECORD_SECONDS * 2U)

volatile uint8_t g_AudioHalfReady = 0;
volatile uint8_t g_AudioFullReady = 0;
uint16_t g_AudioAdcBuf[AUDIO_DMA_BUF_SIZE];

static uint8_t g_WavRecording = 0;
static uint32_t g_WavDataBytes = 0;
static FIL g_WavFile;
static char g_WavPath[64];
static uint8_t g_AudioCaptureRunning = 0;

static uint8_t App_TimeDue(uint32_t nowMs, uint32_t dueMs);
static void Audio_Capture_Start(void);
static void Audio_Capture_Stop(void);
static void Audio_ToWav_WriteHalf(uint16_t startIndex);
static void Wav_Start(void);
static void Wav_Stop(void);
static FRESULT Wav_WriteHeader(FIL *fp, uint32_t dataBytes);
static void ADC1_CH1_Init(void);
static void TIM3_TRGO_Init(uint32_t sampleRateHz);
static void DMA1_Channel1_ADC1_Init(uint16_t *buf, uint16_t size);

static uint8_t App_TimeDue(uint32_t nowMs, uint32_t dueMs)
{
    return ((int32_t)(nowMs - dueMs) >= 0) ? 1U : 0U;
}

static FRESULT Wav_WriteHeader(FIL *fp, uint32_t dataBytes)
{
    uint8_t hdr[44];
    uint32_t riffSize;
    uint32_t byteRate;
    uint16_t blockAlign;
    UINT bw;
    FRESULT fr;

    riffSize = dataBytes + 36U;
    byteRate = AUDIO_SAMPLE_RATE_HZ * 2U;
    blockAlign = 2U;

    hdr[0] = 'R'; hdr[1] = 'I'; hdr[2] = 'F'; hdr[3] = 'F';
    hdr[4] = (uint8_t)riffSize;
    hdr[5] = (uint8_t)(riffSize >> 8);
    hdr[6] = (uint8_t)(riffSize >> 16);
    hdr[7] = (uint8_t)(riffSize >> 24);
    hdr[8] = 'W'; hdr[9] = 'A'; hdr[10] = 'V'; hdr[11] = 'E';
    hdr[12] = 'f'; hdr[13] = 'm'; hdr[14] = 't'; hdr[15] = ' ';
    hdr[16] = 16; hdr[17] = 0; hdr[18] = 0; hdr[19] = 0;
    hdr[20] = 1; hdr[21] = 0;
    hdr[22] = 1; hdr[23] = 0;
    hdr[24] = (uint8_t)AUDIO_SAMPLE_RATE_HZ;
    hdr[25] = (uint8_t)(AUDIO_SAMPLE_RATE_HZ >> 8);
    hdr[26] = (uint8_t)(AUDIO_SAMPLE_RATE_HZ >> 16);
    hdr[27] = (uint8_t)(AUDIO_SAMPLE_RATE_HZ >> 24);
    hdr[28] = (uint8_t)byteRate;
    hdr[29] = (uint8_t)(byteRate >> 8);
    hdr[30] = (uint8_t)(byteRate >> 16);
    hdr[31] = (uint8_t)(byteRate >> 24);
    hdr[32] = (uint8_t)blockAlign;
    hdr[33] = (uint8_t)(blockAlign >> 8);
    hdr[34] = 16; hdr[35] = 0;
    hdr[36] = 'd'; hdr[37] = 'a'; hdr[38] = 't'; hdr[39] = 'a';
    hdr[40] = (uint8_t)dataBytes;
    hdr[41] = (uint8_t)(dataBytes >> 8);
    hdr[42] = (uint8_t)(dataBytes >> 16);
    hdr[43] = (uint8_t)(dataBytes >> 24);

    fr = f_lseek(fp, 0);
    if (fr != FR_OK) {
        printf("WAV header seek fail fr=%d\r\n", (int)fr);
        return fr;
    }

    fr = f_write(fp, hdr, sizeof(hdr), &bw);
    if (fr != FR_OK || bw != sizeof(hdr)) {
        printf("WAV header write fail fr=%d, bw=%u\r\n", (int)fr, (unsigned)bw);
        return (fr != FR_OK) ? fr : FR_DISK_ERR;
    }

    return FR_OK;
}

static void Wav_Stop(void)
{
    FRESULT fr;

    if (!g_WavRecording) {
        return;
    }

    g_WavRecording = 0;
    Audio_Capture_Stop();

    fr = Wav_WriteHeader(&g_WavFile, g_WavDataBytes);
    if (fr != FR_OK) {
        printf("WAV finalize header fail fr=%d\r\n", (int)fr);
    }
    (void)f_sync(&g_WavFile);
    (void)f_close(&g_WavFile);

    printf("WAV saved, bytes=%lu\r\n", (unsigned long)g_WavDataBytes);
    if (fr == FR_OK) {
        if (!Campus_MQTT_PostAlarmForFile(g_WavPath, g_WavDataBytes)) {
            printf("alarm/post wait upload url fail\r\n");
            LED0_OFF();
        }
    } else {
        LED0_OFF();
    }
}

static void Wav_Start(void)
{
    FRESULT fr;
    FRESULT fr2;
    uint8_t tryCount;
    uint8_t headerTry;

    if (g_WavRecording) {
        return;
    }

    printf("WAV prepare\r\n");
    fr = TF_Card_Mount();
    if (fr != FR_OK) {
        printf("TF mount fail fr=%d\r\n", (int)fr);
        return;
    }
    printf("TF mounted\r\n");

    fr = FR_INVALID_NAME;
    for (tryCount = 0; tryCount < 10U; tryCount++) {
        if (!Time_GenerateRecordPath(g_WavPath, sizeof(g_WavPath))) {
            printf("WAV path gen fail\r\n");
            return;
        }

        fr = f_open(&g_WavFile, g_WavPath, FA_CREATE_NEW | FA_WRITE);
        if (fr == FR_OK) {
            break;
        }
        printf("WAV open try %u fail fr=%d\r\n", (unsigned)tryCount, (int)fr);
        if (fr != FR_EXIST) {
            break;
        }
    }
    if (fr != FR_OK) {
        printf("WAV open fail fr=%d\r\n", (int)fr);
        return;
    }

    for (headerTry = 0; headerTry < 3U; headerTry++) {
        fr = Wav_WriteHeader(&g_WavFile, 0);
        if (fr == FR_OK) {
            break;
        }
        (void)f_sync(&g_WavFile);
        Delay_ms(100);
    }
    if (fr != FR_OK) {
        printf("WAV initial header fail fr=%d\r\n", (int)fr);
        (void)f_close(&g_WavFile);
        (void)TF_Card_Unmount();
        return;
    }

    fr2 = f_lseek(&g_WavFile, 44);
    if (fr2 != FR_OK) {
        printf("WAV seek fail fr=%d\r\n", (int)fr2);
        (void)f_close(&g_WavFile);
        return;
    }

    g_WavDataBytes = 0;
    g_WavRecording = 1;
    Audio_Capture_Start();
    printf("WAV rec start: %s\r\n", g_WavPath);
}

uint8_t App_IsRecording(void)
{
    return g_WavRecording;
}

uint8_t App_TryStartAlarmRecording(void)
{
    if (g_WavRecording) {
        return 0;
    }

    Wav_Start();
    return g_WavRecording;
}

static void ADC1_CH1_Init(void)
{
    GPIO_InitTypeDef GPIO_InitStructure;
    ADC_InitTypeDef ADC_InitStructure;

    RCC_ADCCLKConfig(RCC_PCLK2_Div6);
    RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOA | RCC_APB2Periph_ADC1, ENABLE);

    GPIO_InitStructure.GPIO_Mode = GPIO_Mode_AIN;
    GPIO_InitStructure.GPIO_Pin = GPIO_Pin_1;
    GPIO_Init(GPIOA, &GPIO_InitStructure);

    ADC_DeInit(ADC1);
    ADC_InitStructure.ADC_Mode = ADC_Mode_Independent;
    ADC_InitStructure.ADC_ScanConvMode = DISABLE;
    ADC_InitStructure.ADC_ContinuousConvMode = DISABLE;
    ADC_InitStructure.ADC_ExternalTrigConv = ADC_ExternalTrigConv_T3_TRGO;
    ADC_InitStructure.ADC_DataAlign = ADC_DataAlign_Right;
    ADC_InitStructure.ADC_NbrOfChannel = 1;
    ADC_Init(ADC1, &ADC_InitStructure);

    ADC_RegularChannelConfig(ADC1, ADC_Channel_1, 1, ADC_SampleTime_239Cycles5);
    ADC_DMACmd(ADC1, ENABLE);
    ADC_ExternalTrigConvCmd(ADC1, ENABLE);
    ADC_Cmd(ADC1, ENABLE);

    Delay_ms(2);
    ADC_ResetCalibration(ADC1);
    while (ADC_GetResetCalibrationStatus(ADC1));
    ADC_StartCalibration(ADC1);
    while (ADC_GetCalibrationStatus(ADC1));
}

static void TIM3_TRGO_Init(uint32_t sampleRateHz)
{
    uint32_t timerClockHz;
    uint16_t prescaler;
    uint16_t period;
    TIM_TimeBaseInitTypeDef TIM_TimeBaseStructure;

    RCC_APB1PeriphClockCmd(RCC_APB1Periph_TIM3, ENABLE);

    timerClockHz = 1000000U;
    prescaler = (uint16_t)(SystemCoreClock / timerClockHz) - 1U;
    period = (uint16_t)((timerClockHz / sampleRateHz) - 1U);

    TIM_TimeBaseStructure.TIM_ClockDivision = TIM_CKD_DIV1;
    TIM_TimeBaseStructure.TIM_CounterMode = TIM_CounterMode_Up;
    TIM_TimeBaseStructure.TIM_Prescaler = prescaler;
    TIM_TimeBaseStructure.TIM_Period = period;
    TIM_TimeBaseStructure.TIM_RepetitionCounter = 0;
    TIM_TimeBaseInit(TIM3, &TIM_TimeBaseStructure);

    TIM_SelectOutputTrigger(TIM3, TIM_TRGOSource_Update);
    TIM_Cmd(TIM3, ENABLE);
}

static void DMA1_Channel1_ADC1_Init(uint16_t *buf, uint16_t size)
{
    DMA_InitTypeDef DMA_InitStructure;
    NVIC_InitTypeDef NVIC_InitStructure;

    RCC_AHBPeriphClockCmd(RCC_AHBPeriph_DMA1, ENABLE);

    DMA_DeInit(DMA1_Channel1);
    DMA_InitStructure.DMA_PeripheralBaseAddr = (uint32_t)&ADC1->DR;
    DMA_InitStructure.DMA_MemoryBaseAddr = (uint32_t)buf;
    DMA_InitStructure.DMA_DIR = DMA_DIR_PeripheralSRC;
    DMA_InitStructure.DMA_BufferSize = size;
    DMA_InitStructure.DMA_PeripheralInc = DMA_PeripheralInc_Disable;
    DMA_InitStructure.DMA_MemoryInc = DMA_MemoryInc_Enable;
    DMA_InitStructure.DMA_PeripheralDataSize = DMA_PeripheralDataSize_HalfWord;
    DMA_InitStructure.DMA_MemoryDataSize = DMA_MemoryDataSize_HalfWord;
    DMA_InitStructure.DMA_Mode = DMA_Mode_Circular;
    DMA_InitStructure.DMA_Priority = DMA_Priority_High;
    DMA_InitStructure.DMA_M2M = DMA_M2M_Disable;
    DMA_Init(DMA1_Channel1, &DMA_InitStructure);

    DMA_ITConfig(DMA1_Channel1, DMA_IT_HT, ENABLE);
    DMA_ITConfig(DMA1_Channel1, DMA_IT_TC, ENABLE);

    NVIC_InitStructure.NVIC_IRQChannel = DMA1_Channel1_IRQn;
    NVIC_InitStructure.NVIC_IRQChannelPreemptionPriority = 1;
    NVIC_InitStructure.NVIC_IRQChannelSubPriority = 1;
    NVIC_InitStructure.NVIC_IRQChannelCmd = ENABLE;
    NVIC_Init(&NVIC_InitStructure);

    DMA_Cmd(DMA1_Channel1, ENABLE);
}

static void Audio_Capture_Start(void)
{
    if (g_AudioCaptureRunning) {
        return;
    }

    g_AudioHalfReady = 0;
    g_AudioFullReady = 0;
    DMA1_Channel1_ADC1_Init(g_AudioAdcBuf, AUDIO_DMA_BUF_SIZE);
    ADC1_CH1_Init();
    TIM3_TRGO_Init(AUDIO_SAMPLE_RATE_HZ);
    g_AudioCaptureRunning = 1;
}

static void Audio_Capture_Stop(void)
{
    if (!g_AudioCaptureRunning) {
        return;
    }

    TIM_Cmd(TIM3, DISABLE);
    ADC_ExternalTrigConvCmd(ADC1, DISABLE);
    ADC_DMACmd(ADC1, DISABLE);
    DMA_Cmd(DMA1_Channel1, DISABLE);
    ADC_Cmd(ADC1, DISABLE);
    DMA_ClearITPendingBit(DMA1_IT_HT1 | DMA1_IT_TC1);
    g_AudioHalfReady = 0;
    g_AudioFullReady = 0;
    g_AudioCaptureRunning = 0;
}

static void Audio_ToWav_WriteHalf(uint16_t startIndex)
{
    FRESULT fr;
    UINT bw;
    uint16_t i;
    int16_t pcm[AUDIO_DMA_HALF_SIZE];

    if (!g_WavRecording) {
        return;
    }

    if (g_WavDataBytes >= AUDIO_WAV_BYTES_TARGET) {
        Wav_Stop();
        return;
    }

    for (i = 0; i < AUDIO_DMA_HALF_SIZE; i++) {
        int32_t x = (int32_t)g_AudioAdcBuf[startIndex + i] - 2048;
        x <<= 4;
        if (x > 32767) x = 32767;
        if (x < -32768) x = -32768;
        pcm[i] = (int16_t)x;
    }

    fr = f_write(&g_WavFile, (const void *)pcm, (UINT)(AUDIO_DMA_HALF_SIZE * 2U), &bw);
    if (fr != FR_OK || bw != (UINT)(AUDIO_DMA_HALF_SIZE * 2U)) {
        printf("WAV write fail fr=%d, bw=%u\r\n", (int)fr, (unsigned)bw);
        Wav_Stop();
        return;
    }

    g_WavDataBytes += (uint32_t)bw;
    if (g_WavDataBytes >= AUDIO_WAV_BYTES_TARGET) {
        Wav_Stop();
    }
}

void Open_Penetmode(uint8_t key)
{
    if ((key == 1U) && (g_PassthroughMode == 0U)) {
        g_PassthroughMode = 1U;
        printf("Enter AT passthrough mode\r\n");
    }
}

void Get_STM32_UID(char *uid_str)
{
    uint32_t uid[3];

    uid[0] = *(uint32_t *)0x1FFFF7E8;
    uid[1] = *(uint32_t *)0x1FFFF7EC;
    uid[2] = *(uint32_t *)0x1FFFF7F0;
    sprintf(uid_str, "%08lX%08lX%08lX", uid[0], uid[1], uid[2]);
}

int main(void)
{
    char uid_str[25];
    uint32_t nextNtpSyncMs;
    uint32_t nextMqttReconnectMs;

    // 1. 初始化本地外设：语音串口、系统定时、LED、按键和调试串口。
    Serial_Init();						//语音串口  初始化PB10 PB11 9600  将接收到的信息通过串口发送stm32上
    GENERAL_TIM_Init();
    LED_Init();
    Key_Init();
    USART_Config();						//设置串口中断 打印wifi收发信息 占用PA9 PA10 115200 将接收到的信息通过串口发送stm32上

    // 2. 读取芯片唯一ID，作为设备身份相关信息输出到调试串口。
    Get_STM32_UID(uid_str);
    printf("USART1 OK\r\n");
    printf("STM32 UID: %s\r\n", uid_str);

    // 3. 初始化ESP8266，并尝试接入WiFi、建立MQTT连接。
    ESP8266_Init();						 //设置通信串口 初始化WiFi模块使用的接口和外设 PA2  PA3 115200  
    ESP8266_StaTcpClient();			   	//WiFi模块设置   让ESP8266可以通过wifi访问外网
	
    // 4. 设置后续NTP同步和MQTT重连的首次触发时间。
    nextNtpSyncMs = App_Millis() + (Time_IsValid() ? CAMPUS_NTP_SYNC_INTERVAL_MS : CAMPUS_NTP_RETRY_MS);
    nextMqttReconnectMs = App_Millis() + 10000UL;
	
    // 5. 给语音模块发送测试字符串，确认语音串口链路可用。
    Serial_SendString("test");			//语音串口发送字符串

    while (1) {
        uint8_t key;
        uint8_t controlBlocked;

        // 6. 轮询按键；录音期间屏蔽按键控制，避免告警流程被重复触发。
        key = Key_GetNum();
        controlBlocked = App_IsRecording();
        if (!controlBlocked) {
            Open_Penetmode(key);	//开启串口1和2的穿透模式
            Alarm_ButtomDown(key);
        }

        // 7. 只有在非AT穿透模式下，才运行本机业务逻辑。
        if (g_PassthroughMode == 0U) {		//在没有进入穿透模式下
			
			// 8. 处理语音模块串口输入，按命令编号触发不同告警类型。
            if (USART_GetFlagStatus(USART3, USART_FLAG_RXNE) == SET) {
                uint8_t data;

                data = (uint8_t)USART_ReceiveData(USART3);
                if (App_IsRecording()) {
                    uart_index = 0;
                } else if (data == '\r' || data == '\n') {
                    if (uart_index > 0U) {
                        uart_buffer[uart_index] = '\0';
                        printf("Received command: %s\r\n", uart_buffer);

                        if (strcmp(uart_buffer, "1") == 0) LED0_ON();
                        else if (strcmp(uart_buffer, "2") == 0) LED0_OFF();
                        else if (strcmp(uart_buffer, "3") == 0) Alarm_Voice();
                        else if (strcmp(uart_buffer, "4") == 0) Alarm_Fire();
                        else if (strcmp(uart_buffer, "5") == 0) Alarm_Kill();
                        else if (strcmp(uart_buffer, "6") == 0) Alarm_Fight();
                        else if (strcmp(uart_buffer, "7") == 0) Alarm_Kidnap();
                        else if (strcmp(uart_buffer, "8") == 0) Alarm_Explosion();
                        else if (strcmp(uart_buffer, "9") == 0) Alarm_Blood();
                        else if (strcmp(uart_buffer, "10") == 0) Alarm_Faint();
                        else if (strcmp(uart_buffer, "11") == 0) Alarm_StopHit();
                        else if (strcmp(uart_buffer, "12") == 0) Alarm_Robbery();
                        else if (strcmp(uart_buffer, "13") == 0) Alarm_HelpMe();
                        else if (strcmp(uart_buffer, "14") == 0) Alarm_CallPeople();
                        else if (strcmp(uart_buffer, "15") == 0) Alarm_GroupFight();
                        else if (strcmp(uart_buffer, "16") == 0) Alarm_DontMove();
                        else if (strcmp(uart_buffer, "17") == 0) General_alarm();

                        uart_index = 0;
                    }
                } else {
                    if (uart_index < (sizeof(uart_buffer) - 1U)) {
                        uart_buffer[uart_index++] = (char)data;
                    } else {
                        uart_index = 0;
                    }
                }
            }

            // 9. 非录音期间处理远程控制、MQTT订阅消息、MQTT保活重连和NTP校时。
            if (!App_IsRecording()) {
                switch (flag) {
                    case 'a':
                        LED0_ON();
                        printf("LED turn ON\r\n");
                        flag = 0;
                        break;
                    case 'c':
                        LED0_OFF();
                        printf("LED turn OFF\r\n");
                        flag = 0;
                        break;
                    default:
                        break;
                }

                if (g_MqttSubRecvPending) {
                    ProcessMQTTSubRecv();
                }
                if ((g_MqttConnected == 0U) &&
                    (g_ESP8266RawBusy == 0U) &&
                    App_TimeDue(App_Millis(), nextMqttReconnectMs)) {
                    printf("MQTT reconnect start\r\n");
                    ESP8266_StaTcpClient();
                    nextMqttReconnectMs = App_Millis() + 10000UL;
                    nextNtpSyncMs = App_Millis() + (Time_IsValid() ? CAMPUS_NTP_SYNC_INTERVAL_MS : CAMPUS_NTP_RETRY_MS);
                }
                Campus_MQTT_Service(App_Millis());
                if (App_TimeDue(App_Millis(), nextNtpSyncMs) && g_ESP8266RawBusy == 0U) {
                    if (ESP8266_SyncTime()) {
                        nextNtpSyncMs = App_Millis() + CAMPUS_NTP_SYNC_INTERVAL_MS;
                    } else {
                        nextNtpSyncMs = App_Millis() + CAMPUS_NTP_RETRY_MS;
                    }
                }
            }

            // 10. 录音期间由DMA半满/全满标志驱动，把ADC采样分块写入WAV文件。
            if (g_AudioHalfReady) {
                g_AudioHalfReady = 0;
                Audio_ToWav_WriteHalf(0);
            }
            if (g_AudioFullReady) {
                g_AudioFullReady = 0;
                Audio_ToWav_WriteHalf(AUDIO_DMA_HALF_SIZE);
            }
        }
    }
}
