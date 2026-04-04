#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "esp_err.h"

esp_err_t timebase_init(void);
uint64_t timebase_get_time_us(void);
bool timebase_is_ready(void);
bool timebase_self_test(void);
