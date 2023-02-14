package io.cloudevents.experimental.endpoints.amqp;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.qpid.proton.message.Message;

import io.cloudevents.CloudEvent;
import io.cloudevents.amqp.ProtonAmqpMessageFactory;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.message.Encoding;
import io.cloudevents.experimental.endpoints.IEndpointCredential;
import io.cloudevents.experimental.endpoints.ProducerEndpoint;

public class AmqpProducerEndpoint extends ProducerEndpoint {

    /**
     *
     */
    private AmqpProtonClient _client;
    String node;
    private AmqpProtonSender _sender;
     
    AmqpProducerEndpoint(IEndpointCredential credential, Map<String, String> options, List<URI> endpoints)
    {
       if (endpoints == null || endpoints.size() == 0) {
            throw new IllegalArgumentException("endpoints");
        }
        var endpoint = endpoints.get(0);
        if (options != null && options.containsKey("node")) {
            node = options.get("node");
        } else {
            node = endpoint.getPath();
        }
        if (credential == null) {
            throw new IllegalArgumentException("credential");
        }
        this._client = new AmqpProtonClient(endpoint, credential);
    }

    public static void register() {

        addProducerEndpointFactoryHook((credential, protocol, options, endpoints) -> {
            if (protocol.equals("amqp")) {
                return new AmqpProducerEndpoint(credential, options, endpoints);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> sendAsync(CloudEvent cloudEvent, Encoding contentMode, EventFormat formatter) {
        var writer = ProtonAmqpMessageFactory.createWriter();
        
        Message amqpMessage = null;
        if (contentMode == Encoding.BINARY) {
            amqpMessage = writer.writeBinary(cloudEvent);
        } else if (contentMode == Encoding.STRUCTURED) {
            amqpMessage = writer.writeStructured(cloudEvent, formatter);
        }       
        return sendAsync(amqpMessage);
    }
    
    public CompletableFuture<Void> sendAsync(Message amqpMessage) {
        try {
            if (_sender == null) {
                _sender = _client.createSender(node);
            }
            return _sender.sendAsync(amqpMessage);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    @Override
    public void close() throws Exception {
        if (_sender != null) {
            _sender.close();
            _sender = null;
        }
        if (_client != null) {
            _client.close();
            _client = null;
        }
        
    }
}
