package io.cloudevents.experimental.endpoints.mqtt;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.message.Encoding;
import io.cloudevents.experimental.endpoints.IEndpointCredential;
import io.cloudevents.experimental.endpoints.PlainEndpointCredential;
import io.cloudevents.experimental.endpoints.ProducerEndpoint;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttProducerEndpoint extends ProducerEndpoint {

    enum MqttVersion {
        MQTT_3_1_1,
        MQTT_5_0
    }

    private Logger _logger = LogManager.getLogger(MqttProducerEndpoint.class);
    private IEndpointCredential _credential;
    private URI _endpoint;
    private String _topic;
    private int _qos;
    private MqttClient _mqttClient;
    private org.eclipse.paho.mqttv5.client.MqttClient _mqttClientV5;
    private MqttVersion _protocolVersion;
    

    MqttProducerEndpoint(MqttVersion version, IEndpointCredential credential,
            Map<String, String> options, List<URI> endpoints) {
        this._protocolVersion = version;
        this._credential = credential;
        this._endpoint = endpoints.get(0);

        try {
            if (this._endpoint.getScheme().equals("mqtt")) {
                this._endpoint = new URI("tcp", this._endpoint.getUserInfo(), this._endpoint.getHost(),
                        this._endpoint.getPort() == -1 ? 1883 : this._endpoint.getPort(),
                        this._endpoint.getPath(), this._endpoint.getQuery(), this._endpoint.getFragment());
            } else if (this._endpoint.getScheme().equals("mqtts")) {
                this._endpoint = new URI("ssl", this._endpoint.getUserInfo(), this._endpoint.getHost(),
                        this._endpoint.getPort() == -1 ? 8883 : this._endpoint.getPort(),
                        this._endpoint.getPath(), this._endpoint.getQuery(), this._endpoint.getFragment());
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        if (options.containsKey("topic")) {
            this._topic = options.get("topic");
        }
        if (options.containsKey("qos")) {
            this._qos = Integer.parseInt(options.get("qos"));
        }
        if (version == MqttVersion.MQTT_3_1_1) {
            try {
                _mqttClient = new MqttClient(_endpoint.toString(), MqttClient.generateClientId(),
                        new MemoryPersistence(), MqttThreadPool.getExecutor());
            } catch (MqttException e) {
                _logger.error("Error creating MQTT client: " + e.getMessage());
            }
        } else if (version == MqttVersion.MQTT_5_0) {
            try {
                _mqttClientV5 = new org.eclipse.paho.mqttv5.client.MqttClient(_endpoint.toString(),
                        MqttClient.generateClientId(),
                        new org.eclipse.paho.mqttv5.client.persist.MemoryPersistence(),
                        MqttThreadPool.getExecutor());
            } catch (org.eclipse.paho.mqttv5.common.MqttException e) {
                _logger.error("Error creating MQTT client: " + e.getMessage());
            }
        }
    }

    public static void register() {
        addProducerEndpointFactoryHook((credential, protocol, options, endpoints) -> {
            if (protocol.compareToIgnoreCase("mqtt/5.0") == 0 || protocol.compareToIgnoreCase("mqtt") == 0) {
                return new MqttProducerEndpoint(MqttVersion.MQTT_5_0, credential, options, endpoints);
            } else if (protocol.compareToIgnoreCase("mqtt/3.1.1") == 0) {
                return new MqttProducerEndpoint(MqttVersion.MQTT_3_1_1, credential, options, endpoints);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> sendAsync(CloudEvent cloudEvent, Encoding contentMode, EventFormat formatter) {

        if (_protocolVersion == MqttVersion.MQTT_3_1_1) {
            if (contentMode == Encoding.BINARY) {
                return CompletableFuture.failedFuture(new Exception("MQTT 3.1.1 does not support binary encoding"));
            }
            var mqttMessage = new MqttMessage();
            mqttMessage.setQos(_qos);
            mqttMessage.setPayload(formatter.serialize(cloudEvent));
            return sendAsync(mqttMessage);
        } else if (_protocolVersion == MqttVersion.MQTT_5_0) {
            var mqttMessage = new org.eclipse.paho.mqttv5.common.MqttMessage();
            mqttMessage.setQos(_qos);
            var props = new org.eclipse.paho.mqttv5.common.packet.MqttProperties();
            if (contentMode == Encoding.BINARY) {
                mqttMessage.setPayload(cloudEvent.getData().toBytes());
                var userProperties = new ArrayList<org.eclipse.paho.mqttv5.common.packet.UserProperty>();
                for (var entry : cloudEvent.getAttributeNames()) {
                    if (entry != "data") {
                        userProperties.add(new org.eclipse.paho.mqttv5.common.packet.UserProperty(entry,
                                cloudEvent.getAttribute(entry).toString()));
                    }
                }
                props.setContentType(cloudEvent.getDataContentType());
                props.setUserProperties(userProperties);
            } else {
                props.setContentType(formatter.serializedContentType());
                mqttMessage.setPayload(formatter.serialize(cloudEvent));
            }
            mqttMessage.setProperties(props);

            return sendAsync(mqttMessage);
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> sendAsync(MqttMessage mqttMessage) {
        return connect().thenComposeAsync((v) -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            try {
                _mqttClient.publish(_topic, mqttMessage);
                future.complete(null);
            } catch (MqttException e) {
                future.completeExceptionally(e);
            }
            return future;
        }, MqttThreadPool.getExecutor());
    }

    public CompletableFuture<Void> sendAsync(org.eclipse.paho.mqttv5.common.MqttMessage mqttMessage) {
        return connect().thenComposeAsync((v) -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            try {
                _mqttClientV5.publish(_topic, mqttMessage);
                future.complete(null);
            } catch (org.eclipse.paho.mqttv5.common.MqttException e) {
                future.completeExceptionally(e);
            }
            return future;
        }, MqttThreadPool.getExecutor());
    }

    private CompletableFuture<Void> connect() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (_mqttClient != null) {
            if (_mqttClient.isConnected()) {
                future.complete(null);
                return future;
            }
            MqttConnectOptions connOpts = new MqttConnectOptions();
            if (_credential instanceof PlainEndpointCredential) {
                PlainEndpointCredential plainCredential = (PlainEndpointCredential) _credential;
                connOpts.setUserName(plainCredential.getClientId());
                connOpts.setPassword(plainCredential.getClientSecret().toCharArray());
            }
            try {
                _mqttClient.connect(connOpts);
                future.complete(null);
            } catch (MqttException e) {
                _logger.error("Error connecting to MQTT broker: " + e.getMessage());
                future.completeExceptionally(e);
            }
        } else if (_mqttClientV5 != null) {
            if (_mqttClientV5.isConnected()) {
                future.complete(null);
                return future;
            }
            var connOpts = new org.eclipse.paho.mqttv5.client.MqttConnectionOptions();
            if (_credential instanceof PlainEndpointCredential) {
                PlainEndpointCredential plainCredential = (PlainEndpointCredential) _credential;
                connOpts.setUserName(plainCredential.getClientId());
                connOpts.setPassword(plainCredential.getClientSecret().getBytes());
            }
            try {
                _mqttClientV5.connect(connOpts);
                future.complete(null);
            } catch (org.eclipse.paho.mqttv5.common.MqttException e) {
                _logger.error("Error connecting to MQTT broker: " + e.getMessage());
                future.completeExceptionally(e);
            }
        }
        return future;
    }

    @Override
    public void close() {
        if (_mqttClient != null) {
            try {
                if (_mqttClient.isConnected()) {
                    _mqttClient.disconnect();
                    _mqttClient.close();
                }
                _mqttClient.close();
            } catch (MqttException e) {
                _logger.error("Error disconnecting from MQTT broker: " + e.getMessage());
            }
        } else if (_mqttClientV5 != null) {
            try {
                if (_mqttClientV5.isConnected()) {
                    _mqttClientV5.disconnect();
                }
                _mqttClientV5.close();
            } catch (org.eclipse.paho.mqttv5.common.MqttException e) {
                _logger.error("Error disconnecting from MQTT broker: " + e.getMessage());
            }
        }
    }
}