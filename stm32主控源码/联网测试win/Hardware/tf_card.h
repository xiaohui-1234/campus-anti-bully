#ifndef TF_CARD_H
#define TF_CARD_H

#include "stm32f10x.h"
#include "ff.h"

/*
 * TF 卡文件系统接口封装。
 * 本模块基于 FatFs，对外提供挂载、卸载和追加写入文件的简单接口。
 */

/* 挂载 TF 卡文件系统，成功或已挂载时返回 FR_OK。 */
FRESULT TF_Card_Mount(void);

/* 卸载 TF 卡文件系统，成功或未挂载时返回 FR_OK。 */
FRESULT TF_Card_Unmount(void);

//软重启
FRESULT TF_Card_Reset(void);

/*
 * 向指定文件追加写入数据。
 * path    : FatFs 文件路径，例如 "0:/log.txt"。
 * data    : 待写入的数据缓冲区。
 * len     : 期望写入的字节数。
 * written : 实际写入字节数输出指针，可传入 NULL。
 */
FRESULT TF_Card_AppendFile(const char *path, const void *data, UINT len, UINT *written);

#endif
