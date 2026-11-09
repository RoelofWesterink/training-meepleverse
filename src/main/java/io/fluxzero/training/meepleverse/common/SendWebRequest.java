package io.fluxzero.training.meepleverse.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import io.fluxzero.sdk.tracking.handling.validation.ValidationException;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.sdk.web.WebRequestSettings;
import io.fluxzero.sdk.web.WebResponse;
import io.fluxzero.training.meepleverse.authentication.Sender;

import java.util.Set;

import static java.lang.String.format;

public abstract class SendWebRequest {
    protected abstract WebRequest.Builder buildRequest(WebRequest.Builder requestBuilder, Sender sender);

    @HandleQuery
    @HandleCommand
    public Object handle(Sender sender) {
        WebRequest request = buildRequest(WebRequest.builder(), sender).build()
                .addMetadata("$thread", Thread.currentThread().getName());
        WebResponse webResponse = Fluxzero.get().webRequestGateway().sendAndWait(request, requestSettings());
        return handleResponse(webResponse, request);
    }

    @JsonIgnore
    protected String getProxyConsumer() {
        return "forward-proxy";
    }

    protected WebRequestSettings requestSettings() {
        return WebRequestSettings.builder().consumer(getProxyConsumer()).build();
    }

    protected Object handleResponse(WebResponse response, WebRequest request) {
        String body = bodyAsString(response);
        switch (response.getStatus()) {
            case 400 -> throw new ValidationException(
                    format("Invalid request (status %s) to endpoint %s. Response: %s.",
                           response.getStatus(), request.getPath(), body), Set.of());
            case 401 -> throw new UnauthorizedException(
                    format("Unauthorized (status %s) for endpoint %s. Response: %s.",
                           response.getStatus(), request.getPath(), body));
            case 403, 429 -> throw new IllegalCommandException(
                    format("Illegal request (status %s) to endpoint %s. Response: %s.",
                           response.getStatus(), request.getPath(), body));
            case 404 -> {
                return null;
            }
        }
        if (response.getStatus() >= 300) {
            throw new IllegalStateException(
                    format("Error (status %s) when invoking endpoint %s. Response: %s.",
                           response.getStatus(), request.getPath(), body));
        }
        return body;
    }

    protected String bodyAsString(WebResponse response) {
        if (response.getPayload() == null) {
            return null;
        }
        if (response.getPayload() instanceof byte[] bytes) {
            return new String(bytes);
        }
        if (response.getPayload() instanceof String s) {
            return s;
        }
        throw new IllegalStateException("Unexpected response: " + response.getPayload());
    }
}
