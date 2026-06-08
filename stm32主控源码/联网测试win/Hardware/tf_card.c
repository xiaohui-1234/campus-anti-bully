#include "tf_card.h"

#include "diskio.h"
#include "sd_spi.h"

/* FatFs 文件系统对象，只在本模块内部维护。 */
static FATFS s_fs;

/* 挂载状态标记，避免重复挂载同一个逻辑盘。 */
static uint8_t s_mounted = 0;

/*
 * 挂载 TF 卡文件系统。
 * FatFs 逻辑盘号固定为 "0:"，第三个参数为 1 表示立即挂载。
 */
FRESULT TF_Card_Mount(void)
{
	FRESULT fr;

	/* 已经挂载时直接返回成功，方便上层重复调用。 */
	if (s_mounted) {
		return FR_OK;
	}

	fr = f_mount(&s_fs, "0:", 1);
	if (fr == FR_OK) {
		/* 只有 FatFs 真正挂载成功后才更新状态。 */
		s_mounted = 1;
	}
	return fr;
}

/*
 * 卸载 TF 卡文件系统。
 * 未挂载时直接返回成功，便于上层做统一清理。
 */
FRESULT TF_Card_Unmount(void)
{
	FRESULT fr;

	if (!s_mounted) {
		return FR_OK;
	}

	fr = f_mount(0, "0:", 1);
	if (fr == FR_OK || fr == FR_NOT_ENABLED) {
		/* FR_NOT_ENABLED 表示该卷未启用，这里也视为已处于卸载状态。 */
		s_mounted = 0;
		return FR_OK;
	}
	return fr;
}



FRESULT TF_Card_Reset(void)
{
	FRESULT fr;
	SD_SPI_Status sd_status;

	fr = TF_Card_Unmount();
	if (fr != FR_OK) {
		return fr;
	}

	sd_status = SD_SPI_Init();
	if (sd_status != SD_SPI_OK) {
		return FR_DISK_ERR;
	}

	return TF_Card_Mount();
}

/*
 * 追加写入文件。
 * 函数内部会先确保文件系统已挂载，然后以存在则打开、不存在则创建的方式打开文件，
 * 再移动到文件末尾写入数据。
 */
FRESULT TF_Card_AppendFile(const char *path, const void *data, UINT len, UINT *written)
{
	FRESULT fr;
	FIL fil;

	/* 先清零输出值，避免调用者在失败路径读到旧数据。 */
	if (written) {
		*written = 0;
	}

	/* 写文件前确保 TF 卡已挂载。 */
	fr = TF_Card_Mount();
	if (fr != FR_OK) {
		return fr;
	}

	/* FA_OPEN_ALWAYS: 文件存在则打开，不存在则创建；FA_WRITE: 允许写入。 */
	fr = f_open(&fil, path, FA_OPEN_ALWAYS | FA_WRITE);
	if (fr != FR_OK) {
		return fr;
	}

	/* 追加模式需要先把文件指针移动到当前文件末尾。 */
	fr = f_lseek(&fil, f_size(&fil));
	if (fr != FR_OK) {
		(void)f_close(&fil);
		return fr;
	}

	/* 写入完成后立即关闭文件，确保目录项和缓存尽快同步。 */
	fr = f_write(&fil, data, len, written);
	(void)f_close(&fil);
	return fr;
}
