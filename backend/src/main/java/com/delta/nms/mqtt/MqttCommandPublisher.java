package com.delta.nms.mqtt;

import java.util.Map;

public interface MqttCommandPublisher {
    void publishCommand(String macAddress, String commandType, Map<String, Object> payload);
}
