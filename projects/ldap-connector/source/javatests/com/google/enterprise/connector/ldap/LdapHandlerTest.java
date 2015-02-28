// Copyright 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.ldap;

import com.google.common.base.Strings;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.enterprise.connector.instantiator.EncryptedPropertyPlaceholderConfigurer;
import com.google.enterprise.connector.ldap.LdapConstants.AuthType;
import com.google.enterprise.connector.ldap.LdapConstants.LdapConnectionError;
import com.google.enterprise.connector.ldap.LdapConstants.Method;
import com.google.enterprise.connector.ldap.LdapHandler.LdapConnectionSettings;
import com.google.enterprise.connector.ldap.LdapHandler.LdapRule;
import com.google.enterprise.connector.ldap.LdapHandler.LdapRule.Scope;

import junit.framework.TestCase;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.naming.ldap.LdapContext;

/**
 * Tests querying against LDAP.
 * Note: this test requires a live ldap connection (established through the
 * properties in LdapTesting.properties). Any test file that does not have this
 * comment at the top should run fine without a live ldap connection (with a
 * MockLdapHandler)
 */
public class LdapHandlerTest extends TestCase {

  private static final Properties TEST_PROPERTIES = System.getProperties();

  private static String getPassword() {
    // TODO(jlacey): Create a facade interface in CM util.testing package for
    // decrypting encrypted properties.
    // Set keystore path and decrypt properties.
    String cmKeyStore =
        TEST_PROPERTIES.getProperty("connector_manager.keystore");
    if (Strings.isNullOrEmpty(cmKeyStore)) {
      return TEST_PROPERTIES.getProperty("password");
    } else {
      EncryptedPropertyPlaceholderConfigurer.setKeyStorePath(cmKeyStore);
      return EncryptedPropertyPlaceholderConfigurer.decryptString(
          TEST_PROPERTIES.getProperty("password"));
    }
  }

  private static Set<String> getSchema() {
    String schemaString = TEST_PROPERTIES.getProperty("schema");
    return Sets.newHashSet(schemaString.split(","));
  }

  public void testConnectivity() {
    LdapHandler handler = new LdapHandler();
    handler.setLdapConnectionSettings(makeLdapConnectionSettings());
    LdapContext ldapContext = handler.getLdapContext();
    assertNotNull(ldapContext);
  }

  private static LdapConnectionSettings makeLdapConnectionSettings() {
    Method method = Method.STANDARD;
    String hostname = TEST_PROPERTIES.getProperty("hostname");
    int port = 389;
    String baseDN = TEST_PROPERTIES.getProperty("basedn");
    LdapConnectionSettings settings;
    String user = TEST_PROPERTIES.getProperty("user");
    String password = getPassword();
    if (Strings.isNullOrEmpty(user) || Strings.isNullOrEmpty(password)) {
      settings = new LdapConnectionSettings(method, hostname, port, baseDN);
    } else {
      settings = new LdapConnectionSettings(method, hostname, port, baseDN,
          AuthType.SIMPLE, user, password);
    }
    return settings;
  }

  public void testBadConnectivity() {
    LdapHandler handler = new LdapHandler();
    handler.setLdapConnectionSettings(makeInvalidLdapConnectionSettings());
    LdapContext ldapContext = handler.getLdapContext();
    assertNull(ldapContext);
    Map<LdapConnectionError, String> errors = handler.getErrors();
    for (LdapConnectionError e : errors.keySet()) {
      System.out.println("Error " + e + " message: " + errors.get(e));
    }
  }

  private static LdapConnectionSettings makeInvalidLdapConnectionSettings() {
    Method method = Method.STANDARD;
    String hostname = "not-ldap.xyzzy.foo";
    int port = 389;
    String baseDN = TEST_PROPERTIES.getProperty("basedn");
    LdapConnectionSettings settings =
        new LdapConnectionSettings(method, hostname, port, baseDN);
    return settings;
  }

  public void testBadPasswordConnectivity() {
    LdapHandler handler = new LdapHandler();
    handler.setLdapConnectionSettings(
        makeBadPwdLdapConnectionSettings());
    LdapContext ldapContext = handler.getLdapContext();
    assertNull(ldapContext);
    Map<LdapConnectionError, String> errors = handler.getErrors();
    assertTrue(errors.keySet().contains(
        LdapConnectionError.AuthenticationException));
  }

  private static LdapConnectionSettings makeBadPwdLdapConnectionSettings() {
    String hostname = TEST_PROPERTIES.getProperty("hostname.badpassword");
    int port = 389;
    String baseDN = TEST_PROPERTIES.getProperty("binding_dn.badpassword");
    String password = "wrongpassword";
    LdapConnectionSettings settings =
        new LdapConnectionSettings(Method.STANDARD, hostname, port, baseDN,
            AuthType.SIMPLE, baseDN, password);
    return settings;
  }

  public void testTimeoutConnectivity() {
    LdapHandler handler = new LdapHandler();
    long beforeTime = System.currentTimeMillis();
    handler.setConnectionTimeout("10000");
    handler.setLdapConnectionSettings(makeTimedoutLdapConnectionSettings());
    LdapContext ldapContext = handler.getLdapContext();
    long afterTime = System.currentTimeMillis();
    assertEquals((afterTime - beforeTime), 10000, 100);
    assertNull(ldapContext);
  }

  private static LdapConnectionSettings makeTimedoutLdapConnectionSettings() {
    Method method = Method.STANDARD;
    // hostname needs to be valid but unreachable and times out for this test
    String hostname = TEST_PROPERTIES.getProperty("hostname.timeout");
    int port = 389;
    String baseDN = TEST_PROPERTIES.getProperty("basedn");
    LdapConnectionSettings settings =
        new LdapConnectionSettings(method, hostname, port, baseDN);
    return settings;
  }  

  private static LdapHandler makeLdapHandlerForTesting(Set<String> schema, int maxResults) {
    // TODO: This is a hack to get the tests to run against a large
    // test LDAP server without throwing an OutOfMemoryError.
    if (maxResults == 0) {
      maxResults = 1000;
    }

    LdapRule ldapRule = makeSimpleLdapRule();
    LdapHandler ldapHandler = new LdapHandler();
    ldapHandler.setLdapConnectionSettings(makeLdapConnectionSettings());
    ldapHandler.setQueryParameters(ldapRule, schema,
        TEST_PROPERTIES.getProperty("schema_key"), maxResults);
    return ldapHandler;
  }

  private static LdapRule makeSimpleLdapRule() {
    String filter = TEST_PROPERTIES.getProperty("filter");
    Scope scope = Scope.SUBTREE;
    LdapRule ldapRule = new LdapRule(scope, filter);
    return ldapRule;
  }

  public void testSimpleQuery() {
    // makes sure we can instantiate and execute something
    LdapHandler ldapHandler = makeLdapHandlerForTesting(null, 0);
    Map<String, Multimap<String, String>> mapOfMultimaps = ldapHandler.get();
    System.out.println(mapOfMultimaps.size());
    dump(mapOfMultimaps);
  }

  public void testSpecifiedSchemaQuery() {
    // this time with a schema
    Set<String> schema = getSchema();
    dumpSchema(schema);
    LdapHandler ldapHandler = makeLdapHandlerForTesting(schema, 0);
    Map<String, Multimap<String, String>> mapOfMultimaps = ldapHandler.get();
    System.out.println(mapOfMultimaps.size());
    dump(mapOfMultimaps);
  }

  public void testQueryTwice() {
    Set<String> schema = getSchema();
    dumpSchema(schema);
    LdapHandler ldapHandler = makeLdapHandlerForTesting(schema, 0);
    Map<String, Multimap<String, String>> mapOfMultimaps = ldapHandler.get();
    System.out.println("first time size: " + mapOfMultimaps.size());

    mapOfMultimaps = ldapHandler.get();
    System.out.println("second time size: " + mapOfMultimaps.size());
  }

  public void testLimitedQuery() {
    LdapHandler ldapHandler = makeLdapHandlerForTesting(null, 1);
    Map<String, Multimap<String, String>> mapOfMultimaps = ldapHandler.get();
    assertEquals(1, mapOfMultimaps.size());
  }

  private void dumpSchema(Set<String> schema) {
    System.out.println("Schema:");
    for (String k: schema) {
      System.out.println(k);
    }
  }

  private void dump(Map<String, Multimap<String, String>> mapOfMultimaps) {
    for (Entry<String, Multimap<String, String>> entry : mapOfMultimaps.entrySet()) {
      String key = entry.getKey();
      Multimap<String, String> person = entry.getValue();
      System.out.println();
      for (String attrname : person.keySet()) {
        System.out.print(attrname);
        for (String value : person.get(attrname)) {
          System.out.print(" " + value);
        }
        System.out.println();
      }
    }
  }
}
