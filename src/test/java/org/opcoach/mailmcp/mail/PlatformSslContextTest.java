package org.opcoach.mailmcp.mail;

import org.junit.jupiter.api.Test;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformSslContextTest {

    @Test
    void windowsRootsAreUsedOnlyWithoutAnExplicitJavaTrustStore() {
        assertTrue(PlatformSslContext.shouldUseWindowsRoots("Windows 11", null, null));
        assertTrue(PlatformSslContext.shouldUseWindowsRoots("Windows 11", "", ""));
        assertFalse(PlatformSslContext.shouldUseWindowsRoots("Linux", null, null));
        assertFalse(PlatformSslContext.shouldUseWindowsRoots(
                "Windows 11",
                "C:\\certs\\custom.p12",
                null
        ));
        assertFalse(PlatformSslContext.shouldUseWindowsRoots("Windows 11", null, "PKCS12"));
    }

    @Test
    void compositeTrustManagerAcceptsAChainTrustedByWindowsFallback() throws CertificateException {
        AtomicInteger fallbackCalls = new AtomicInteger();
        X509TrustManager fallback = acceptingTrustManager(fallbackCalls);
        PlatformSslContext.CompositeX509TrustManager composite =
                new PlatformSslContext.CompositeX509TrustManager(List.of(
                        rejectingTrustManager("Java truststore rejected the chain"),
                        fallback
                ));

        composite.checkServerTrusted(new X509Certificate[0], "RSA");

        assertEquals(1, fallbackCalls.get());
    }

    @Test
    void compositeTrustManagerRejectsAChainRejectedByEveryStore() {
        PlatformSslContext.CompositeX509TrustManager composite =
                new PlatformSslContext.CompositeX509TrustManager(List.of(
                        rejectingTrustManager("Java truststore rejected the chain"),
                        rejectingTrustManager("Windows truststore rejected the chain")
                ));

        CertificateException exception = assertThrows(
                CertificateException.class,
                () -> composite.checkServerTrusted(new X509Certificate[0], "RSA")
        );

        assertEquals("Java truststore rejected the chain", exception.getMessage());
        assertEquals(1, exception.getSuppressed().length);
    }

    private static X509TrustManager acceptingTrustManager(AtomicInteger calls) {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                calls.incrementAndGet();
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                calls.incrementAndGet();
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

    private static X509TrustManager rejectingTrustManager(String message) {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                throw new CertificateException(message);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                throw new CertificateException(message);
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}
