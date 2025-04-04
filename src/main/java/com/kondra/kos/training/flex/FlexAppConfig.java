/**
 * (C) Copyright 2025, TCCC, All rights reserved.
 */
package com.kondra.kos.training.flex;

import com.tccc.kos.commons.core.service.config.annotations.ConfigDesc;
import com.tccc.kos.core.service.app.BaseAppConfig;

import lombok.Getter;
import lombok.Setter;

/**
 * Config bean for FlexApp app.
 */
@Getter @Setter
public class FlexAppConfig extends BaseAppConfig {
    @ConfigDesc("Enable the CAN simulator so CAN hardware appears when not running on real hardware.")
    private boolean enableCanSimulator;
}