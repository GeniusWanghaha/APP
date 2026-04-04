#pragma once

#include "driver/gpio.h"
#include "driver/i2c.h"
#include "esp_adc/adc_oneshot.h"

#define BOARD_AD8232_OUTPUT_GPIO        GPIO_NUM_4
#define BOARD_AD8232_ADC_UNIT           ADC_UNIT_1
#define BOARD_AD8232_ADC_CHANNEL        ADC_CHANNEL_3
#define BOARD_AD8232_LO_PLUS_GPIO       GPIO_NUM_5
#define BOARD_AD8232_LO_MINUS_GPIO      GPIO_NUM_6
#define BOARD_AD8232_SDN_GPIO           GPIO_NUM_7

#define BOARD_MAX30102_I2C_PORT         I2C_NUM_0
#define BOARD_MAX30102_SCL_GPIO         GPIO_NUM_17
#define BOARD_MAX30102_SDA_GPIO         GPIO_NUM_18
#define BOARD_MAX30102_INT_GPIO         GPIO_NUM_21
#define BOARD_MAX30102_I2C_ADDR         0x57

/*
 * Business IO deliberately avoids ESP32-S3 strapping pins GPIO0/GPIO3/GPIO45/GPIO46.
 */
