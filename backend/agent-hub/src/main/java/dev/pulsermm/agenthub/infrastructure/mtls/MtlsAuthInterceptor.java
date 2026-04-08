package dev.pulsermm.agenthub.infrastructure.mtls;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@GrpcGlobalServerInterceptor
public class MtlsAuthInterceptor implements ServerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(MtlsAuthInterceptor.class);
    private static final Pattern CN_PATTERN = Pattern.compile("CN=([^,]+)");

    private static final Set<String> METHODS_ALLOWED_WITHOUT_CERT = Set.of(
        "pulse.v1.AgentService/Enrol",
        "pulse.v1.AgentService/Ping",
        "grpc.health.v1.Health/Check"
    );

    private final RevocationChecker revocationChecker;

    public MtlsAuthInterceptor(RevocationChecker revocationChecker) {
        this.revocationChecker = revocationChecker;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String method = call.getMethodDescriptor().getFullMethodName();
        UUID endpointId = extractEndpointId(call);

        if (endpointId == null) {
            if (METHODS_ALLOWED_WITHOUT_CERT.contains(method)) {
                return next.startCall(call, headers);
            }
            logger.debug("Rejecting unauthenticated call to {}", method);
            call.close(Status.UNAUTHENTICATED.withDescription("client certificate required"), new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        }

        if (revocationChecker.isRevoked(endpointId)) {
            logger.info("Rejecting revoked endpoint {} on {}", endpointId, method);
            call.close(Status.UNAUTHENTICATED.withDescription("endpoint revoked"), new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        }

        Context ctx = Context.current().withValue(MtlsContext.ENDPOINT_ID, endpointId);
        return Contexts.interceptCall(ctx, call, headers, next);
    }

    private UUID extractEndpointId(ServerCall<?, ?> call) {
        SSLSession session = call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
        if (session == null) return null;
        try {
            Certificate[] peers = session.getPeerCertificates();
            if (peers.length == 0 || !(peers[0] instanceof X509Certificate x509)) return null;
            String dn = x509.getSubjectX500Principal().getName();
            Matcher m = CN_PATTERN.matcher(dn);
            if (!m.find()) return null;
            return UUID.fromString(m.group(1));
        } catch (SSLPeerUnverifiedException e) {
            return null;
        } catch (IllegalArgumentException e) {
            logger.warn("Peer cert CN is not a valid UUID");
            return null;
        }
    }
}
