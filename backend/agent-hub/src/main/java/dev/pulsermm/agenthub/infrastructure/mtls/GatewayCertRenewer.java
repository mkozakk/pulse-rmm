package dev.pulsermm.agenthub.infrastructure.mtls;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GatewayCertRenewer {

    private static final Logger logger = LoggerFactory.getLogger(GatewayCertRenewer.class);

    private final GatewayCertBootstrap bootstrap;
    private final ReloadableX509KeyManager keyManager;

    public GatewayCertRenewer(GatewayCertBootstrap bootstrap, ReloadableX509KeyManager keyManager) {
        this.bootstrap = bootstrap;
        this.keyManager = keyManager;
    }

    @Scheduled(fixedDelayString = "${pulse.mtls.renew-check-interval-ms:3600000}",
               initialDelayString = "${pulse.mtls.renew-check-interval-ms:3600000}")
    public void checkAndRenew() {
        try {
            GatewayCertBootstrap.Bundle b = bootstrap.ensure();
            if (b.renewed()) {
                keyManager.reload();
                logger.info("Gateway server cert renewed and hot-reloaded");
            }
        } catch (Exception e) {
            logger.error("Cert renewal check failed; will retry on next interval", e);
        }
    }
}
