package com.enterprise.notification.client;

import com.enterprise.notification.common.ApiException;
import java.util.function.Supplier;
import org.springframework.web.client.ResourceAccessException;

/** Turns network failures (timeouts, connection refused) into a clear 503 for the API caller. */
final class RemoteCalls {

    private RemoteCalls() {
    }

    static <T> T call(String serviceName, Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException ex) {
            throw ApiException.serviceUnavailable(serviceName + " is not reachable right now. Please try again.");
        }
    }
}
