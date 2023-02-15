/*package io.cloudevents.experimental.endpoints.mqtt;

Sad story: 

This is an attempt to map the Paho MQTT client API to the Java 8
CompletableFuture API. Unfortunately, I was not able to make it work yet due
some obscure interactions of thread pool and timing issues between
CompletableFuture and Paho.

import java.util.concurrent.CompletableFuture;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;

class MqttFuture extends CompletableFuture<Void>
        implements IMqttActionListener, org.eclipse.paho.mqttv5.client.MqttActionListener {
   
    public MqttFuture() {
        this.toString();
    }

    public static MqttFuture toFuture(IMqttToken token) {
        var actionCallback = token.getActionCallback();
        MqttFuture future = null;
        if (actionCallback instanceof MqttFuture) {
            future = (MqttFuture) actionCallback;
        } else {
            future = new MqttFuture();
            token.setActionCallback(future);
        }
        if (token.isComplete() && !future.isDone()) {
            if (token.getException() == null) {
                future.complete(null);
            } else {
                future.completeExceptionally(token.getException());
            }
        }
        return future;
    }    

    public static MqttFuture toFuture(org.eclipse.paho.mqttv5.client.IMqttToken token) {
        var actionCallback = token.getActionCallback();
        MqttFuture future = null;
        if (actionCallback instanceof MqttFuture) {
            future = (MqttFuture) actionCallback;
        } else if (token.getUserContext() instanceof MqttFuture) {
            future = (MqttFuture) token.getUserContext();
        } else {
            future = new MqttFuture();
            token.setActionCallback(future);
        }
        if (token.isComplete() && !future.isDone()) {
            if (token.getException() == null) {
                future.complete(null);
            } else {
                future.completeExceptionally(token.getException());
            }
        }
        return future;
    }
    
    @Override
    public void onSuccess(org.eclipse.paho.mqttv5.client.IMqttToken asyncActionToken) {
        this.complete(null);
    }

    @Override
    public void onFailure(org.eclipse.paho.mqttv5.client.IMqttToken asyncActionToken, Throwable exception) {
        this.completeExceptionally(exception);        
    }

    @Override
    public void onSuccess(IMqttToken asyncActionToken) {
        this.complete(null);        
    }

    @Override
    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        this.completeExceptionally(exception);        
    }
}
*/