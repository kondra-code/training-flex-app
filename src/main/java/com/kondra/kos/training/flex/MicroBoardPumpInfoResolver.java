/**
 * (C) Copyright 2025, TCCC, All rights reserved.
 */
package com.kondra.kos.training.flex;

import com.tccc.kos.ext.freestyle.pumpinfo.DefaultPumpInfoResolver;
import com.tccc.kos.ext.freestyle.pumpinfo.PumpInfo;

/**
 * {@code PumpInfoResolver} for the micro board to match the towers.
 */
public class MicroBoardPumpInfoResolver extends DefaultPumpInfoResolver {
    @Override
    public void resolve(PumpInfo info) {
        int pos = info.getPosition();

        // check for agit pumps
        if (pos >= 0) {
            setNamePrefix("A");
        }

        // check for static pumps
        if (pos >= 8) {
            setNamePrefix("S");
            info.setPosition(pos - 8);
            info.setMaxPosition(info.getMaxPosition() - 8);
        }

        // regular resolve
        super.resolve(info);
    }
}