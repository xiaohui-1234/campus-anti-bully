/**
  ******************************************************************************
  * @file    Project/STM32F10x_StdPeriph_Template/stm32f10x_it.c 
  * @author  MCD Application Team
  * @version V3.5.0
  * @date    08-April-2011
  * @brief   Main Interrupt Service Routines.
  *          This file provides template for all exceptions handler and 
  *          peripherals interrupt service routine.
  ******************************************************************************
  * @attention
  *
  * THE PRESENT FIRMWARE WHICH IS FOR GUIDANCE ONLY AIMS AT PROVIDING CUSTOMERS
  * WITH CODING INFORMATION REGARDING THEIR PRODUCTS IN ORDER FOR THEM TO SAVE
  * TIME. AS A RESULT, STMICROELECTRONICS SHALL NOT BE HELD LIABLE FOR ANY
  * DIRECT, INDIRECT OR CONSEQUENTIAL DAMAGES WITH RESPECT TO ANY CLAIMS ARISING
  * FROM THE CONTENT OF SUCH FIRMWARE AND/OR THE USE MADE BY CUSTOMERS OF THE
  * CODING INFORMATION CONTAINED HEREIN IN CONNECTION WITH THEIR PRODUCTS.
  *
  * <h2><center>&copy; COPYRIGHT 2011 STMicroelectronics</center></h2>
  ******************************************************************************
  */

/* Includes ------------------------------------------------------------------*/
#include "stm32f10x_it.h"
#include <string.h> 
#include "Hardware/usart.h"
#include "Hardware/esp8266.h"
#include "Time.h"
 #include "Library/stm32f10x_dma.h"
extern volatile uint8_t flag;
extern volatile uint8_t g_PassthroughMode;
 extern volatile uint8_t g_AudioHalfReady;
 extern volatile uint8_t g_AudioFullReady;

/** @addtogroup STM32F10x_StdPeriph_Template
  * @{
  */

/* Private typedef -----------------------------------------------------------*/
/* Private define ------------------------------------------------------------*/
/* Private macro -------------------------------------------------------------*/
/* Private variables ---------------------------------------------------------*/
/* Private function prototypes -----------------------------------------------*/
/* Private functions ---------------------------------------------------------*/

/******************************************************************************/
/*            Cortex-M3 Processor Exceptions Handlers                         */
/******************************************************************************/

/**
  * @brief  This function handles NMI exception.
  * @param  None
  * @retval None
  */
void NMI_Handler(void)
{
}

/**
  * @brief  This function handles Hard Fault exception.
  * @param  None
  * @retval None
  */
void HardFault_Handler(void)
{
  /* Go to infinite loop when Hard Fault exception occurs */
  while (1)
  {
  }
}

/**
  * @brief  This function handles Memory Manage exception.
  * @param  None
  * @retval None
  */
void MemManage_Handler(void)
{
  /* Go to infinite loop when Memory Manage exception occurs */
  while (1)
  {
  }
}

/**
  * @brief  This function handles Bus Fault exception.
  * @param  None
  * @retval None
  */
void BusFault_Handler(void)
{
  /* Go to infinite loop when Bus Fault exception occurs */
  while (1)
  {
  }
}

/**
  * @brief  This function handles Usage Fault exception.
  * @param  None
  * @retval None
  */
void UsageFault_Handler(void)
{
  /* Go to infinite loop when Usage Fault exception occurs */
  while (1)
  {
  }
}

/**
  * @brief  This function handles SVCall exception.
  * @param  None
  * @retval None
  */
void SVC_Handler(void)
{
}

/**
  * @brief  This function handles Debug Monitor exception.
  * @param  None
  * @retval None
  */
void DebugMon_Handler(void)
{
}

/**
  * @brief  This function handles PendSVC exception.
  * @param  None
  * @retval None
  */
void PendSV_Handler(void)
{
}

/**
  * @brief  This function handles SysTick Handler.
  * @param  None
  * @retval None
  */
void SysTick_Handler(void)
{
}
/*串口中断服务函数*/


void DMA1_Channel1_IRQHandler(void)
{
	if (DMA_GetITStatus(DMA1_IT_HT1) != RESET)
	{
		DMA_ClearITPendingBit(DMA1_IT_HT1);
		g_AudioHalfReady = 1;
	}
	if (DMA_GetITStatus(DMA1_IT_TC1) != RESET)
	{
		DMA_ClearITPendingBit(DMA1_IT_TC1);
		g_AudioFullReady = 1;
	}
}


void DEBUG_USART_IRQHandler(void)//串口1中断服务函数
{ 
  uint8_t ucTemp;
	if(USART_GetITStatus(USART1,USART_IT_RXNE)!=RESET)
	{		
		ucTemp = USART_ReceiveData(USART1);
    if (g_PassthroughMode)
    {
    	USART_SendData(macESP8266_USARTx, ucTemp);
    }
    else
    {
    	USART_SendData(USART1,ucTemp);
    }
	}	     
}


void ProcessMQTTSubRecv(void);

void macESP8266_USART_INT_FUN ( void )//串口2中断服务函数（ESP8266接收中断�?
{	
  uint8_t ucCh;
  //                                         接收数据寄存器非空中断标�?
  //逐字接收数据 把数据存到缓存区
	if ( USART_GetITStatus ( macESP8266_USARTx, USART_IT_RXNE ) != RESET )
	{
    //接收来自ESP8266的反馈消�?
		ucCh  = USART_ReceiveData( macESP8266_USARTx );
    //将反馈信息通过串口1发送出�?
		USART_SendData(USART1, ucCh);
    //Data_RX_BUF是数�?
		if ( strEsp8266_Fram_Record .InfBit .FramLength >= ( RX_BUF_MAX_LEN - 1 ) )
		{
			memmove(strEsp8266_Fram_Record.Data_RX_BUF,
				strEsp8266_Fram_Record.Data_RX_BUF + (RX_BUF_MAX_LEN / 2),
				RX_BUF_MAX_LEN / 2);
			strEsp8266_Fram_Record.InfBit.FramLength = RX_BUF_MAX_LEN / 2;
		}
		strEsp8266_Fram_Record .Data_RX_BUF [ strEsp8266_Fram_Record .InfBit .FramLength ++ ]  = ucCh;
  }
	  	 //串口�?3.5 个字符周期内没有新数据，触发USART_IT_IDLE中断
	if ( USART_GetITStatus( macESP8266_USARTx, USART_IT_IDLE ) == SET )                                         //数据帧接收完�?
	{
    //标记接收完成
    strEsp8266_Fram_Record .InfBit .FramFinishFlag = 1;
		
		ucCh = USART_ReceiveData( macESP8266_USARTx );            //由软件序列清除中断标志位(先读USART_SR，然后读USART_DR)
		 // 给接收缓存加字符串结束符，把数组变成C语言可识别的字符�?
		if ( strEsp8266_Fram_Record .InfBit .FramLength < RX_BUF_MAX_LEN )
			strEsp8266_Fram_Record .Data_RX_BUF [ strEsp8266_Fram_Record .InfBit .FramLength ] = '\0';
		// 非透传模式下，解析ESP8266的网络数据（+IPD指令�?
		if ( ( g_PassthroughMode == 0 ) && ( g_ESP8266RawBusy == 0 ) )
		{
			char * pIpd;
			char * pLastIpd;
			char * pData;
      //将数组Data_RX_BUF给到pIpd
			pIpd = strEsp8266_Fram_Record .Data_RX_BUF;
			pLastIpd = 0;
      // 找最后一�?+IPD"的位置（因为可能一帧里有多�?IPD指令�?
			while ( ( pIpd = strstr ( pIpd, "+IPD" ) ) != 0 )
			{
				pLastIpd = pIpd;//指后最新的+IPD
				pIpd += 4;
			}
			if ( pLastIpd )
			{
        // 找到"+IPD"后面的冒号（分隔长度和数据）
				pData = strchr ( pLastIpd, ':' );
        // 如果冒号后面有数据，就取第一个字节给flag
				if ( pData && ( *( pData + 1 ) != '\0' ) )
					flag = *( pData + 1 );
				strEsp8266_Fram_Record .InfBit .FramLength = 0;
			}
		}
		// 处理MQTT订阅消息
		if ( ( g_ESP8266RawBusy == 0 ) && strstr(strEsp8266_Fram_Record.Data_RX_BUF, "+MQTTSUBRECV") )
		{
			uint16_t oldLen = 0;
			uint16_t copyLen = strEsp8266_Fram_Record.InfBit.FramLength;
			if (g_MqttSubRecvPending) {
				oldLen = (uint16_t)strlen(g_MqttSubRecvBuf);
			}
			if ((oldLen + copyLen) >= RX_BUF_MAX_LEN) {
				oldLen = 0;
			}
			memcpy(g_MqttSubRecvBuf + oldLen, strEsp8266_Fram_Record.Data_RX_BUF, copyLen);
			g_MqttSubRecvBuf[oldLen + copyLen] = '\0';
			g_MqttSubRecvPending = 1;
			strEsp8266_Fram_Record.InfBit.FramLength = 0;
		}
		if ( strstr ( strEsp8266_Fram_Record.Data_RX_BUF, "+MQTTCONNECTED" ) )
		{
			g_MqttConnected = 1;
		}
		if ( strstr ( strEsp8266_Fram_Record.Data_RX_BUF, "+MQTTDISCONNECTED" ) )
		{
			g_MqttConnected = 0;
		}
  		ucTcpClosedFlag = strstr ( strEsp8266_Fram_Record .Data_RX_BUF, "CLOSED\r\n" ) ? 1 : 0;
  		
  }	
	//Usart_SendString( USART1, q);
	
}


/******************************************************************************/
/*                 STM32F10x Peripherals Interrupt Handlers                   */
/*  Add here the Interrupt Handler for the used peripheral(s) (PPP), for the  */
/*  available peripheral interrupt handler's name please refer to the startup */
/*  file (startup_stm32f10x_xx.s).                                            */
/******************************************************************************/

/**
  * @brief  This function handles PPP interrupt request.
  * @param  None
  * @retval None
  */
/*void PPP_IRQHandler(void)
{
}*/

/**
  * @}
  */ 


/******************* (C) COPYRIGHT 2011 STMicroelectronics *****END OF FILE****/
