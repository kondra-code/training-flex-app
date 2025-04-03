/**
 * (C) Copyright 2024, TCCC, All rights reserved.
 */
package com.kondra.kos.training.flex;

import java.util.Arrays;

import com.tccc.kos.commons.core.context.annotations.Autowired;
import com.tccc.kos.commons.core.service.config.BeanChanges;
import com.tccc.kos.commons.util.resource.ClassLoaderResourceLoader;
import com.tccc.kos.core.service.app.AppService;
import com.tccc.kos.core.service.app.Application;
import com.tccc.kos.core.service.app.SystemApplication;
import com.tccc.kos.core.service.device.DeviceService;
import com.tccc.kos.core.service.device.serialnum.SerialNumberProvider;
import com.tccc.kos.core.service.device.serialnum.criticaldata.CriticalDataSerialNumberProvider;
import com.tccc.kos.ext.freestyle.FreestyleExtension;
import com.tccc.kos.ext.freestyle.region.FreestyleRegionFactory;

import lombok.Getter;

/**
 * System application for Flex dispenser. This is the main entry point from
 * kOS during the boot process..
 */
public class FlexApp extends SystemApplication<FlexAppConfig> {

    @Autowired
    private FreestyleExtension freestyleExt;
    @Autowired
    private AppService appService;
    @Autowired
    private DeviceService deviceService;
    private FlexAssembly assembly;
    @Getter
    private DDK ddk;

    @Override
    public void load() throws Exception {

        // load regions from xml
        FreestyleRegionFactory factory = new FreestyleRegionFactory();
        factory.addLoader(new ClassLoaderResourceLoader(getClass().getClassLoader()));
        factory.load("regions.xml");

        // install regions
        installRegions(factory.getRegions());

        // use critical data for serial number provider
        SerialNumberProvider provider = new CriticalDataSerialNumberProvider();
        addToCtx(provider);
        deviceService.setSerialNumberProvider(provider);
    }

    @Override
    public void start() throws Exception {

        // create and install the assembly for the device
        installAssembly(assembly = new FlexAssembly(getDescriptor()));
    }

    @Override
    public void started() throws Exception {
        // fake config change to get simulator going if needed
        onConfigChanged(null);

        appService.whenAppStarted(DDK.APP_ID, app -> prepareDDK(app));
    }

    /**
     * Called when the ddk is loaded so we can configure it.
     */
    private void prepareDDK(Application<?> ddkApp) {
        // cast the app to the DDK interface
        this.ddk = (DDK) ddkApp;

        // use ddk as the pour delegate
        assembly.getBeveragePipeline().setDelegate(ddk);

        // add the nozzle
        ddk.setNozzle(assembly.getNozzle());

        // configure ncui auth
        ddk.getAuthService().setRolePinCode(NcuiRole.CREW, "2494");
        ddk.getAuthService().setRolePinCode(NcuiRole.MANAGER, "8561");
        ddk.getAuthService().setRolePinCode(NcuiRole.TECHNICIAN, "2653");

        // add network tests
        ddk.getNetworkTestService().addDnsTest(Arrays.asList("google.com"));
        ddk.getNetworkTestService().addPingTest(Arrays.asList("8.8.8.8"));

        // add assembly as door listener
        ddkApp.getCtx().connect(assembly);

        // load startup screen
        ddk.goToStartScreen();
    }

    @Override
    public void onConfigChanged(BeanChanges changes) {
        // enable / disable the simulator but only after the app is started
        if (isStarted()) {
            if (getConfig().isEnableCanSimulator()) {
                freestyleExt.startSimulator();
            } else {
                freestyleExt.stopSimulator();
            }
        }
    }
}
