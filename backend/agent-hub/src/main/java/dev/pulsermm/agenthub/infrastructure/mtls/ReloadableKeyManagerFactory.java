package dev.pulsermm.agenthub.infrastructure.mtls;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import java.security.KeyStore;

public class ReloadableKeyManagerFactory extends KeyManagerFactory {

    public ReloadableKeyManagerFactory(ReloadableX509KeyManager km) {
        super(new Spi(km), null, "ReloadableX509");
        try {
            init((KeyStore) null, null);
        } catch (Exception e) {
            throw new IllegalStateException("initializing reloadable KMF", e);
        }
    }

    private static class Spi extends KeyManagerFactorySpi {
        private final KeyManager[] managers;

        Spi(ReloadableX509KeyManager km) {
            this.managers = new KeyManager[]{km};
        }

        @Override
        protected void engineInit(KeyStore ks, char[] password) {
        }

        @Override
        protected void engineInit(ManagerFactoryParameters spec) {
        }

        @Override
        protected KeyManager[] engineGetKeyManagers() {
            return managers;
        }
    }
}
