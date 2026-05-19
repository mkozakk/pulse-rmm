package dev.pulsermm.gateway.infrastructure.mtls;

import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "pulse.mtls.enabled", havingValue = "true")
public class GrpcMtlsServerConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(GrpcMtlsServerConfigurer.class);

    private final GatewayCertBootstrap bootstrap;

    public GrpcMtlsServerConfigurer(GatewayCertBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Bean
    public GrpcServerConfigurer mtlsConfigurer() {
        return serverBuilder -> {
            if (!(serverBuilder instanceof NettyServerBuilder netty)) {
                logger.warn("gRPC server is not Netty-based; skipping mTLS config");
                return;
            }
            try {
                GatewayCertBootstrap.Bundle b = bootstrap.ensure();
                netty.sslContext(
                    GrpcSslContexts.configure(
                        GrpcSslContexts.forServer(b.certFile(), b.keyFile())
                            .trustManager(b.caFile())
                            .clientAuth(ClientAuth.OPTIONAL),
                        SslProvider.OPENSSL
                    ).build()
                );
                logger.info("gRPC server configured with mTLS (ClientAuth.OPTIONAL)");
            } catch (Exception e) {
                throw new IllegalStateException("configuring gRPC mTLS", e);
            }
        };
    }
}
