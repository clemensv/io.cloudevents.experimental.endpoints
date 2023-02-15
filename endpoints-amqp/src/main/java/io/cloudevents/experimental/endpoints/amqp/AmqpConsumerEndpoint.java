// (c) Cloud Native Computing Foundation. See LICENSE for details

package io.cloudevents.experimental.endpoints.amqp;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.apache.qpid.proton.message.Message;

import io.cloudevents.amqp.ProtonAmqpMessageFactory;
import io.cloudevents.experimental.endpoints.ConsumerEndpoint;
import io.cloudevents.experimental.endpoints.IEndpointCredential;

/**
 * This class is a consumer endpoint implementation that uses AMQP 1.0 as the
 * transport. The class uses the Apache Qpid Proton-J library and the AMQP
 * extensions of the CloudEvents SDK for Java to implement the AMQP 1.0
 * protocol support.
 * 
 * The class does not have a public constructor. Instead, instances are 
 * created by calling the static {@link io.cloudevents.experimental.endpoints.ConsumerEndpoint#create(IEndpointCredential, Map, List)}
 * factory method on the base class.
 * 
 * The AMQP protocol support must be registered with the factory by calling the
 * static {@link #register()} method once during application startup.
 * 
 * See the {@link io.cloudevents.experimental.endpoints.ConsumerEndpoint} class for
 * more information about the common message model and the factory.
 * 
 * The "options" parameter to the factory method is optionally used to specify
 * the AMQP "node" (queue, topic, subscription, etc.) to which the consumer
 * endpoint will attach. The node name can also be specified as a path in the
 * endpoint URI. If the node name is specified in both the options and the URI,
 * the value in the options takes precedence.
 * 
 * In addition to the CloudEvents message model, the AMQP consumer endpoint also
 * supports AMQP messages directly. AMQP messages are first delivered to
 * subscribers who registered by calling the
 * {@link #subscribe(DispatchMessageAsync)} method. The subscriber provides a
 * callback interface implementation. The
 * {@link DispatchMessageAsync#onMessage(Message, MessageContext) } callback
 * method is invoked with the AMQP message and {@link MessageContext} object
 * that allows the subscriber to control the message disposition. If subscribers
 * exist at this level, the message will only be made available to the
 * CloudEvents subscribers if none of the subscribers calls any of the
 * disposition methods on the context. Only one disposition method may be called
 * once per message. An AMQP subscriber must call one of the disposition methods
 * if it handled the message. There is no auto-accept behavior.
 * 
 * If the message is CloudEvents compliant, either in binary or structured mode
 * as indicated by the content-type, it will be converted and delivered to the
 * CloudEvents subscribers. 
 * 
 * If any of the CloudEvents subscribers or the AMQP subscribers throw an 
 * exception, the message will be automatically released. If all CloudEvents 
 * subscribers return normally, the message will be automatically accepted.
 * 
 * If the message has not been disposed by an AMQP subscriber and if it is not
 * CloudEvents compliant, it will be automatically rejected because it cannot be
 * dispatched by this endpoint.
 * 
 * 
 * CloudEvents Example:
 * 
 * <pre>
 * {@code
 * // Create a consumer endpoint that connects to the AMQP broker at
 * // "amqp://localhost:5672" and attaches to the "myqueue" node.
 * 
 * var credential = new PlainEndpointCredential("username", "password");
 * var endpoint = ConsumerEndpoint.create(
 *                      credential, "amqp", 
 *                      Map.of("node", "myqueue"), 
 *                      List.of(URI.create("amqp://localhost:5672")));
 * endpoint.subscribe((cloudEvent) -> {
 *    // Handle the event
 * });
 * endpoint.startAsync();
 * }
 * </pre>
 * 
 * AMQP Example:
 * 
 * <pre>
 * {@code
 * // Create a consumer endpoint that connects to the AMQP broker at
 * // "amqp://localhost:5672" and attaches to the "myqueue" node.
 * // Cast the endpoint to the AMQP-specific subclass to access the
 * // AMQP-specific methods.
 * 
 * var credential = new PlainEndpointCredential("username", "password");
 * var endpoint = (AmqpConsumerEndpoint)ConsumerEndpoint.create(
 *                      credential, "amqp", 
 *                      Map.of("node", "myqueue"), 
 *                      List.of(URI.create("amqp://localhost:5672")));
 * endpoint.subscribe((message, context) -> {
 *     // Handle the message
 *     context.accept();
 * });
 * endpoint.startAsync();
 * }
 * </pre>
 * 
 * @see io.cloudevents.experimental.endpoints.ConsumerEndpoint
 */
public class AmqpConsumerEndpoint extends ConsumerEndpoint {

    private static final Logger logger = LogManager.getLogger(AmqpConsumerEndpoint.class);
    private AmqpProtonClient _client;
    String node;

    /**
     * Private constructor. Instances are created by the factory method.
     * @param credential The endpoint credential.
     * @param options The endpoint options.
     * @param endpoints The endpoint URIs.
     */
    AmqpConsumerEndpoint(IEndpointCredential credential, Map<String, String> options,
            List<URI> endpoints) {
        
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

    static String ERROR_LOG_TEMPLATE = "Error in AMQPConsumerEndpoint: %s";
    static String VERBOSE_LOG_TEMPLATE = "AMQPConsumerEndpoint: %s";

    /**
     * Callback interface for AMQP messages.
     */
    public interface DispatchMessageAsync {
        /**
         * Callback method for AMQP messages.
         * @param amqpMessage The AMQP message.
         * @param context The message context.
         */
        void onMessage(Message amqpMessage, MessageContext context);
    }

    private final Set<DispatchMessageAsync> subscribers = new HashSet<>();
    private AmqpProtonSubscriber _subscriber;

    /**
     * Subscribe to AMQP messages.
     */
    public void subscribe(DispatchMessageAsync subscriber) {
        subscribers.add(subscriber);
    }

    /**
     * Unsubscribe from AMQP messages.
     */
    public void unsubscribe(DispatchMessageAsync subscriber) {
        subscribers.remove(subscriber);
    }

    /**
     * Start the consumer endpoint.
     * @return A future that completes when the endpoint is started.
     * @see io.cloudevents.experimental.endpoints.ConsumerEndpoint#startAsync()     * 
     */
    @Override
    public CompletableFuture<Void> startAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            this._subscriber = _client.createSubscriber(node, (amqpMessage, context) -> {

                for (DispatchMessageAsync d : subscribers) {
                    d.onMessage(amqpMessage, context);
                }
                if (!context.getIsSettled()) {
                    boolean hasCEProperty = false;
                    var props = amqpMessage.getApplicationProperties();
                    if (props != null) {
                        for (var prop : props.getValue().keySet()) {
                            if (prop.toString().startsWith("cloudEvents")) {
                                hasCEProperty = true;
                                break;
                            }
                        }
                    }
                    if (amqpMessage.getContentType() != null
                            && (amqpMessage.getContentType().startsWith("application/cloudevents") || hasCEProperty)) {
                        // let's try to parse the message as a CloudEvent
                        try {
                            var reader = ProtonAmqpMessageFactory.createReader(amqpMessage);
                            this.deliver(reader.toEvent());
                            context.accept();
                        } catch (Exception e) {
                            logger.warn(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
                            context.release();
                        }
                    } else {
                        context.reject();
                    }
                }
            });
            future.complete(null);
        } catch (Exception e) {
            logger.warn(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * Stop the consumer endpoint.
     * @return A future that completes when the endpoint is stopped.
     * @see io.cloudevents.experimental.endpoints.ConsumerEndpoint#stopAsync()     * 
     */
    @Override
    public CompletableFuture<Void> stopAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            this._subscriber.close();
            future.complete(null);
        } catch (Exception e) {
            logger.warn(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    protected <T> void deliver(T message) {
        super.deliver(message);
    }

    /**
     * Register the AMQP consumer endpoint factory.
     */
    public static void register() {
        ConsumerEndpoint.addConsumerEndpointFactoryHook((credential, protocol, options, endpoints) -> {
            if (protocol.equals("amqp")) {
                return new AmqpConsumerEndpoint(credential, options, endpoints);
            }
            return null;
        });
    }

    @Override
    public void close() {
        try {
            this.stopAsync().get();
        } catch (Exception e) {
            logger.warn(String.format(ERROR_LOG_TEMPLATE, e.getMessage()));
        }
    }

}
