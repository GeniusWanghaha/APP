idf_component_register(
    SRCS "ecg_sampler.c" "ppg_sampler.c"
    INCLUDE_DIRS "."
    REQUIRES ecg_ad8232 ppg_max30102 state_monitor timebase driver freertos
)
