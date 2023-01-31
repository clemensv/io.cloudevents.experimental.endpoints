package io.cloudevents.experimental.endpoints.mqtt;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.message.Encoding;
import io.cloudevents.experimental.endpoints.IEndpointCredential;
import io.cloudevents.experimental.endpoints.ProducerEndpoint;

public class MqttProducerEndpoint extends ProducerEndpoint  {

    private Logger _logger;
    private IEndpointCredential _credential;
    private List<URI> _endpoints;
    private String _node;

    MqttProducerEndpoint(Logger logger, IEndpointCredential credential, Map<String, String> options, List<URI> endpoints)
    {
        this._logger = logger;
        this._credential = credential;
        this._endpoints = endpoints;
        if (options.containsKey("node")) {
            this._node = options.get("node");
        }
    }

    public static void register() {
        addProducerEndpointFactoryHook((logger, credential, protocol, options, endpoints) -> {
            if (protocol.equals("mqtt")) {
                return new MqttProducerEndpoint(logger, credential, options, endpoints);
            }
            return null;
        });
    }

    @Override
    public void sendAsync(CloudEvent cloudEvent, Encoding contentMode, EventFormat formatter) throws Exception {
        // TODO Auto-generated method stub

    }
    
    public void sendAsync(org.eclipse.paho.client.mqttv3.MqttMessage mqttMessage) throws Exception {
        // TODO Auto-generated method stub

    }

    public void sendAsync(org.eclipse.paho.mqttv5.common.packet.MqttWireMessage mqttMessage) throws Exception {
        // TODO Auto-generated method stub

    }
    
}
