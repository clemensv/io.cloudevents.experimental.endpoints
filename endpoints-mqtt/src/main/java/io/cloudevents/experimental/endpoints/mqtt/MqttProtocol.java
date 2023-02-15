package io.cloudevents.experimental.endpoints.mqtt;

public final class MqttProtocol {
    public static final String NAME = "mqtt";
    
    /**
     * Registers the MQTT protocol.
     */
    public static void register() {
        MqttConsumerEndpoint.register();
        MqttProducerEndpoint.register();
    }


}
