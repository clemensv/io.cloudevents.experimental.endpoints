// implement the stubs using Eclipse Paho MQTT client:

package io.cloudevents.experimental.endpoints.mqtt;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.experimental.endpoints.ConsumerEndpoint;
import io.cloudevents.experimental.endpoints.IEndpointCredential;
import io.cloudevents.experimental.endpoints.PlainEndpointCredential;
import io.cloudevents.jackson.JsonFormat;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;


public class MqttConsumerEndpoint extends ConsumerEndpoint {

    private IEndpointCredential _credential;
    private List<URI> _endpoints;
    private String[] _topics;
    private MqttVersion _protocolVersion;
    private int[] _qos;
    MqttAsyncClient _mqttClientV3;
    org.eclipse.paho.mqttv5.client.MqttAsyncClient _mqttClientV5;
    private String _implicitContentType;    

    enum MqttVersion {
        MQTT_3_1_1,
        MQTT_5_0
    }

    public MqttConsumerEndpoint(MqttVersion protocolVersion, Logger logger, IEndpointCredential credential,
            Map<String, String> options,
            List<URI> endpoints) {
        super(logger);

        this._protocolVersion = protocolVersion;
        this._credential = credential;
        this._endpoints = endpoints;
        if (options.containsKey("topic")) {
            _topics = options.get("topic").split(",");
        }
        if (options.containsKey("qos")) {
            var qosStrings = options.get("qos").split(",");
            _qos = new int[_topics.length];
            for (int i = 0; i < _topics.length; i++) {
                _qos[i] = Integer.parseInt(qosStrings[Math.max(i, qosStrings.length - 1)]);
            }
        }
        if ( options.containsKey("implicitContentType") ) {
            _implicitContentType = options.get("implicitContentType");
        };
    }

    static String ERROR_LOG_TEMPLATE = "Error in mqttConsumerEndpoint: {0}";
    static String VERBOSE_LOG_TEMPLATE = "mqttConsumerEndpoint: {0}";

    public interface DispatchMqttV3MessageAsync {
        void onMessage(org.eclipse.paho.client.mqttv3.MqttMessage message, Logger logger);
    }

    private final Set<DispatchMqttV3MessageAsync> mqttV3Subscribers = new HashSet<>();

    public void subscribe(DispatchMqttV3MessageAsync subscriber) {
        mqttV3Subscribers.add(subscriber);
    }

    public void unsubscribe(DispatchMqttV3MessageAsync subscriber) {
        mqttV3Subscribers.remove(subscriber);
    }

    public interface DispatchMqttV5MessageAsync {
        void onMessage(org.eclipse.paho.mqttv5.common.MqttMessage message, Logger logger);
    }

    private final Set<DispatchMqttV5MessageAsync> mqttV5Subscribers = new HashSet<>();

    public void subscribe(DispatchMqttV5MessageAsync subscriber) {
        mqttV5Subscribers.add(subscriber);
    }

    public void unsubscribe(DispatchMqttV5MessageAsync subscriber) {
        mqttV5Subscribers.remove(subscriber);
    }

    @Override
    public CompletableFuture<Void> startAsync() {

        var that = this;
        CompletableFuture<Void> future = new CompletableFuture<Void>();
        if (_protocolVersion == MqttVersion.MQTT_3_1_1) {
            
            try {
                _mqttClientV3 = new MqttAsyncClient(_endpoints.get(0).toString(), MqttClient.generateClientId());
                MqttConnectOptions options = new MqttConnectOptions();
                if (_credential instanceof PlainEndpointCredential) {
                    PlainEndpointCredential plainCredential = (PlainEndpointCredential) _credential;
                    options.setUserName(plainCredential.getClientId());
                    options.setPassword(plainCredential.getClientSecret().toCharArray());
                }
                _mqttClientV3.connect(options, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        try {
                            _mqttClientV3.subscribe(_topics, _qos, null, new IMqttActionListener() {
                                @Override
                                public void onSuccess(IMqttToken asyncActionToken) {
                                    try {
                                        _mqttClientV3.setCallback(new MqttV3Callback(that));
                                        future.complete(null);
                                    } catch (Exception e) {
                                        future.completeExceptionally(e);
                                        logger.severe(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
                                    }
                                }
                                @Override
                                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                                    future.completeExceptionally(exception);
                                    logger.severe(String.format(ERROR_LOG_TEMPLATE, exception.getMessage()));
                                }
                            });
                        } catch (MqttException e) {
                            future.completeExceptionally(e);
                            logger.severe(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
                        }
                    }
                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        future.completeExceptionally(exception);
                        logger.severe(String.format(ERROR_LOG_TEMPLATE, exception.getMessage()));
                    }
                });
            } catch (MqttException e) {
                future.completeExceptionally(e);
                logger.severe(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
            }
        } else if (_protocolVersion == MqttVersion.MQTT_5_0) {
                
                try {
                    _mqttClientV5 = new org.eclipse.paho.mqttv5.client.MqttAsyncClient(_endpoints.get(0).toString(), MqttClient.generateClientId());
                    org.eclipse.paho.mqttv5.client.MqttConnectionOptions options = new org.eclipse.paho.mqttv5.client.MqttConnectionOptions();
                    if (_credential instanceof PlainEndpointCredential) {
                        PlainEndpointCredential plainCredential = (PlainEndpointCredential) _credential;
                        options.setUserName(plainCredential.getClientId());
                        options.setPassword(plainCredential.getClientSecret().getBytes(StandardCharsets.UTF_8));
                    }
                    _mqttClientV5.connect(options, null, new org.eclipse.paho.mqttv5.client.MqttActionListener() {
                        @Override
                        public void onSuccess(org.eclipse.paho.mqttv5.client.IMqttToken asyncActionToken) {
                            try {
                                _mqttClientV5.subscribe(_topics, _qos, null, new org.eclipse.paho.mqttv5.client.MqttActionListener() {
                                    @Override
                                    public void onSuccess(org.eclipse.paho.mqttv5.client.IMqttToken asyncActionToken) {
                                        try {
                                            _mqttClientV5.setCallback(new MqttV5Callback(that));
                                            future.complete(null);
                                        } catch (Exception e) {
                                            future.completeExceptionally(e);
                                            logger.severe(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
                                        }
                                    }
                                    @Override
                                    public void onFailure(org.eclipse.paho.mqttv5.client.IMqttToken asyncActionToken, Throwable exception) {
                                        future.completeExceptionally(exception);
                                        logger.severe(String.format(ERROR_LOG_TEMPLATE, exception.getMessage()));
                                    }
                                });
                            } catch (org.eclipse.paho.mqttv5.common.MqttException e) {
                                future.completeExceptionally(e);
                                logger.severe(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
                            }
                        }
                        @Override
                        public void onFailure(org.eclipse.paho.mqttv5.client.IMqttToken asyncActionToken, Throwable exception) {
                            future.completeExceptionally(exception);
                            logger.severe(String.format(ERROR_LOG_TEMPLATE, exception.getMessage()));
                        }
                    });
                } catch (org.eclipse.paho.mqttv5.common.MqttException e) {
                    future.completeExceptionally(e);
                    logger.severe(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
                }
            } else {
                future.completeExceptionally(new Exception("Unsupported protocol version"));
        }
        return future;
    }

    @Override
    public CompletableFuture<Void> stopAsync() {
        CompletableFuture<Void> future = new CompletableFuture<Void>();
        if (_mqttClientV3 != null) {
            
            try {
                var result = _mqttClientV3.disconnect();
                result.setActionCallback(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        future.complete(null);
                    }
                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        future.completeExceptionally(exception);
                    }
                });
                _mqttClientV3.close();
                _mqttClientV3 = null;
            } catch (MqttException e) {
                logger.severe(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
                throw new RuntimeException(e);
            }
            
        } else if (_mqttClientV5 != null) {
            try {
                var result = _mqttClientV5.disconnect();
                result.setActionCallback(new org.eclipse.paho.mqttv5.client.MqttActionListener() {
                    @Override
                    public void onSuccess(org.eclipse.paho.mqttv5.client.IMqttToken asyncActionToken) {
                        future.complete(null);
                    }

                    @Override
                    public void onFailure(org.eclipse.paho.mqttv5.client.IMqttToken asyncActionToken,
                            Throwable exception) {
                        future.completeExceptionally(exception);
                    }
                });
                _mqttClientV5.close();
                _mqttClientV5 = null;

            } catch (org.eclipse.paho.mqttv5.common.MqttException e) {
                logger.severe(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
                throw new RuntimeException(e);
            }
        }
        else {
            future.complete(null);
        }
        return future;
    }

    @Override
    protected <T> void deliver(T message) {
        super.deliver(message);
    }

    public static void register() {
        ConsumerEndpoint.addConsumerEndpointFactoryHook((logger, credential, protocol, options, endpoints) -> {
            if (protocol.equals("mqtt") || protocol.equals("mqtt/5.0")) {
                return new MqttConsumerEndpoint(MqttVersion.MQTT_5_0, logger, credential, options, endpoints);
            } else if (protocol.equals("mqtt/3.1.1")) {
                return new MqttConsumerEndpoint(MqttVersion.MQTT_3_1_1, logger, credential, options, endpoints);
            } else {
                throw new IllegalArgumentException("Unknown protocol: " + protocol);
            }
        });
    }

    class MqttV3Callback implements MqttCallback {

        private MqttConsumerEndpoint endpoint;

        MqttV3Callback(MqttConsumerEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void connectionLost(Throwable cause) {
            endpoint.logger.warning("Connection lost: " + cause.getMessage());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            for (DispatchMqttV3MessageAsync subscriber : endpoint.mqttV3Subscribers) {
                subscriber.onMessage(message, logger);
            }

            var contentType = _implicitContentType != null ? _implicitContentType : JsonFormat.CONTENT_TYPE;
            if (contentType != null) {
                if (contentType.startsWith("application/cloudevents")) {
                    var eventFormat = EventFormatProvider.getInstance().resolveFormat(contentType);
                    if (eventFormat != null) {
                        var event = eventFormat.deserialize(message.getPayload());
                        endpoint.deliver(event);
                    } else {
                        logger.warning(topic + " received message with unsupported CloudEvents encoding: " + contentType);
                    }
                } else {
                    logger.warning(topic + " received message with unsupported content type: " + contentType);
                }
            }
        }

        @Override
        public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
            
        }
    }

    class MqttV5Callback implements org.eclipse.paho.mqttv5.client.MqttCallback {

        private MqttConsumerEndpoint endpoint;

        MqttV5Callback(MqttConsumerEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void authPacketArrived(int arg0, org.eclipse.paho.mqttv5.common.packet.MqttProperties arg1) {
            // won't do anything at the moment
            endpoint.logger.warning("Auth packet arrived unexpectedly");
        }

        @Override
        public void connectComplete(boolean arg0, String arg1) {
            endpoint.logger.info("Connection complete");
        }

        @Override
        public void deliveryComplete(org.eclipse.paho.mqttv5.client.IMqttToken arg0) {
            
        }

        @Override
        public void disconnected(org.eclipse.paho.mqttv5.client.MqttDisconnectResponse arg0) {
            endpoint.logger.warning("Disconnected: " + arg0.getReasonString());
        }

        @Override
        public void messageArrived(String topic, org.eclipse.paho.mqttv5.common.MqttMessage message) throws Exception {
            for (DispatchMqttV5MessageAsync subscriber : endpoint.mqttV5Subscribers) {
                subscriber.onMessage(message, endpoint.logger);
            }
            var contentType = message.getProperties().getContentType();
            if (contentType != null) {
                if (contentType.startsWith("application/cloudevents")) {
                    var eventFormat = EventFormatProvider.getInstance().resolveFormat(contentType);
                    if (eventFormat != null) {
                        var event = eventFormat.deserialize(message.getPayload());
                        endpoint.deliver(event);
                    }
                } else {
                    var userProps = message.getProperties().getUserProperties();
                    // find whether the message is a CloudEvent by looking for the specversion and type user properties
                    if (userProps != null) {
                        String specVersion = null;
                        String type = null;
                        String dataContentType = null;
                        CloudEventBuilder builder = CloudEventBuilder.v1();
                        for (var prop : userProps) {
                            builder.withContextAttribute(prop.getKey(), prop.getValue());
                            if (prop.getKey().equals("specversion")) {
                                specVersion = prop.getValue();
                            } else if (prop.getKey().equals("type")) {
                                type = prop.getValue();
                            } else if (prop.getKey().equals("datacontenttype")) {
                                dataContentType = prop.getValue();
                            }
                        }
                        if ( dataContentType == null) {
                            builder.withDataContentType(contentType);
                        }
                        if (specVersion != null && type != null) {
                            // required for this to be a CloudEvent
                            builder.withData(message.getPayload());
                            endpoint.deliver(builder.build());
                        }
                    }                    
                }
            }
        }

        @Override
        public void mqttErrorOccurred(org.eclipse.paho.mqttv5.common.MqttException exception) {
            endpoint.logger.warning("MQTT error occurred: " + exception.getMessage());
        }

    }
}
