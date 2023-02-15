package io.cloudevents.experimental.endpoints.amqp;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;

import org.apache.qpid.proton.engine.BaseHandler;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.CoreHandler;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Event;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.Sender;
import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.engine.SslDomain.Mode;
import org.apache.qpid.proton.engine.SslDomain.VerifyMode;
import org.apache.qpid.proton.message.Message;
import org.apache.qpid.proton.reactor.FlowController;
import org.apache.qpid.proton.reactor.Handshaker;
import org.apache.qpid.proton.reactor.Reactor;
import io.cloudevents.experimental.endpoints.IEndpointCredential;
import io.cloudevents.experimental.endpoints.PlainEndpointCredential;


/**
 * This class implements a simplified client for AMQP 1.0 wrapping the Proton-J
 * library. It is used by the AMQP 1.0 endpoint implementations.
 */
public class AmqpProtonClient {
    private Connection _connection;
    private URI _endpoint;
    private IEndpointCredential _credential;
    private Logger _logger = LogManager.getLogger(AmqpProtonClient.class.getName());
    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final BufferPool BUFFER_POOL = new BufferPool(BUFFER_SIZE, 5);
    private int tag = 0;
    private Reactor _reactor;
    private Thread _reactorThread;
    private CompletableFuture<Connection> _connectionFuture;


    public static class BufferPool {
        private final int bufferSize;
        private final LinkedList<ByteBuffer> pool;

        public BufferPool(int bufferSize, int poolSize) {
            this.bufferSize = bufferSize;
            this.pool = new LinkedList<>();
            for (int i = 0; i < poolSize; i++) {
                pool.add(ByteBuffer.allocate(bufferSize));
            }
        }

        public ByteBuffer getBuffer() {
            synchronized (pool) {
                return pool.isEmpty() ? ByteBuffer.allocate(bufferSize) : pool.removeFirst();
            }
        }

        public void returnBuffer(ByteBuffer buffer) {
            synchronized (pool) {
                pool.add(buffer.clear());
            }
        }
    }

    /**
     * Creates a new instance of the AmqpProtonClient class.
     * @param endpoint The endpoint URI.
     * @param credential The endpoint credential to use.
     */
    public AmqpProtonClient(URI endpoint, IEndpointCredential credential) {
        this._endpoint = endpoint;
        this._credential = credential;
        this._connectionFuture = new CompletableFuture<>();
    }


    /** 
     * This handler is used to process events from the Proton-J reactor.
     */
    class ReactorHandler extends BaseHandler {

        private AmqpProtonClient client;

        ReactorHandler(AmqpProtonClient client) {
            this.client = client;
            add(new Handshaker());
            add(new FlowController());
        }

        /**
         * initilaizes the connection to the AMQP 1.0 endpoint.
         */
        @Override
        public void onReactorInit(Event event) {
            _reactor = event.getReactor();
            _reactor.connectionToHost(client._endpoint.getHost(), client._endpoint.getPort(), this);
            _logger.info(String.format("connecting to %s:%d", client._endpoint.getHost(), client._endpoint.getPort()));
        }

        /**
         * This method is called when the connection has 
         * been bound to the transport, but before the 
         * connection has been opened. 
         */
        @Override
        public void onConnectionBound(Event event) {

            var connection = event.getConnection();
            Transport transport = connection.getTransport();
            try {
                // configure TLS if the endpoint uses the amqps scheme
                if (client._endpoint.getScheme().equals("amqps")) {
                    var sslContext = SSLContext.getInstance("TLSv1.2");
                    var sslDomain = Proton.sslDomain();
                    sslDomain.init(Mode.CLIENT);
                    sslDomain.setSslContext(sslContext);
                    sslDomain.setPeerAuthentication(VerifyMode.VERIFY_PEER);
                    transport.ssl(sslDomain);
                    _logger.info("using TLS");
                }

                // configure SASL if we know how to configure the credential
                var sasl = transport.sasl();
                sasl.setRemoteHostname(connection.getHostname());
                if (client._credential instanceof PlainEndpointCredential) {
                    sasl.setMechanisms("PLAIN");
                    sasl.plain(((PlainEndpointCredential) client._credential).getClientId(),
                            ((PlainEndpointCredential) client._credential).getClientSecret());
                    _logger.info("using PLAIN SASL");
                } else {
                    sasl.setMechanisms("ANOYMOUS");
                }
                sasl.client();

            } catch (Exception e) {
                _logger.error(String.format("error while trying to connect to %s:%d: %s", _endpoint.getHost(),
                        _endpoint.getPort(), e.getMessage()));
            }

            client._connection = connection;
            client._connectionFuture.complete(client._connection);
            _logger.info(String.format("Connection bound. State: %s", client._connection.getLocalState()));
        }

    }

    /** 
     * Initializes the Proton-J reactor.
     */
    private synchronized void initReactor(CoreHandler handler) throws IOException {
        if (_reactor != null) {
            return;
        }
        _logger.info("initializing reactor");
        _reactor = Proton.reactor(handler);
        int port = _endpoint.getPort();
        if (port == -1) {
            port = 5671;
            if (_endpoint.getScheme().equals("amqp")) {
                port = 5672;
            }
        }
        runReactor();
    }

    /**
     * Runs the Proton-J reactor in a separate thread.
     */
    private void runReactor() {
        _reactorThread = new Thread(() -> {
            try {
                _reactor.run();
            } catch (Exception e) {
                _logger.error(String.format("error while trying to process reactor: %s", e.getMessage()));
            }
        });
        _reactorThread.start();
    }

    /** 
     * Creates an AMQP message subscriber instance.
     * @param node The node to subscribe to. This may be the address of a queue
     * or a topic or a durable topic subscription. The address is interpreted
     * by the AMQP 1.0 endpoint.
     * @param handler The message handler to use.
     * @return The AMQP message subscriber instance.
     */
    public AmqpProtonSubscriber createSubscriber(String node, MessageHandler handler) throws Exception {
        _logger.info(String.format("creating subscriber for node: %s", node));
        var sub = new SubscriberImpl(this, node, handler);
        initReactor(sub);
        return sub;
    }

    /** 
     * Creates an AMQP message sender instance.
     * @param node The node to send messages to. This may be the address of a queue
     * or a topic. The address is interpreted by the AMQP 1.0 endpoint.
     * @return The AMQP message sender instance.
     */
    public AmqpProtonSender createSender(String node) throws Exception {
        _logger.info(String.format("creating sender for node: %s", node));
        var sender = new SenderImpl(this, node);
        initReactor(sender);
        return sender;
    }

    /** 
     * Implements AmqpProtonSender
     * @see AmqpProtonSender
     */
    public class SenderImpl extends ReactorHandler implements AmqpProtonSender {
        private String node;
        private CompletableFuture<Sender> _senderFuture = new CompletableFuture<>();

        public SenderImpl(AmqpProtonClient client, String node) {
            super(client);
            this.node = node;
        }

        /**
         * This method is called when the network connection has been
         * established and the AMQP connection needs to be initialized.
         */
        @Override
        public void onConnectionInit(Event event) {
            try {
                var connection = event.getConnection();
                connection.setContainer(RandomStringUtils.randomAlphanumeric(16));
                _logger.info(String.format("Connection init. State: %s", connection.getLocalState()));
                var session = connection.session();
                var sender = session.sender(RandomStringUtils.randomAlphanumeric(16));
                Target target = new Target();
                target.setAddress(node);
                sender.setTarget(target);
                sender.setContext(this);
                connection.open();
                session.open();
                sender.open();
                _senderFuture.complete(sender);
                _logger.info(String.format("Sender init. State: %s", sender.getLocalState()));
            } catch (Exception e) {
                _senderFuture.completeExceptionally(e);
                _logger.error(String.format("error while trying to init sender: %s", e.getMessage()));
            }
        }

        /** 
         * Sends a message.
         * @param amqpMessage The message to send.
         * @return A future that completes when the message has been sent.
         */
        public CompletableFuture<Void> sendAsync(Message amqpMessage) {
            try {
                var sender = _senderFuture.get();
                var buffer = BUFFER_POOL.getBuffer();
                try {
                    int len = amqpMessage.encode(buffer.array(), 0, buffer.limit());
                    Delivery dlv = sender.delivery(String.format("s-%s", ++tag).getBytes());
                    _logger.info(String.format("Sending message. Tag: %s", dlv.getTag()));
                    sender.send(buffer.array(), 0, len);
                    sender.advance();
                    dlv.settle();
                } catch (Exception e) {
                    _logger.error(String.format("error while trying to send message: %s", e.getMessage()));
                    throw e;
                } finally {
                    BUFFER_POOL.returnBuffer(buffer);
                }
                return CompletableFuture.completedFuture(null);
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public void close() throws Exception {
            if (_senderFuture.isDone()) {
                _senderFuture.get().close();
            }
        }

    }

    /** 
     * Implements AmqpProtonSubscriber
     * @see AmqpProtonSubscriber
     */
    public class SubscriberImpl extends ReactorHandler implements AmqpProtonSubscriber {
        private MessageHandler handler;
        private String node;
        private CompletableFuture<Receiver> _receiverFuture = new CompletableFuture<>();

        public SubscriberImpl(AmqpProtonClient client, String node, MessageHandler handler) {
            super(client);
            this.handler = handler;
            this.node = node;
        }

        /**
         * This method is called when the network connection has been
         * established and the AMQP connection needs to be initialized.
         */
        @Override
        public void onConnectionInit(Event event) {
            try {
                var connection = event.getConnection();
                connection.setContainer(RandomStringUtils.randomAlphanumeric(16));
                var session = connection.session();
                var receiver = session.receiver(RandomStringUtils.randomAlphanumeric(16));
                Source source = new Source();
                source.setAddress(node);
                receiver.setSource(source);
                receiver.setContext(this);

                connection.open();
                session.open();
                receiver.open();
                _receiverFuture.complete(receiver);
                receiver.flow(10);
                _logger.info(String.format("Receiver init. State: %s", receiver.getLocalState()));
            } catch (Exception e) {
                _logger.error(String.format("error while trying to create receiver: %s", e.getMessage()));
            }
        }

        /**
         * This method is called when a message has been received.
         */
        @Override
        public void onDelivery(Event event) {
            var delivery = event.getDelivery();
            var receiver = event.getReceiver();

            _logger.info(String.format("Delivery. State: %s", delivery.getLocalState()));
            var buffer = BUFFER_POOL.getBuffer();
            try {
                if (delivery.isReadable()) {
                    _logger.info(String.format("Delivery is readable. State: %s", delivery.getLocalState()));
                    int read = receiver.recv(buffer.array(), 0, buffer.capacity());
                    if (read > 0) {
                        buffer.flip();

                        // decode the message
                        Message amqpMessage = Proton.message();
                        amqpMessage.decode(buffer.array(), 0, read);

                        try {
                            // create a context that can be used to accept/reject/release the message
                            var handlerContext = new MessageContext() {
                                boolean isSettled = false;

                                @Override
                                public void accept() {
                                    _logger.info(
                                            String.format("Delivery accepted. State: %s", delivery.getLocalState()));
                                    delivery.disposition(Accepted.getInstance());
                                    isSettled = true;
                                }

                                @Override
                                public void reject() {
                                    _logger.info(
                                            String.format("Delivery rejected. State: %s", delivery.getLocalState()));
                                    Rejected rejected = new Rejected();
                                    rejected.setError(
                                            new ErrorCondition(AmqpError.ILLEGAL_STATE, "Rejected by client"));
                                    delivery.disposition(rejected);
                                    isSettled = true;
                                }

                                @Override
                                public void release() {
                                    _logger.info(
                                            String.format("Delivery released. State: %s", delivery.getLocalState()));
                                    delivery.disposition(Released.getInstance());
                                    isSettled = true;
                                }

                                @Override
                                public void modify(boolean deliveryFailed, boolean undeliverableHere) {
                                    _logger.info(
                                            String.format("Delivery modified. State: %s", delivery.getLocalState()));
                                    Modified modified = new Modified();
                                    modified.setDeliveryFailed(deliveryFailed);
                                    modified.setUndeliverableHere(undeliverableHere);
                                    delivery.disposition(modified);
                                    isSettled = true;
                                }

                                @Override
                                public boolean getIsSettled() {
                                    return isSettled || delivery.remotelySettled();
                                }
                            };
                            AmqpProtonSubscriber subscriber = (AmqpProtonSubscriber) receiver.getContext();
                            subscriber.getHandler().handle(amqpMessage, handlerContext);
                            if (!handlerContext.getIsSettled() && !delivery.remotelySettled()) {
                                // if the handler did not settle the message, we will reject it
                                delivery.disposition(new Rejected());
                            }
                        } catch (Exception e) {
                            _logger.error(
                                    String.format("error while trying to handle message: %s", e.getMessage()));
                        }
                        delivery.settle();
                        receiver.advance();
                        receiver.flow(1);
                    }
                }
            } catch (Exception e) {
                _logger.error(String.format("error while trying to receive message: %s", e.getMessage()));
            } finally {
                BUFFER_POOL.returnBuffer(buffer);
            }
        }


        public MessageHandler getHandler() {
            return handler;
        }

        @Override
        public void close() throws Exception {
            if (_receiverFuture.isDone()) {
                _receiverFuture.get().close();
            }
        }
    }

    public void close() {

        try {
            if (this._reactor != null) {
                this._reactor.stop();
                this._reactorThread.join();
                this._reactorThread = null;
            }
        } catch (Exception e) {
            _logger.error(String.format("error while trying to close reactor: %s", e.getMessage()));
        }
        try {
            if (this._connection != null) {
                this._connection.close();
                this._connection = null;
            }
        } catch (Exception e) {
            _logger.error(String.format("error while trying to close connection: %s", e.getMessage()));
        }

    }
}
