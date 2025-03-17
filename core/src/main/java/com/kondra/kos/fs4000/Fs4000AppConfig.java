/**
 * (C) Copyright 2024, Kondra, All rights reserved.
 */
package com.kondra.kos.fs4000;

import com.tccc.kos.commons.core.service.config.annotations.ConfigDesc;
import com.tccc.kos.core.service.app.BaseAppConfig;

import lombok.Getter;
import lombok.Setter;

/**
 * Config bean for Fs7100 app.
 *
 * @author David Vogt (david@kondra.com)
 * @version 2024-07-30
 */
@Getter @Setter
public class Fs4000AppConfig extends BaseAppConfig {
    @ConfigDesc("Enable the CAN simulator so CAN hardware appears when not running on real hardware.")
    private boolean enableCanSimulator;
}