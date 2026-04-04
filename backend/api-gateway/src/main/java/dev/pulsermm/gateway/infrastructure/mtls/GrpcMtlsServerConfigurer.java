package dev.pulsermm.gateway.infrastructure.mtls;

import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcMtlsServerConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(GrpcMtlsServerConfigurer.class);

    private final GatewayCertBootstrap bootstrap;

    public GrpcMtlsServerConfigurer(GatewayCertBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Bean
    public ReloadableX509KeyManager reloadableKeyManager() throws Exception {
        GatewayCertBootstrap.Bundle b = bootstrap.ensure();
        return new ReloadableX509KeyManager(b.certFile().toPath(), b.keyFile().toPath());
    }

    @Bean
    public GrpcServerConfigurer mtlsConfigurer(ReloadableX509KeyManager keyManager) {
        return serverBuilder -> {
            if (!(serverBuilder instanceof NettyServerBuilder netty)) {
                logger.warn("gRPC server is not Netty-based; skipping mTLS config");
                return;
            }
            try {
                GatewayCertBootstrap.Bundle b = bootstrap.ensure();
                ReloadableKeyManagerFactory kmf = new ReloadableKeyManagerFactory(keyManager);
                SslContextBuilder builder = SslContextBuilder.forServer(kmf)
                    .trustManager(b.caFile())
                    // OPTIONAL so the TLS handshake succeeds for first-boot Enrol RPCs
                    // (agent has no cert yet). MtlsAuthInterceptor rejects non-Enrol RPCs
                    // that arrive without a valid cert.
                    .clientAuth(ClientAuth.OPTIONAL)
                    .sslProvider(SslProvider.JDK);
                netty.sslContext(GrpcSslContexts.configure(builder, SslProvider.JDK).build());
                logger.info("gRPC server configured with mTLS (JDK provider, hot-reload enabled)");
            } catch (Exception e) {
                throw new IllegalStateException("configuring gRPC mTLS", e);
            }
        };
    }
}
