package io.cloudevents.experimental.endpoints.amqp;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.azure.core.amqp.models.AmqpAnnotatedMessage;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.message.Encoding;
import io.cloudevents.experimental.endpoints.IEndpointCredential;
import io.cloudevents.experimental.endpoints.ProducerEndpoint;

public class AmqpProducerEndpoint extends ProducerEndpoint  {

    private Logger _logger;
    private IEndpointCredential _credential;
    private List<URI> _endpoints;
    private String _node;

    AmqpProducerEndpoint(Logger logger, IEndpointCredential credential, Map<String, String> options, List<URI> endpoints)
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
            if (protocol.equals("amqp")) {
                return new AmqpProducerEndpoint(logger, credential, options, endpoints);
            }
            return null;
        });
    }

    @Override
    public void sendAsync(CloudEvent cloudEvent, Encoding contentMode, EventFormat formatter) throws Exception {
        // TODO Auto-generated method stub

    }
    
    public void sendAsync(AmqpAnnotatedMessage amqpMessage) throws Exception {
        // TODO Auto-generated method stub

    }
    
}
