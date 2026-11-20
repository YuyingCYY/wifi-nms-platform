package com.delta.nms.mqtt;

import com.delta.nms.service.DeviceMessageProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttBridgeClient implements MqttCommandPublisher, MqttCallbackExtended {

    private static final String[] SUBSCRIBE_TOPICS = {
            "nms/devices/+/heartbeat",
            "nms/devices/+/telemetry",
            "nms/devices/+/event"
    };

    private final DeviceMessageProcessor messageProcessor;
    private final ObjectMapper objectMapper;

    @Value("${nms.mqtt.broker-url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${nms.mqtt.client-id:wifi-nms-backend-01}")
    private String clientId;

    @Value("${nms.mqtt.username:}")
    private String username;

    @Value("${nms.mqtt.password:}")
    private String password;

    @Value("${nms.mqtt.enabled:true}")
    private boolean mqttEnabled;

    private MqttClient mqttClient;

    @PostConstruct
    public void init() {
        if (!mqttEnabled) {
            log.info("MQTT client is disabled via configuration.");
            return;
        }
        try {
            String uniqueClientId = clientId + "-" + System.currentTimeMillis() % 10000;
            mqttClient = new MqttClient(brokerUrl, uniqueClientId, new MemoryPersistence());
            mqttClient.setCallback(this);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);
            if (username != null && !username.isBlank()) {
                options.setUserName(username);
                options.setPassword(password.toCharArray());
            }

            log.info("Connecting to MQTT broker at {} as {}", brokerUrl, uniqueClientId);
            mqttClient.connect(options);
        } catch (MqttException e) {
            log.error("Failed to connect to MQTT broker {}: {}", brokerUrl, e.getMessage());
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT connected to {} (reconnect={}). Subscribing to device topics...", serverURI, reconnect);
        try {
            int[] qos = new int[]{0, 1, 1};
            mqttClient.subscribe(SUBSCRIBE_TOPICS, qos);
            log.info("Subscribed to topics: {}", String.join(", ", SUBSCRIBE_TOPICS));
        } catch (MqttException e) {
            log.error("Failed to subscribe to MQTT topics: {}", e.getMessage(), e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}. Automatic reconnect enabled.", cause != null ? cause.getMessage() : "unknown");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        messageProcessor.handleIncomingMessage(topic, payload);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Delivery acknowledged by broker
    }

    @Override
    public void publishCommand(String macAddress, String commandType, Map<String, Object> payload) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            throw new IllegalStateException("MQTT broker is currently disconnected");
        }
        String topic = String.format("nms/devices/%s/command", macAddress.toUpperCase());
        try {
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("commandType", commandType);
            envelope.put("timestamp", Instant.now().toString());
            envelope.put("payload", payload);

            byte[] bytes = objectMapper.writeValueAsBytes(envelope);
            MqttMessage msg = new MqttMessage(bytes);
            msg.setQos(1); // At least once delivery for commands
            mqttClient.publish(topic, msg);
            log.info("Published {} command to topic {}", commandType, topic);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish MQTT command to " + topic, e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
                log.info("MQTT client disconnected gracefully.");
            } catch (MqttException e) {
                log.warn("Error while disconnecting MQTT client: {}", e.getMessage());
            }
        }
    }
}
