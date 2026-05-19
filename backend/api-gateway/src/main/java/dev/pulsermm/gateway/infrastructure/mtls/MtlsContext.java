package dev.pulsermm.gateway.infrastructure.mtls;

import io.grpc.Context;

import java.util.UUID;

public final class MtlsContext {
    public static final Context.Key<UUID> ENDPOINT_ID = Context.key("pulse.endpoint-id");

    private MtlsContext() {}
}
