/**
 * (C) Copyright 2025, TCCC, All rights reserved.
 */
package com.kondra.kos.training.flex;

import java.util.ArrayList;
import java.util.List;

import com.tccc.kos.commons.core.context.annotations.Autowired;
import com.tccc.kos.commons.util.json.JsonDescriptor;
import com.tccc.kos.commons.util.resource.ClassLoaderResourceLoader;
import com.tccc.kos.core.app.KosCore;
import com.tccc.kos.core.service.assembly.CoreAssembly;
import com.tccc.kos.core.service.criticaldata.CriticalDataService;
import com.tccc.kos.core.service.spawn.SpawnService;
import com.tccc.kos.ddk.service.door.Door;
import com.tccc.kos.ddk.service.door.DoorAware;
import com.tccc.kos.ext.dispense.HolderBuilder;
import com.tccc.kos.ext.dispense.pipeline.beverage.BeverageNozzlePipeline;
import com.tccc.kos.ext.dispense.pipeline.ingredient.IngredientNozzlePipeline;
import com.tccc.kos.ext.dispense.pipeline.ingredient.XmlPumpIntentFactory;
import com.tccc.kos.ext.dispense.service.insertion.InsertionService;
import com.tccc.kos.ext.dispense.service.nozzle.Nozzle;
import com.tccc.kos.ext.freestyle.StandardFreestyleAssembly;
import com.tccc.kos.ext.freestyle.SuperPumpDefinition;
import com.tccc.kos.ext.freestyle.hardware.can.board.FlexMacroBoard;
import com.tccc.kos.ext.freestyle.hardware.can.board.FlexMacroBoard.NsPumpType;
import com.tccc.kos.ext.freestyle.hardware.can.board.FlexMicroBoard;
import com.tccc.kos.ext.freestyle.hardware.can.subnode.GPIOState;
import com.tccc.kos.ext.freestyle.hardware.can.subnode.pump.MSVPump;
import com.tccc.kos.ext.freestyle.hardware.rfid.RfidAntenna;
import com.tccc.kos.ext.freestyle.hardware.rfid.RfidBoard;
import com.tccc.kos.ext.freestyle.hardware.rfid.adapters.EX10RfidAdapter;
import com.tccc.kos.ext.freestyle.pipeline.beverage.FreestyleBeveragePourEngine;
import com.tccc.kos.ext.freestyle.pipeline.ingredient.XmlFreestylePumpIntentFactory;
import com.tccc.kos.ext.freestyle.service.brandset.beans.Ingredient;
import com.tccc.kos.ext.freestyle.service.cartridge.CartridgeHolderBuilder;
import com.tccc.kos.ext.freestyle.service.cartridge.FreestyleCartridgeScanner;
import com.tccc.kos.ext.freestyle.service.cartridgeagitation.CartridgeAgitator;
import com.tccc.kos.ext.freestyle.service.rfid.RfidService;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Assembly class for Flex dispenser.
 */
@Slf4j
public class FlexAssembly extends StandardFreestyleAssembly implements CoreAssembly, DoorAware {
    // reason codes
    private static final String REASON_door = "door";

    @Autowired
    private FlexApp app;                              // system app
    @Autowired
    private SpawnService spawnService;                  // used to start adapter
    @Autowired
    private InsertionService insertionService;          // to insert waters
    @Autowired
    private RfidService rfidService;                    // to setup rfid
    @Autowired
    private CriticalDataService criticalDataService;    // add FCM's as critical data sources
    private XmlPumpIntentFactory intentFactory;
    private FlexMacroBoard macroBoard;                  // macro board
    private FlexMicroBoard microBoard;                  // micro board
    private RfidBoard rfidBoard;                        // rfid board
    private CartridgeAgitator agitator;                 // cartridge agitator
    @Getter
    private Nozzle nozzle;                              // the nozzle
    @Getter
    private FreestyleBeveragePourEngine engine;
    @Getter
    private BeverageNozzlePipeline beveragePipeline;    // access to the pipeline
    private EX10RfidAdapter rfidAdapter;                // rfid adapter for EX10 hardware
    private GPIOState gpioState;                        // gpio state bean

    public FlexAssembly(JsonDescriptor descriptor) throws Exception {
        super("core", descriptor);
    }

    @Override
    public void load() throws Exception {
        // create a nozzle and add it to this assembly
        addNozzle(nozzle = new Nozzle(this, "nozzle"));


        List<SuperPumpDefinition> superPumps = new ArrayList<>();
        superPumps.add(new SuperPumpDefinition(18, new int[] {15,16,17}));

        addBoard(microBoard = new FlexMicroBoard(this, "micro", superPumps, new MicroBoardPumpInfoResolver()));
        addBoard(macroBoard = new FlexMacroBoard(this, "macro", NsPumpType.MSV));
        addBoard(rfidBoard = new RfidBoard(this, "rfid"));

        // add cartridge agitator agitation tower and start in paused state
        agitator = new CartridgeAgitator("agit", macroBoard.getCartridgeAgitatorSubNode(), false);
        addAgitator(agitator);
        agitator.pause(REASON_door, false);

        // EX10 rfid adapter
        rfidAdapter = new EX10RfidAdapter();

        // setup rfid antennas
        RfidAntenna staticAntenna = new RfidAntenna("static", 1);
        RfidAntenna agitAntenna = new RfidAntenna("agit", 2);
        rfidBoard.addAntenna(staticAntenna);
        rfidBoard.addAntenna(agitAntenna);

        // cartridge scanners for antennas to insert ingredients
        FreestyleCartridgeScanner agitScanner = new FreestyleCartridgeScanner(agitAntenna);
        FreestyleCartridgeScanner staticScanner = new FreestyleCartridgeScanner(staticAntenna);

        // add scanners to rfid service
        rfidService.addScanner(agitScanner);
        rfidService.addScanner(staticScanner);

        // add static micro pumps
        CartridgeHolderBuilder microBuilder = new CartridgeHolderBuilder(this, nozzle);
        microBuilder.setPumpIterator(microBoard.getMicros(), 8, 1);
        microBuilder.setNameIterator("S", 1, 1);
        microBuilder.setScannerIterator(staticScanner, 7, -1);
        microBuilder.buildMicros(8, 0);

        // add agit micro pumps
        microBuilder = new CartridgeHolderBuilder(this, nozzle);
        microBuilder.setAgitator(agitator);
        microBuilder.setPumpIterator(microBoard.getMicros(), 0, 1);
        microBuilder.setNameIterator("A", 1, 1);
        microBuilder.setScannerIterator(agitScanner, 0, 1);
        microBuilder.buildMicros(8, 1);

        // add waters and sweetener
        HolderBuilder holderBuilder = new HolderBuilder(this, nozzle);
        holderBuilder.buildWater(macroBoard.getWaterPump());
        holderBuilder.buildCarb(macroBoard.getCarbPump());
        holderBuilder.buildNutritive(macroBoard.getNsPump());

        // add aux macros
        holderBuilder.buildMacro(macroBoard.getMacro1()).setIngType(Ingredient.TYPE_BIB);
        holderBuilder.buildMacro(macroBoard.getMacro2()).setIngType(Ingredient.TYPE_BIB);
        holderBuilder.buildMacro(macroBoard.getMacro3()).setIngType(Ingredient.TYPE_BIB);
        holderBuilder.buildMacro(macroBoard.getMacro4()).setIngType(Ingredient.TYPE_BIB);

        // load intents used by the pump pipeline
        intentFactory = new XmlFreestylePumpIntentFactory();
        intentFactory.addLoader(new ClassLoaderResourceLoader(getClass().getClassLoader()));
        intentFactory.load("intents.xml");

        // add a ingredient pipeline and use water for dilution:
        IngredientNozzlePipeline ingredientPipeline = new IngredientNozzlePipeline(intentFactory);
        ingredientPipeline.setDilutionPump(macroBoard.getWaterPump());
        nozzle.add(ingredientPipeline);

        // add a beverage pipeline:
        engine = new FreestyleBeveragePourEngine(macroBoard);
        beveragePipeline = new BeverageNozzlePipeline(engine);
        nozzle.add(beveragePipeline);

        // add additional critical data sources as they have eeproms
        criticalDataService.addSource(macroBoard.getWaterPump());
        criticalDataService.addSource(macroBoard.getCarbPump());
        criticalDataService.addSource((MSVPump)macroBoard.getNsPump());

        // register gpioState with state service so it generates events
        gpioState = macroBoard.getGpioSubNode().getState();
        getCtx().connect(gpioState);

        // gpio input0 is the door switch... attach a listener to update the door
        gpioState.addListener(s -> {
            Boolean doorState = gpioState.getInput0();
            if (doorState != null) {
                app.getDdk().getDoorService().setOpen(doorState);
            }
        });

        // when rfid is ready, do an inventory scan to pick up all the
        // cartridges, even if the door is closed
        rfidBoard.addReadyListener(b -> {
            log.info("Performing inventory scan");
            agitScanner.inventoryScan();
            staticScanner.inventoryScan();
        });
    }

    @Override
    public void onDoorChanged(Door door) {
        if (door.isOpen()) {
            // pause agitator
            agitator.pause(REASON_door, true);

            // enable rfid
            rfidService.startRecurringDefaultGroup();
        }

        // door is closed
        else {
            // disable rfid
            rfidService.stopRecurring();

            // resume agitator
            agitator.resume(REASON_door);
        }
    }

    @Override
    public void start() {
        // load the rfid adapter if not in the simulator
        if (!KosCore.isSimulator()) {
            spawnService.addProcess(rfidAdapter);
        }
    }

    @Override
    public void started() {
        // insert plain and carbonated water as intrinsic ingredients
        insertionService.insertIntrinsic(Ingredient.WATER, macroBoard.getWaterPump().getHolder());
        insertionService.insertIntrinsic(Ingredient.CARB, macroBoard.getCarbPump().getHolder());
        insertionService.insertLocked(Ingredient.FIS, macroBoard.getNsPump().getHolder());
    }
}
