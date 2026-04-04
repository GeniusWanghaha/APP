#pragma once

#include "esp_bit_defs.h"

#define MAX30102_REG_INTR_STATUS_1         0x00
#define MAX30102_REG_INTR_STATUS_2         0x01
#define MAX30102_REG_INTR_ENABLE_1         0x02
#define MAX30102_REG_INTR_ENABLE_2         0x03
#define MAX30102_REG_FIFO_WR_PTR           0x04
#define MAX30102_REG_OVF_COUNTER           0x05
#define MAX30102_REG_FIFO_RD_PTR           0x06
#define MAX30102_REG_FIFO_DATA             0x07
#define MAX30102_REG_FIFO_CONFIG           0x08
#define MAX30102_REG_MODE_CONFIG           0x09
#define MAX30102_REG_SPO2_CONFIG           0x0A
#define MAX30102_REG_LED1_PA               0x0C
#define MAX30102_REG_LED2_PA               0x0D
#define MAX30102_REG_MULTI_LED_CTRL1       0x11
#define MAX30102_REG_MULTI_LED_CTRL2       0x12
#define MAX30102_REG_TEMP_INTEGER          0x1F
#define MAX30102_REG_TEMP_FRACTION         0x20
#define MAX30102_REG_TEMP_CONFIG           0x21
#define MAX30102_REG_REV_ID                0xFE
#define MAX30102_REG_PART_ID               0xFF

#define MAX30102_INT_A_FULL                BIT7
#define MAX30102_INT_PPG_RDY               BIT6
#define MAX30102_INT_ALC_OVF               BIT5
#define MAX30102_INT_PWR_RDY               BIT0
#define MAX30102_INT_DIE_TEMP_RDY          BIT1

#define MAX30102_MODE_SHDN                 BIT7
#define MAX30102_MODE_RESET                BIT6
#define MAX30102_MODE_HEART_RATE           0x02
#define MAX30102_MODE_SPO2                 0x03
#define MAX30102_MODE_MULTI_LED            0x07

#define MAX30102_FIFO_SMP_AVE_SHIFT        5
#define MAX30102_FIFO_ROLLOVER_EN          BIT4
#define MAX30102_FIFO_A_FULL_MASK          0x0F

#define MAX30102_SPO2_ADC_RGE_SHIFT        5
#define MAX30102_SPO2_SR_SHIFT             2
#define MAX30102_SPO2_LED_PW_MASK          0x03

#define MAX30102_TEMP_EN                   BIT0
#define MAX30102_EXPECTED_PART_ID          0x15

#define MAX30102_SMP_AVE_1                 0x00
#define MAX30102_SPO2_ADC_RGE_2048         0x00
#define MAX30102_SPO2_ADC_RGE_4096         0x01
#define MAX30102_SPO2_ADC_RGE_8192         0x02
#define MAX30102_SPO2_ADC_RGE_16384        0x03

#define MAX30102_SPO2_SR_50                0x00
#define MAX30102_SPO2_SR_100               0x01
#define MAX30102_SPO2_SR_200               0x02
#define MAX30102_SPO2_SR_400               0x03
#define MAX30102_SPO2_SR_800               0x04
#define MAX30102_SPO2_SR_1000              0x05
#define MAX30102_SPO2_SR_1600              0x06
#define MAX30102_SPO2_SR_3200              0x07

#define MAX30102_LED_PW_69                 0x00
#define MAX30102_LED_PW_118                0x01
#define MAX30102_LED_PW_215                0x02
#define MAX30102_LED_PW_411                0x03
