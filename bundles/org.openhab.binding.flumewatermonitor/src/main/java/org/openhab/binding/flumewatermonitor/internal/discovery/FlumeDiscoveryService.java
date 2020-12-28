/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.flumewatermonitor.internal.discovery;

import static org.openhab.binding.flumewatermonitor.internal.FlumeWaterMonitorBindingConstants.*;

import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.flumewatermonitor.internal.api.FlumeAsyncHttpApi;
import org.openhab.binding.flumewatermonitor.internal.handler.FlumeAccountHandler;
import org.openhab.binding.flumewatermonitor.internal.model.FlumeDeviceData;
import org.openhab.binding.flumewatermonitor.internal.model.FlumeDeviceType;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class does discovery of discoverable things
 *
 * @author Sara Geleskie Damiano - Initial contribution
 */
@NonNullByDefault
public class FlumeDiscoveryService extends AbstractDiscoveryService {
    private static final long RESCAN_INTERVAL_HOURS = 48;
    public static final Set<ThingTypeUID> DISCOVERABLE_THING_TYPES_UIDS = SUPPORTED_DEVICE_TYPES;

    private final Logger logger = LoggerFactory.getLogger(FlumeDiscoveryService.class);
    private @Nullable ScheduledFuture<?> discoveryJob;
    private final FlumeAccountHandler accountHandler;
    private FlumeAsyncHttpApi api;

    /**
     * Create a new discovery service for devices tied to the Flume account.
     *
     * @param accountHandler a handler for the Flume account
     *
     * @note The Flume account "bridge" MUST be created manually. It will not be discovered.
     */
    public FlumeDiscoveryService(final FlumeAccountHandler accountHandler) {
        super(DISCOVERABLE_THING_TYPES_UIDS, 3);
        this.accountHandler = accountHandler;
        this.api = accountHandler.getAsyncApi();
        this.discoveryJob = null;
    }

    @Override
    protected void startBackgroundDiscovery() {
        discoveryJob = scheduler.scheduleWithFixedDelay(this::startScan, 0, RESCAN_INTERVAL_HOURS, TimeUnit.HOURS);
    }

    @Override
    protected synchronized void startScan() {
        logger.debug("Start scan for Flume devices.");
        final ThingUID accountUid = accountHandler.getThing().getUID();
        logger.trace("Searching for Flume sensors associated with Flume account with UID {}", accountUid);
        api = accountHandler.getAsyncApi();
        FlumeDeviceData[] deviceList = api.getAllDevices().join();
        if (deviceList.length > 0) {
            for (final FlumeDeviceData device : deviceList) {
                if (device.deviceId > 0 && device.deviceType == FlumeDeviceType.FlumeSensor) {
                    logger.trace("Found a Flume sensor with device id {}", device.deviceId);
                    final ThingUID deviceUid = new ThingUID(THING_TYPE_FLUME_SENSOR, accountUid,
                            String.valueOf(device.deviceId));
                    logger.trace("This corresponds to a thing type UID {} and a thing UID {}", THING_TYPE_FLUME_SENSOR,
                            deviceUid);
                    final DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(deviceUid)
                            .withThingType(THING_TYPE_FLUME_SENSOR).withBridge(accountUid)
                            .withProperty(CONFIG_DEVICE_ID, String.valueOf(device.deviceId))
                            .withLabel("Flume Water Sensor").withRepresentationProperty(String.valueOf(device.deviceId))
                            .build();
                    thingDiscovered(discoveryResult);
                }
            }
        } else {
            logger.debug("No discovery results returned.");
        }
    }

    @Override
    protected void stopBackgroundDiscovery() {
        stopScan();
        ScheduledFuture<?> backgroudDiscoveryJob = this.discoveryJob;
        if (backgroudDiscoveryJob != null && !backgroudDiscoveryJob.isCancelled()) {
            backgroudDiscoveryJob.cancel(true);
            this.discoveryJob = null;
        }
    }
}
