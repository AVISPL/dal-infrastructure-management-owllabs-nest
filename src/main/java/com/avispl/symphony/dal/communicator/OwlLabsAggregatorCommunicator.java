/*
 * Copyright (c) 2023 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import com.avispl.symphony.api.dal.error.CommandFailureException;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.monitor.aggregator.Aggregator;
import com.avispl.symphony.dal.aggregator.parser.AggregatedDeviceProcessor;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMapping;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMappingParser;
import com.avispl.symphony.dal.communicator.data.Constant;
import com.avispl.symphony.dal.communicator.data.dto.Authorization;
import com.avispl.symphony.dal.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Communicator details
 * @author Maksym.Rossiitsev/Av
 * @since 1.0.0
 */
public class OwlLabsAggregatorCommunicator extends RestCommunicator implements Aggregator, Monitorable, Controller {
    private Authorization authorization;
    private String oauthHostname;
    private long adapterInitializationTimestamp;
    private Properties adapterProperties;

    private AggregatedDeviceProcessor aggregatedDeviceProcessor;
    private Map<String, AggregatedDevice> aggregatedDevices = new HashMap<>();

    public OwlLabsAggregatorCommunicator() throws IOException {
        Map<String, PropertiesMapping> mapping = new PropertiesMappingParser().loadYML("mapping/model-mapping.yml", getClass());
        aggregatedDeviceProcessor = new AggregatedDeviceProcessor(mapping);

        adapterProperties = new Properties();
        adapterProperties.load(getClass().getResourceAsStream("/version.properties"));
    }

    /**
     * Retrieves {@link #oauthHostname}
     *
     * @return value of {@link #oauthHostname}
     */
    public String getOauthHostname() {
        return oauthHostname;
    }

    /**
     * Sets {@link #oauthHostname} value
     *
     * @param oauthHostname new value of {@link #oauthHostname}
     */
    public void setOauthHostname(String oauthHostname) {
        this.oauthHostname = oauthHostname;
    }

    @Override
    protected void internalInit() throws Exception {
        adapterInitializationTimestamp = System.currentTimeMillis();
        super.internalInit();
    }

    @Override
    public void controlProperty(ControllableProperty controllableProperty) throws Exception {
        String deviceId = controllableProperty.getDeviceId();
        String property = controllableProperty.getProperty();

        switch (property) {
            case "Reboot":
                sendReboot(deviceId);
                break;
            case "Hoot":
                sendHoot(deviceId);
                break;
            default:
                logger.warn("Unable to send control command to the device: unknown control property: " + property);
                break;
        }
    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {
        if (CollectionUtils.isEmpty(list)) {
            throw new IllegalArgumentException("OwlLabs Nest: Controllable properties cannot be null or empty");
        }

        for (ControllableProperty controllableProperty : list) {
            controlProperty(controllableProperty);
        }
    }

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        Map<String, String> statistics = new HashMap<>();
        ExtendedStatistics extendedStatistics = new ExtendedStatistics();
        Map<String, String> dynamicStatistics = new HashMap<>();

        statistics.put("AdapterVersion", adapterProperties.getProperty("aggregator.version"));
        statistics.put("AdapterBuildDate", adapterProperties.getProperty("aggregator.build.date"));

        long adapterUptime = System.currentTimeMillis() - adapterInitializationTimestamp;
        statistics.put("AdapterUptime(min)", String.valueOf(adapterUptime / (1000*60)));
        statistics.put("AdapterUptime", normalizeUptime(adapterUptime/1000));
        dynamicStatistics.put("MonitoredDevicesTotal", String.valueOf(aggregatedDevices.size()));

        extendedStatistics.setDynamicStatistics(dynamicStatistics);
        extendedStatistics.setStatistics(statistics);
        return Collections.singletonList(extendedStatistics);
    }

    @Override
    public List<AggregatedDevice> retrieveMultipleStatistics() throws Exception {
        if (authorization == null || authorization.needsUpdate()) {
            authenticate();
        }
        fetchDevicesList();
        aggregatedDevices.values().forEach(device -> {
            try {
                populateDeviceDetails(device);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return new ArrayList<>(aggregatedDevices.values());
    }

    @Override
    public List<AggregatedDevice> retrieveMultipleStatistics(List<String> list) throws Exception {
        return retrieveMultipleStatistics()
                .stream()
                .filter(aggregatedDevice -> list.contains(aggregatedDevice.getDeviceId()))
                .collect(Collectors.toList());
    }

    @Override
    protected void authenticate() throws Exception {
        MultiValueMap<String, String> request = new LinkedMultiValueMap<>();
        request.add("grant_type","client_credentials");
        request.add("client_id", getLogin());
        request.add("client_secret", getPassword());
        authorization = doPost("https://" + oauthHostname + Constant.URI.AUTH, request, Authorization.class);
    }

    @Override
    protected HttpHeaders putExtraRequestHeaders(HttpMethod httpMethod, String uri, HttpHeaders headers) throws Exception {
        headers.set("Accept", "application/json");
        if (uri.contains(Constant.URI.AUTH)) {
            headers.set("Content-Type", "application/x-www-form-urlencoded");
        } else {
            headers.set("Content-Type", "application/json");
            headers.set("Authorization", "Bearer " + authorization.getAccessToken());
        }
        return super.putExtraRequestHeaders(httpMethod, uri, headers);
    }

    private synchronized void fetchDevicesList() throws Exception {
        String continuationToken = "";

        // To keep track of all the relevant devices collected this cycle
        List<AggregatedDevice> retrievedAggregatedDevices = new ArrayList<>();
            do {
                String urlTemplate = String.format(Constant.URI.DEVICES, continuationToken, 1);

                JsonNode response = doGet(urlTemplate, JsonNode.class);
                continuationToken = response.at("/nextToken").asText();

                List<AggregatedDevice> devices = aggregatedDeviceProcessor.extractDevices(response);
                retrievedAggregatedDevices.addAll(devices);
                // TODO: this "null" thing isn't nice. Gotta make it automatic
            } while (StringUtils.isNotNullOrEmpty(continuationToken) && !continuationToken.equals("null"));

        // Remove cached devices that are not in the latest list of devices
        aggregatedDevices.entrySet().removeIf(deviceEntry -> !retrievedAggregatedDevices.stream().map(AggregatedDevice::getDeviceId).collect(Collectors.toList()).contains(deviceEntry.getKey()));
        retrievedAggregatedDevices.forEach(device -> {
            aggregatedDevices.put(device.getDeviceId(), device);
        });
    }

    private void populateDeviceDetails(AggregatedDevice device) throws Exception {
        JsonNode deviceResponse = doGet(String.format(Constant.URI.DEVICE, device.getDeviceId()), JsonNode.class);
        aggregatedDeviceProcessor.applyProperties(device.getProperties(), deviceResponse, "Details");

        boolean deviceOnline = deviceResponse.at("/data/iotStatus").asText().equalsIgnoreCase("online");
        device.setDeviceOnline(deviceOnline);
    }

    /**
     * Uptime is received in seconds, need to normalize it and make it human-readable, like
     * 1 day(s) 5 hour(s) 12 minute(s) 55 minute(s)
     * Incoming parameter is may have a decimal point, so in order to safely process this - it's rounded first.
     * We don't need to add a segment of time if it's 0.
     *
     * @param uptimeSeconds value in seconds
     * @return string value of format 'x day(s) x hour(s) x minute(s) x minute(s)'
     */
    private String normalizeUptime(long uptimeSeconds) {
        StringBuilder normalizedUptime = new StringBuilder();

        long seconds = uptimeSeconds % 60;
        long minutes = uptimeSeconds % 3600 / 60;
        long hours = uptimeSeconds % 86400 / 3600;
        long days = uptimeSeconds / 86400;

        if (days > 0) {
            normalizedUptime.append(days).append(" day(s) ");
        }
        if (hours > 0) {
            normalizedUptime.append(hours).append(" hour(s) ");
        }
        if (minutes > 0) {
            normalizedUptime.append(minutes).append(" minute(s) ");
        }
        if (seconds > 0) {
            normalizedUptime.append(seconds).append(" second(s)");
        }
        return normalizedUptime.toString().trim();
    }

    /**
     * Send reboot command to the device
     *
     * @param deviceId id of the target device
     * */
    private void sendReboot(String deviceId) throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("command", "reboot");
        request.put("reason", "Scheduled maintenance");
        JsonNode response = doPost(String.format(Constant.URI.CONTROL, deviceId), request, JsonNode.class);
        if (response == null) {
            throw new RuntimeException("Reboot command failed: reboot command response is null");
        }
        if (!response.at("/success").asBoolean()) {
            throw new RuntimeException("Reboot command failed: " + response.at("/message").asText());
        }
    }

    /**
     * Send hoot command to the device
     *
     * @param deviceId is of the target device
     */
    private void sendHoot(String deviceId) throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("command", "hoot");
        request.put("reason", "Device checkup");
        JsonNode response = doPost(String.format(Constant.URI.CONTROL, deviceId), request, JsonNode.class);
        if (response == null) {
            throw new RuntimeException("Hoot command failed: hoot command response is null");
        }
        if (!response.at("/success").asBoolean()) {
            throw new RuntimeException("Hoot command failed: " + response.at("/message").asText());
        }
    }
}
