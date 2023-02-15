// Copyright (c) Cloud Native Computing Foundation. See LICENSE for details
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

/**
 * This class is a producer endpoint implementation that uses MQTT 3.1.1 or MQTT
 * 5.0 as the transport. The class uses the Eclipse Paho MQTT library the MQTT
 * protocol support.
 * 
 * The class does not have a public constructor. Instead, instances are 
 * created by calling the static {@link io.cloudevents.experimental.endpoints.ProducerEndpoint#create(IEndpointCredential, String, Map, List)}
 * factory method on the base class.
 * 
 * The MQTT protocol support must be registered with the factory by calling the
 * static {@link MqttProtocol#register()} method once during application startup.
 * 
 * See the {@link io.cloudevents.experimental.endpoints.ProducerEndpoint} class for
 * more information about the common message model and the factory.
 * 
 * The "options" parameter to the factory method is optionally used to specify
 * MQTT-specific options. The following options are supported:
 * - "topic" - the MQTT topic to subscribe to. If not specified, the topic is 
 *   taken from the "path" portion of the endpoint URI. 
 * - "qos" - the MQTT quality of service level to use. If not specified, the
 *   default value of 0 is used.
 * 
 * The "protocol" parameter to the factory method is used to specify the MQTT
 * protocol version to use. The following values are supported:
 * - "mqtt" - MQTT 5.0
 * - "mqtt/3.1.1" - MQTT 3.1.1
 * - "mqtt/5.0" - MQTT 5.0
 * 
 * The "endpoints" parameter to the factory method is used to specify the MQTT
 * broker endpoints to connect to. Only one endpoint is permitted. The URI scheme
 * must be "mqtt" or "mqtts". If the scheme is "mqtts", the endpoint is configured to 
 * use TLS.  
 * 
 * In addition to the CloudEvents message model, the MQTT consumer endpoint also
 * supports MQTT messages (PUBLISH packets) directly. 
 * 
 * As with the underlying Paho MQTT library, MQTT 5.0 and MQTT 3.1.1 are handled 
 * separately. 
 * 
 * @see io.cloudevents.experimental.endpoints.ProducerEndpoint
 * 
 * Example for CloudEvents:
 * 
 * <pre>
 * {@code
 * // Create a producer endpoint that connects to the MQTT broker at
 * // "mqtt://localhost:1883" and attaches to the "mytopic" node.
 * 
 * var credential = new PlainEndpointCredential("username", "password");
 * var endpoint = ConsumerEndpoint.create(
 *                      credential, "mqtt", 
 *                      Map.of("topic", "mytopic"), 
 *                      List.of(URI.create("mqtt://localhost:1883")));
 * 
 * // Send a CloudEvent message to the consumer endpoint.
 * var event = CloudEventBuilder.v1()
 *                  .withId(UUID.randomUUID().toString())
 *                  .withSource(URI.create("/myapp"))
 *                  .withType("myevent")
 *                  .withData("text/plain", "Hello, world!".getBytes())
 *                  .build();
 * 
 * endpoint.sendAsync(event, Encoding.BINARY, EventFormatProvider.getInstance().resolveFormat("json")).join();
 * }
 * </pre>
 * 
 * Example for MQTT 5.0 messages:
 * 
 * <pre>
 * {@code
 * // Create a producer endpoint that connects to the MQTT broker at
 * // "mqtt://localhost:1883" and attaches to the "mytopic" node.
 * 
 * var credential = new PlainEndpointCredential("username", "password");
 * var endpoint = ConsumerEndpoint.create(
 *                      credential, "mqtt", 
 *                      Map.of("topic", "mytopic"), 
 *                      List.of(URI.create("mqtt://localhost:1883")));
 * 
 * // Send an MQTT message to the consumer endpoint.
 * var message = new MqttMessage("Hello, world!".getBytes());
 * message.setQos(0);
 * message.setRetained(false);
 * 
 * endpoint.sendAsync(message).join();
 * 
 */

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
    

    /**
     * Private consztuctor. Instances are created by the factory method.
     * @param version the MQTT protocol version
     * @param credential the credential to use to connect to the MQTT broker
     * @param options the MQTT-specific options
     * @param endpoints the MQTT broker endpoints
     */
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

    /** 
     * {@inheritDoc}
     */
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

    /**
     * Sends a MQTT 3.1.1 message to the MQTT broker
     * @param mqttMessage the message to send
     * @return a completable future that completes when the message has been sent
     */
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

    /** 
     * Sends a MQTT 5.0 message to the MQTT broker
     * @param mqttMessage the message to send
     * @return a completable future that completes when the message has been sent
     */
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

    /**
     * Connects to the MQTT broker
     * @return a completable future that completes when the connection has been established
     */
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

    /**
     * {@inheritDoc}
     */
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