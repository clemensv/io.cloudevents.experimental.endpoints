package io.cloudevents.experimental.endpoints.mqtt;

public final class MqttProtocol {
    public static final String NAME = "mqtt";
    
    public static void register() {
        MqttConsumerEndpoint.register();
        MqttProducerEndpoint.register();
    }


}
