/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdds.security.x509.certificate.authority;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import org.apache.hadoop.hdds.security.SecurityConfig;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

/**
 * Certificate Approver interface is used to inspectCSR a certificate.
 */
public interface CertificateApprover {
  /**
   * Approves a Certificate Request based on the policies of this approver.
   *
   * @param csr - Certificate Signing Request.
   * @return - Future that will contain Void if the certificate is considered valid otherwise an exception.
   */
  CompletableFuture<Void> inspectCSR(PKCS10CertificationRequest csr);

  /**
   * Sign function signs a Certificate.
   * @param config - Security Config.
   * @param caPrivate - CAs private Key.
   * @param caCertificate - CA Certificate.
   * @param validFrom - Begin Date
   * @param validTill - End Date
   * @param certificationRequest - Certification Request.
   * @param scmId - SCM id.
   * @param clusterId - Cluster id.
   * @param certSerialId - the new certificate id.
   * @return Signed Certificate.
   * @throws IOException - On Error
   * @throws CertificateException - on Error.
   */
  @SuppressWarnings("ParameterNumber")
  X509Certificate sign(
      SecurityConfig config,
      PrivateKey caPrivate,
      X509Certificate caCertificate,
      Date validFrom,
      Date validTill,
      PKCS10CertificationRequest certificationRequest,
      String scmId,
      String clusterId,
      String certSerialId)
      throws IOException, CertificateException;

  /**
   * Approval Types for a certificate request.
   */
  enum ApprovalType {
    KERBEROS_TRUSTED, /* The Request came from a DN using Kerberos Identity*/
    MANUAL, /* Wait for a Human being to inspect CSR of this certificate */
    TESTING_AUTOMATIC /* For testing purpose, Automatic Approval. */
  }

}
