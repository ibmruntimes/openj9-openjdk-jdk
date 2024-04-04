/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

 import java.security.Security;
 import java.security.Provider;
 import java.util.List;
 import java.util.ArrayList;
 import java.util.Map;
 import java.util.HashMap;
 
 import java.io.FileInputStream;
 import java.io.FileOutputStream;
 import java.security.KeyStore;
 import java.security.Key;
 import java.security.cert.Certificate;
 import java.util.Enumeration;
 
 public class NetSslUtils {  
     public static final List<String> TLS_PROTOCOLS = new ArrayList<>();
     public static final Map<String, String> TLS_CIPHERSUITES = new HashMap<>();
  
     public static final String isFIPS = System.getProperty("semeru.fips");
     public static boolean isFIPS() {
         System.out.println("semeru.fips is: " + System.getProperty("semeru.fips"));
         return Boolean.parseBoolean(isFIPS);
     }
 
     public static final String FIPS_PROFILE = System.getProperty("semeru.customprofile");
     public static String getFipsProfile() {
         System.out.println("semeru.customprofile is: " + System.getProperty("semeru.customprofile"));
         return FIPS_PROFILE;
     }
 
     public static String revertJKSToPKCS12(String keyFilename, String passwd) {
         String p12keyFilename = keyFilename + ".p12";
         try {
             KeyStore jksKeystore = KeyStore.getInstance("JKS");
             try (FileInputStream fis = new FileInputStream(keyFilename)) {
                 jksKeystore.load(fis, passwd.toCharArray());
             }
 
             KeyStore pkcs12Keystore = KeyStore.getInstance("PKCS12");
             pkcs12Keystore.load(null, null);
 
             Enumeration<String> aliasesKey = jksKeystore.aliases();
             while (aliasesKey.hasMoreElements()) {
                 String alias = aliasesKey.nextElement();
                 if (jksKeystore.isKeyEntry(alias)) {
                     char[] keyPassword = passwd.toCharArray();
                     Key key = jksKeystore.getKey(alias, keyPassword);
                     Certificate[] chain = jksKeystore.getCertificateChain(alias);
                     pkcs12Keystore.setKeyEntry(alias, key, passwd.toCharArray(), chain);
                 } else if (jksKeystore.isCertificateEntry(alias)) {
                     Certificate cert = jksKeystore.getCertificate(alias);
                     pkcs12Keystore.setCertificateEntry(alias, cert);
                 }
             }
 
             try (FileOutputStream fos = new FileOutputStream(p12keyFilename)) {
                 pkcs12Keystore.store(fos, passwd.toCharArray());
             }
             System.out.println("JKS keystore converted to PKCS12 successfully.");
         } catch (Exception e) {
             e.printStackTrace();
         }
         return p12keyFilename;
     }
  
     static {
         TLS_PROTOCOLS.add("TLSv1.2");
         TLS_PROTOCOLS.add("TLSv1.3");
          
         TLS_CIPHERSUITES.put("TLS_AES_128_GCM_SHA256", "TLSv1.3");
         TLS_CIPHERSUITES.put("TLS_AES_256_GCM_SHA384", "TLSv1.3");
         TLS_CIPHERSUITES.put("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", "TLSv1.2");
         TLS_CIPHERSUITES.put("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "TLSv1.2");
         TLS_CIPHERSUITES.put("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", "TLSv1.2");
         TLS_CIPHERSUITES.put("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", "TLSv1.2");
         TLS_CIPHERSUITES.put("TLS_DHE_RSA_WITH_AES_256_GCM_SHA384", "TLSv1.2");
         TLS_CIPHERSUITES.put("TLS_DHE_RSA_WITH_AES_128_GCM_SHA256", "TLSv1.2");
         TLS_CIPHERSUITES.put("TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384", "TLSv1.2");
         TLS_CIPHERSUITES.put("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384", "TLSv1.2");
         TLS_CIPHERSUITES.put("TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", "TLSv1.2");
         TLS_CIPHERSUITES.put("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256", "TLSv1.2");
         TLS_CIPHERSUITES.put("TLS_DHE_RSA_WITH_AES_256_CBC_SHA256", "TLSv1.2");
         TLS_CIPHERSUITES.put("TLS_DHE_RSA_WITH_AES_128_CBC_SHA256", "TLSv1.2");
     }
 }