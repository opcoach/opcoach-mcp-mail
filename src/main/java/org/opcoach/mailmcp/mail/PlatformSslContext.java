package org.opcoach.mailmcp.mail;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class PlatformSslContext {

    private static final List<String> WINDOWS_ROOT_STORES = List.of(
            "Windows-ROOT",
            "Windows-ROOT-LOCALMACHINE"
    );
    private static volatile Optional<SSLSocketFactory> cachedWindowsSocketFactory;

    private PlatformSslContext() {
    }

    static Optional<SSLSocketFactory> windowsAndJavaSocketFactory() {
        if (!shouldUseWindowsRoots(
                System.getProperty("os.name", ""),
                System.getProperty("javax.net.ssl.trustStore"),
                System.getProperty("javax.net.ssl.trustStoreType"))) {
            return Optional.empty();
        }

        Optional<SSLSocketFactory> result = cachedWindowsSocketFactory;
        if (result != null) {
            return result;
        }
        synchronized (PlatformSslContext.class) {
            result = cachedWindowsSocketFactory;
            if (result == null) {
                result = createWindowsAndJavaSocketFactory();
                cachedWindowsSocketFactory = result;
            }
            return result;
        }
    }

    static boolean shouldUseWindowsRoots(String osName, String configuredTrustStore, String configuredTrustStoreType) {
        return osName.toLowerCase(Locale.ROOT).contains("win")
                && isBlank(configuredTrustStore)
                && isBlank(configuredTrustStoreType);
    }

    private static Optional<SSLSocketFactory> createWindowsAndJavaSocketFactory() {
        try {
            List<X509TrustManager> trustManagers = new ArrayList<>();
            trustManagers.add(trustManager(null));
            for (String storeType : WINDOWS_ROOT_STORES) {
                loadTrustManager(storeType).ifPresent(trustManagers::add);
            }
            if (trustManagers.size() == 1) {
                return Optional.empty();
            }

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(
                    null,
                    new TrustManager[]{new CompositeX509TrustManager(trustManagers)},
                    null
            );
            return Optional.of(context.getSocketFactory());
        } catch (GeneralSecurityException exception) {
            return Optional.empty();
        }
    }

    private static Optional<X509TrustManager> loadTrustManager(String storeType) {
        try {
            KeyStore windowsRootStore = KeyStore.getInstance(storeType);
            windowsRootStore.load(null, null);
            return Optional.of(trustManager(windowsRootStore));
        } catch (GeneralSecurityException | IOException exception) {
            return Optional.empty();
        }
    }

    private static X509TrustManager trustManager(KeyStore keyStore) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore);
        for (TrustManager trustManager : factory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager x509TrustManager) {
                return x509TrustManager;
            }
        }
        throw new GeneralSecurityException("No X.509 trust manager is available.");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static final class CompositeX509TrustManager implements X509TrustManager {

        private final List<X509TrustManager> delegates;

        CompositeX509TrustManager(List<X509TrustManager> delegates) {
            if (delegates == null || delegates.isEmpty()) {
                throw new IllegalArgumentException("At least one trust manager is required.");
            }
            this.delegates = List.copyOf(delegates);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            checkTrusted(chain, authType, false);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            checkTrusted(chain, authType, true);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegates.stream()
                    .flatMap(delegate -> Arrays.stream(delegate.getAcceptedIssuers()))
                    .distinct()
                    .toArray(X509Certificate[]::new);
        }

        private void checkTrusted(X509Certificate[] chain, String authType, boolean server)
                throws CertificateException {
            CertificateException firstFailure = null;
            for (X509TrustManager delegate : delegates) {
                try {
                    if (server) {
                        delegate.checkServerTrusted(chain, authType);
                    } else {
                        delegate.checkClientTrusted(chain, authType);
                    }
                    return;
                } catch (CertificateException exception) {
                    if (firstFailure == null) {
                        firstFailure = exception;
                    } else {
                        firstFailure.addSuppressed(exception);
                    }
                }
            }
            if (firstFailure != null) {
                throw firstFailure;
            }
            throw new CertificateException("No trust manager accepted the certificate chain.");
        }
    }
}
