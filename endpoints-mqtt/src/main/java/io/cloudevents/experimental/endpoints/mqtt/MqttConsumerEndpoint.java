// implement the stubs using Eclipse Paho MQTT client:

package io.cloudevents.experimental.endpoints.mqtt;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.experimental.endpoints.ConsumerEndpoint;
import io.cloudevents.experimental.endpoints.IEndpointCredential;
import io.cloudevents.experimental.endpoints.PlainEndpointCredential;
import io.cloudevents.jackson.JsonFormat;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttConsumerEndpoint extends ConsumerEndpoint {

    private static final Logger logger = LogManager.getLogger(MqttConsumerEndpoint.class);

    private IEndpointCredential _credential;
    private URI _endpoint;
    private String[] _topics;
    private MqttVersion _protocolVersion;
    private int[] _qos;
    MqttClient _mqttClientV3;
    org.eclipse.paho.mqttv5.client.MqttClient _mqttClientV5;
    private String _implicitContentType;

    enum MqttVersion {
        MQTT_3_1_1,
        MQTT_5_0
    }

    public MqttConsumerEndpoint(MqttVersion protocolVersion, IEndpointCredential credential,
            Map<String, String> options,
            List<URI> endpoints) {

        this._protocolVersion = protocolVersion;
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
            logger.error(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
            throw new RuntimeException(e);
        }

        if (options.containsKey("topic")) {
            _topics = options.get("topic").split(",");
        }
        if (options.containsKey("qos")) {
            var qosStrings = options.get("qos").split(",");
            _qos = new int[_topics.length];
            for (int i = 0; i < _topics.length; i++) {
                _qos[i] = Integer.parseInt(qosStrings[Math.max(i, qosStrings.length - 1)]);
            }
        } else {
            _qos = new int[_topics.length];
            for (int i = 0; i < _topics.length; i++) {
                _qos[i] = 0;
            }
        }

        if (options.containsKey("implicitContentType")) {
            _implicitContentType = options.get("implicitContentType");
        }
        ;
    }

    static String ERROR_LOG_TEMPLATE = "Error in mqttConsumerEndpoint: {0}";
    static String VERBOSE_LOG_TEMPLATE = "mqttConsumerEndpoint: {0}";

    public interface DispatchMqttV3MessageAsync {
        void onMessage(org.eclipse.paho.client.mqttv3.MqttMessage message);
    }

    private final Set<DispatchMqttV3MessageAsync> mqttV3Subscribers = new HashSet<>();

    public void subscribe(DispatchMqttV3MessageAsync subscriber) {
        mqttV3Subscribers.add(subscriber);
    }

    public void unsubscribe(DispatchMqttV3MessageAsync subscriber) {
        mqttV3Subscribers.remove(subscriber);
    }

    public interface DispatchMqttV5MessageAsync {
        void onMessage(org.eclipse.paho.mqttv5.common.MqttMessage message);
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

        if (_protocolVersion == MqttVersion.MQTT_3_1_1) {

            try {
                _mqttClientV3 = new MqttClient(_endpoint.toString(), MqttClient.generateClientId(),
                        new MemoryPersistence(), MqttThreadPool.getExecutor());
                _mqttClientV3.setCallback(new MqttV3Callback(this));
                MqttConnectOptions options = new MqttConnectOptions();
                if (_credential instanceof PlainEndpointCredential) {
                    PlainEndpointCredential plainCredential = (PlainEndpointCredential) _credential;
                    options.setUserName(plainCredential.getClientId());
                    options.setPassword(plainCredential.getClientSecret().toCharArray());
                }

                logger.info("Connecting to " + _endpoint);
                _mqttClientV3.connect(options);
                _mqttClientV3.subscribe(_topics, _qos);
                return CompletableFuture.completedFuture(null);
            } catch (MqttException e) {
                logger.error(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
                return CompletableFuture.failedFuture(e);
            }
        } else if (_protocolVersion == MqttVersion.MQTT_5_0) {

            try {

                _mqttClientV5 = new org.eclipse.paho.mqttv5.client.MqttClient(_endpoint.toString(),
                        MqttClient.generateClientId(), new org.eclipse.paho.mqttv5.client.persist.MemoryPersistence(),
                        MqttThreadPool.getExecutor());
                _mqttClientV5.setCallback(new MqttV5Callback(this));
                org.eclipse.paho.mqttv5.client.MqttConnectionOptions options = new org.eclipse.paho.mqttv5.client.MqttConnectionOptions();
                if (_credential instanceof PlainEndpointCredential) {
                    PlainEndpointCredential plainCredential = (PlainEndpointCredential) _credential;
                    options.setUserName(plainCredential.getClientId());
                    options.setPassword(plainCredential.getClientSecret().getBytes(StandardCharsets.UTF_8));
                }

                logger.info("Connecting to " + _endpoint);

                try {
                    _mqttClientV5.connect(options);
                    _mqttClientV5.subscribe(_topics, _qos);
                    return CompletableFuture.completedFuture(null);
                } catch (Exception e) {
                    return CompletableFuture.failedFuture(e);
                }

            } catch (org.eclipse.paho.mqttv5.common.MqttException e) {
                logger.error(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
                return CompletableFuture.failedFuture(e);
            }
        } else {
            return CompletableFuture.failedFuture(new Exception("Unsupported protocol version"));
        }
    }

    @Override
    public CompletableFuture<Void> stopAsync() {
        if (_mqttClientV3 != null) {

            try {
                _mqttClientV3.disconnect();
                _mqttClientV3.close();
                _mqttClientV3 = null;
                return CompletableFuture.completedFuture(null);
            } catch (MqttException e) {
                logger.error(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
                return CompletableFuture.failedFuture(e);
            }

        } else if (_mqttClientV5 != null) {
            try {
                _mqttClientV5.disconnect();
                _mqttClientV5.close();
                _mqttClientV5 = null;
                return CompletableFuture.completedFuture(null);
            } catch (org.eclipse.paho.mqttv5.common.MqttException e) {
                logger.error(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalStateException("Not started");
        }
    }

    @Override
    protected <T> void deliver(T message) {
        super.deliver(message);
    }

    public static void register() {
        ConsumerEndpoint.addConsumerEndpointFactoryHook((credential, protocol, options, endpoints) -> {
            if (protocol.compareToIgnoreCase("mqtt") == 0 || protocol.compareToIgnoreCase("mqtt/5.0") == 0) {
                return new MqttConsumerEndpoint(MqttVersion.MQTT_5_0, credential, options, endpoints);
            } else if (protocol.compareToIgnoreCase("mqtt/3.1.1") == 0) {
                return new MqttConsumerEndpoint(MqttVersion.MQTT_3_1_1, credential, options, endpoints);
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
            logger.warn("Connection lost: " + cause.getMessage());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            for (DispatchMqttV3MessageAsync subscriber : endpoint.mqttV3Subscribers) {
                try {
                    subscriber.onMessage(message);
                } catch (Exception e) {
                    logger.warn("Error delivering message to subscriber: " + e.getMessage());
                }
            }

            var contentType = _implicitContentType != null ? _implicitContentType : JsonFormat.CONTENT_TYPE;
            if (contentType != null) {
                if (contentType.startsWith("application/cloudevents")) {
                    var eventFormat = EventFormatProvider.getInstance().resolveFormat(contentType);
                    if (eventFormat != null) {
                        var event = eventFormat.deserialize(message.getPayload());
                        endpoint.deliver(event);
                    } else {
                        logger.warn( topic + " received message with unsupported CloudEvents encoding: " + contentType);
                    }
                } else {
                    logger.warn(topic + " received message with unsupported content type: " + contentType);
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
            logger.warn("Auth packet arrived unexpectedly");
        }

        @Override
        public void connectComplete(boolean arg0, String arg1) {
            logger.info("Connection complete");
        }

        @Override
        public void deliveryComplete(org.eclipse.paho.mqttv5.client.IMqttToken arg0) {
            logger.info("Delivery complete");
        }

        @Override
        public void disconnected(org.eclipse.paho.mqttv5.client.MqttDisconnectResponse arg0) {
            logger.warn("Disconnected: " + arg0.getReasonString());
        }

        @Override
        public void messageArrived(String topic, org.eclipse.paho.mqttv5.common.MqttMessage message) throws Exception {
            logger.info("Message arrived: " + message);
            for (DispatchMqttV5MessageAsync subscriber : endpoint.mqttV5Subscribers) {
                try {
                subscriber.onMessage(message);
                } catch (Exception e) {
                    logger.warn("Error while dispatching message to subscriber", e);
                }
            }
            var contentType = message.getProperties().getContentType();
            if (contentType != null && contentType.startsWith("application/cloudevents")) {
                try {
                    var eventFormat = EventFormatProvider.getInstance().resolveFormat(contentType);
                    if (eventFormat != null) {
                        var event = eventFormat.deserialize(message.getPayload());
                        try {
                            endpoint.deliver(event);
                        } catch (Exception e) {
                            logger.warn("Error while dispatching CloudEvent to subscriber", e);
                        }
                    }
                } catch (Exception e) {
                    logger.warn(topic + " received message with unsupported CloudEvents encoding: " + contentType);
                }
            } else {
                try {
                    var userProps = message.getProperties().getUserProperties();
                    // find whether the message is a CloudEvent by looking for the specversion and
                    // type user properties
                    if (userProps != null) {
                        String specVersion = null;
                        String type = null;
                        String dataContentType = null;
                        CloudEventBuilder builder = CloudEventBuilder.v1();
                        for (var prop : userProps) {
                            if (prop.getKey().equals("specversion")) {
                                specVersion = prop.getValue();
                            } else {
                                builder.withContextAttribute(prop.getKey(), prop.getValue());
                                if (prop.getKey().equals("type")) {
                                    type = prop.getValue();
                                } else if (prop.getKey().equals("datacontenttype")) {
                                    dataContentType = prop.getValue();
                                }
                            }
                        }
                        if (dataContentType == null) {
                            builder.withDataContentType(contentType);
                        }
                        if (specVersion != null && type != null) {
                            // required for this to be a CloudEvent
                            builder.withData(message.getPayload());
                            try {
                                endpoint.deliver(builder.build());
                            } catch (Exception e) {
                                logger.warn("Error delivering incoming binary CloudEvent: " + e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing incoming binary CloudEvent: " + e.getMessage());
                }
            }
        }

        @Override
        public void mqttErrorOccurred(org.eclipse.paho.mqttv5.common.MqttException exception) {
            logger.warn("MQTT error occurred: " + exception.getMessage());
        }

    }

    @Override
    public void close() {
        if (_mqttClientV3 != null) {
            try {
                _mqttClientV3.disconnect();
                _mqttClientV3.close();
            } catch (MqttException e) {
                logger.error(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
            }
        }
        if (_mqttClientV5 != null) {
            try {
                _mqttClientV5.disconnect();
                _mqttClientV5.close();
            } catch (org.eclipse.paho.mqttv5.common.MqttException e) {
                logger.error(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
            }
        }
    }
}
