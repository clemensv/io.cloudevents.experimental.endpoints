package io.cloudevents.experimental.endpoints.mqtt;

import io.cloudevents.experimental.endpoints.mqtt.MqttConsumerEndpoint;
import io.cloudevents.experimental.endpoints.mqtt.MqttProducerEndpoint;

public final class MqttProtocol {
    public static final String NAME = "mqtt";

    static {
        MqttConsumerEndpoint.register();
        MqttProducerEndpoint.register();
    }
}
