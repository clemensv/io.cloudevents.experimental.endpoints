package io.cloudevents.experimental.endpoints.mqtt;

import java.util.concurrent.ScheduledThreadPoolExecutor;

class MqttThreadPool {
    private static final ScheduledThreadPoolExecutor _executor = new ScheduledThreadPoolExecutor(8);

    public static ScheduledThreadPoolExecutor getExecutor() {
        return _executor;
    }
}
