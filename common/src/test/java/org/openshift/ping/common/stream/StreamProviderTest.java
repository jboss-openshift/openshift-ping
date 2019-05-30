/*
 *  Copyright 2019 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package org.openshift.ping.common.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import org.junit.Test;

/**
 * Verify {@link TokenStreamProvider} and {@link CertificateStreamProvider} correctly parse all certificates from the file.
 *
 * @author Radoslav Husar
 * @see CLOUD-3228
 */
public class StreamProviderTest {

    private static String CA_FILE = StreamProviderTest.class.getResource("/certificates/ca.crt").getFile();

    @Test
    public void testTokenStreamProviderCaCert() throws Exception {
        testConfigureCaCert(TokenStreamProvider.configureCaCert(CA_FILE));
    }

    @Test
    public void testCertificateStreamProviderCaCert() throws Exception {
        testConfigureCaCert(CertificateStreamProvider.configureCaCert(CA_FILE));
    }

    private static void testConfigureCaCert(TrustManager[] trustManagers) {
        assertEquals(1, trustManagers.length);
        X509TrustManager trustManager = (X509TrustManager) trustManagers[0];
        X509Certificate[] acceptedIssuers = trustManager.getAcceptedIssuers();

        // Assert all 4 CA certs are parsed
        boolean allMatch = true;
        for (String cn : Arrays.asList("CN=kube-apiserver-localhost-signer", "CN=kube-apiserver-service-network-signer", "CN=ingress-operator@1559194394", "CN=kube-apiserver-lb-signer")) {
            boolean noneMatch = true;
            for (X509Certificate c : acceptedIssuers) {
                if (c.getSubjectDN().toString().startsWith(cn)) {
                    noneMatch = false;
                    break;
                }
            }
            if (noneMatch) {
                allMatch = false;
                break;
            }
        }
        assertTrue(allMatch);

// TODO replace the above once based on JDK8
//        assertTrue(Stream.of("CN=kube-apiserver-localhost-signer", "CN=kube-apiserver-service-network-signer", "CN=ingress-operator@1559194394", "CN=kube-apiserver-lb-signer")
//                .allMatch(cn -> certificates.stream()
//                        .anyMatch(c -> c.getSubjectDN().toString().startsWith(cn)))
//        );
    }
}
