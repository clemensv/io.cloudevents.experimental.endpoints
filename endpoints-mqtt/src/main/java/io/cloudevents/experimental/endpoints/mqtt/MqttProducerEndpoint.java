package io.cloudevents.experimental.endpoints.mqtt;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.message.Encoding;
import io.cloudevents.experimental.endpoints.IEndpointCredential;
import io.cloudevents.experimental.endpoints.PlainEndpointCredential;
import io.cloudevents.experimental.endpoints.ProducerEndpoint;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MqttProducerEndpoint extends ProducerEndpoint {

    enum MqttVersion {
        MQTT_3_1_1,
        MQTT_5_0
    }
    private Logger _logger;
    private IEndpointCredential _credential;
    private List<URI> _endpoints;
    private String _topic;
    private int _qos;
    private MqttAsyncClient _mqttClient;
    private org.eclipse.paho.mqttv5.client.MqttAsyncClient _mqttClientV5;
    private boolean _isConnected;
    private MqttVersion _protocolVersion;

    MqttProducerEndpoint(MqttVersion version, Logger logger, IEndpointCredential credential, Map<String, String> options, List<URI> endpoints)
    {
        this._protocolVersion = version;
        this._logger = logger;
        this._credential = credential;
        this._endpoints = endpoints;
        if (options.containsKey("topic")) {
            this._topic = options.get("topic");
        }
        if (options.containsKey("qos")) {
            this._qos = Integer.parseInt(options.get("qos"));
        }
        if (version == MqttVersion.MQTT_3_1_1) {
            try {
                _mqttClient = new MqttAsyncClient(_endpoints.get(0).toString(), MqttAsyncClient.generateClientId());
            } catch (MqttException e) {
                _logger.severe("Error creating MQTT client: " + e.getMessage());
            }
        } else if (version == MqttVersion.MQTT_5_0) {
            try {
                _mqttClientV5 = new org.eclipse.paho.mqttv5.client.MqttAsyncClient(_endpoints.get(0).toString(), MqttAsyncClient.generateClientId());
            } catch (org.eclipse.paho.mqttv5.common.MqttException e) {
                _logger.severe("Error creating MQTT client: " + e.getMessage());
            }
        }
    }

    public static void register() {
        addProducerEndpointFactoryHook((logger, credential, protocol, options, endpoints) -> {
            if (protocol.equals("mqtt/5.0") || protocol.equals("mqtt")) {
                return new MqttProducerEndpoint(MqttVersion.MQTT_5_0, logger, credential, options, endpoints);
            } else if (protocol.equals("mqtt/3.1.1")) {
                return new MqttProducerEndpoint(MqttVersion.MQTT_3_1_1, logger, credential, options, endpoints);
            }            
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> sendAsync(CloudEvent cloudEvent, Encoding contentMode, EventFormat formatter)
            throws Exception {
        
        if (_protocolVersion == MqttVersion.MQTT_3_1_1) {
            if ( contentMode == Encoding.BINARY) {
                throw new Exception("MQTT 3.1.1 does not support binary encoding");
            }
            var mqttMessage = new MqttMessage();
            mqttMessage.setQos(_qos);
            mqttMessage.setPayload(formatter.serialize(cloudEvent));
            return sendAsync(mqttMessage);
        } else if ( _protocolVersion == MqttVersion.MQTT_5_0) {
            var mqttMessage = new org.eclipse.paho.mqttv5.common.MqttMessage();
            mqttMessage.setQos(_qos);
            if (contentMode == Encoding.BINARY) {
                mqttMessage.setPayload(cloudEvent.getData().toBytes());
            } else {
                var props = new org.eclipse.paho.mqttv5.common.packet.MqttProperties();
                var userProperties = new ArrayList<org.eclipse.paho.mqttv5.common.packet.UserProperty>();
                for (var entry : cloudEvent.getAttributeNames()) {
                    if ( entry != "data" ) {
                        userProperties.add(new org.eclipse.paho.mqttv5.common.packet.UserProperty(entry, cloudEvent.getAttribute(entry).toString()));
                    }
                }
                props.setContentType(formatter.serializedContentType());
                props.setUserProperties(null);
                mqttMessage.setProperties(props);
                mqttMessage.setPayload(formatter.serialize(cloudEvent));                
            }
            
            return sendAsync(mqttMessage);
        }
        return CompletableFuture.completedFuture(null);
    }
    
    public CompletableFuture<Void> sendAsync(MqttMessage mqttMessage) throws Exception {
        if (!_isConnected) {
            connect();
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        var token = _mqttClient.publish(_topic, mqttMessage);
        token.setActionCallback(new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                future.complete(null);
            }
            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    public CompletableFuture<Void> sendAsync(org.eclipse.paho.mqttv5.common.MqttMessage mqttMessage) throws Exception {
        if (!_isConnected) {
            connect();
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        var token = _mqttClientV5.publish(_topic, mqttMessage);
        token.setActionCallback(new org.eclipse.paho.mqttv5.client.MqttActionListener() {
            @Override
            public void onSuccess(org.eclipse.paho.mqttv5.client.IMqttToken asyncActionToken) {
                future.complete(null);
            }
            @Override
            public void onFailure(org.eclipse.paho.mqttv5.client.IMqttToken asyncActionToken, Throwable exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }
    
    private synchronized void connect() {
        if (_isConnected) {
            return;
        }
        if (_mqttClient != null) {
            MqttConnectOptions connOpts = new MqttConnectOptions();
            if ( _credential instanceof PlainEndpointCredential) {
                PlainEndpointCredential plainCredential = (PlainEndpointCredential)_credential;
                connOpts.setUserName(plainCredential.getClientId());
                connOpts.setPassword(plainCredential.getClientSecret().toCharArray());
            }
            try {
                _mqttClient.connect(connOpts);
                _isConnected = true;
            } catch (MqttException e) {
                _logger.severe("Error connecting to MQTT broker: " + e.getMessage());
            }
        } else if (_mqttClientV5 != null) {
            var connOpts = new org.eclipse.paho.mqttv5.client.MqttConnectionOptions();
            if ( _credential instanceof PlainEndpointCredential) {
                PlainEndpointCredential plainCredential = (PlainEndpointCredential)_credential;
                connOpts.setUserName(plainCredential.getClientId());
                connOpts.setPassword(plainCredential.getClientSecret().getBytes());
            }
            try {
                _mqttClientV5.connect(connOpts);
                _isConnected = true;
            } catch (org.eclipse.paho.mqttv5.common.MqttException e) {
                _logger.severe("Error connecting to MQTT broker: " + e.getMessage());
            }
        }
    }
}