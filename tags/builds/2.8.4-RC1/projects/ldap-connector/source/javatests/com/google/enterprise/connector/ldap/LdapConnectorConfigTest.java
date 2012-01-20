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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.enterprise.connector.ldap.LdapConstants.ConfigName;

import junit.framework.TestCase;

import org.json.JSONArray;

import java.util.Set;

public class LdapConnectorConfigTest extends TestCase {

  public void testSimple() {
    ImmutableMap<String, String> configMap =
        ImmutableMap.<String, String> builder().
        put(LdapConstants.ConfigName.AUTHTYPE.toString(), "ANONYMOUS").
        put(LdapConstants.ConfigName.HOSTNAME.toString(),
            "ldap.realistic-looking-domain.com").
        put(LdapConstants.ConfigName.METHOD.toString(), "STANDARD").
        put(LdapConstants.ConfigName.BASEDN.toString(),
            "ou=people,dc=example,dc=com").
        put(LdapConstants.ConfigName.FILTER.toString(), "ou=people").
        put(LdapConstants.ConfigName.SCHEMAVALUE.toString(),
            getSchemaValue("foo","bar","baz")).
        build();
    LdapConnectorConfig ldapConnectorConfig = new LdapConnectorConfig(configMap);
    Set<String> schema = ldapConnectorConfig.getSchema();
    assertTrue(schema.contains("foo"));
    assertTrue(schema.contains("bar"));
    assertTrue(schema.contains("baz"));
    assertEquals(LdapHandler.DN_ATTRIBUTE, ldapConnectorConfig.getSchemaKey());
    assertTrue(schema.contains(LdapHandler.DN_ATTRIBUTE));
  }

  /**
   * Generates a String representation of schema attributes as the UI would
   * generate when user clicks on one or more checkboxes to select schema 
   * attributes.
   * @return String of the regex [<delemiter-value>attribute1]*
   */
  private String getSchemaValue(String... attributes) {
    JSONArray arr = new JSONArray();
    for (String attribute : attributes) {
      arr.put(attribute);
    }
    return arr.toString();
  }

  public void testBigSchema() {
    Builder<String, String> builder = ImmutableMap.<String, String> builder();
    builder.
        put("authtype", "ANONYMOUS").
        put("hostname", "ldap.realistic-looking-domain.com").
        put("method", "STANDARD").
        put("basedn", "ou=people,dc=example,dc=com").
        put("filter", "ou=people");
    JSONArray arr = new JSONArray();
    for (int i = 0; i < 200; i++) {
      arr.put("attribute" + i);
    }
    builder.put(ConfigName.SCHEMAVALUE.toString(), arr.toString());
    ImmutableMap<String, String> configMap = builder.build();
    LdapConnectorConfig ldapConnectorConfig = new LdapConnectorConfig(configMap);
    Set<String> schema = ldapConnectorConfig.getSchema();
    assertEquals(LdapHandler.DN_ATTRIBUTE, ldapConnectorConfig.getSchemaKey());
    assertTrue(schema.contains(LdapHandler.DN_ATTRIBUTE));
    //200 from the for loop and 1 for the DN
    assertEquals(201, schema.size());
    for (int i = 0; i < 200; i++) {
      assertTrue(schema.contains("attribute" + i));
    }
  }
  
  public void testForOnlyOneSchemaAttribute() {
    Builder<String, String> builder = ImmutableMap.<String, String> builder();
    builder.
        put("authtype", "ANONYMOUS").
        put("hostname", "ldap.realistic-looking-domain.com").
        put("method", "STANDARD").
        put("basedn", "ou=people,dc=example,dc=com").
        put("filter", "ou=people");
    builder.put(ConfigName.SCHEMAVALUE.toString(), getSchemaValue("foo"));
    ImmutableMap<String, String> configMap = builder.build();
    LdapConnectorConfig ldapConnectorConfig = new LdapConnectorConfig(configMap);
    Set<String> schema = ldapConnectorConfig.getSchema();
    assertEquals(LdapHandler.DN_ATTRIBUTE, ldapConnectorConfig.getSchemaKey());
    assertTrue(schema.contains(LdapHandler.DN_ATTRIBUTE));
    assertEquals(2, schema.size());
    assertTrue(schema.contains("foo"));
  }
  
  public void testForZeroSchemaAttributes() {
    Builder<String, String> builder = ImmutableMap.<String, String> builder();
    builder.
        put("authtype", "ANONYMOUS").
        put("hostname", "ldap.realistic-looking-domain.com").
        put("method", "STANDARD").
        put("basedn", "ou=people,dc=example,dc=com").
        put("filter", "ou=people");
    builder.put(ConfigName.SCHEMAVALUE.toString(), "");
    ImmutableMap<String, String> configMap = builder.build();
    LdapConnectorConfig ldapConnectorConfig = new LdapConnectorConfig(configMap);
    Set<String> schema = ldapConnectorConfig.getSchema();
    assertEquals(LdapHandler.DN_ATTRIBUTE, ldapConnectorConfig.getSchemaKey());
    assertEquals(0,schema.size());
  }
  
  public void testForSchemaAttributeWithQuotesInThemToScareJson() {
    Builder<String, String> builder = ImmutableMap.<String, String> builder();
    builder.
        put("authtype", "ANONYMOUS").
        put("hostname", "ldap.realistic-looking-domain.com").
        put("method", "STANDARD").
        put("basedn", "ou=people,dc=example,dc=com").
        put("filter", "ou=people");
    builder.put(ConfigName.SCHEMAVALUE.toString(),
        getSchemaValue("foo","@tributeWith\"quotes\"in it"));
    ImmutableMap<String, String> configMap = builder.build();
    LdapConnectorConfig ldapConnectorConfig = new LdapConnectorConfig(configMap);
    Set<String> schema = ldapConnectorConfig.getSchema();
    assertEquals(LdapHandler.DN_ATTRIBUTE, ldapConnectorConfig.getSchemaKey());
    assertTrue(schema.contains(LdapHandler.DN_ATTRIBUTE));
    assertEquals(3, schema.size());
    assertTrue(schema.contains("foo"));
    assertTrue(schema.contains("@tributeWith\"quotes\"in it"));
    assertFalse(schema.contains("quotes"));
  }

  public void testSchemaForBackwordCompatibility() {
    Builder<String, String> builder = ImmutableMap.<String, String> builder();
    builder.
        put("authtype", "ANONYMOUS").
        put("hostname", "ldap.realistic-looking-domain.com").
        put("method", "STANDARD").
        put("basedn", "ou=people,dc=example,dc=com").
        put("filter", "ou=people");
    for (int i = 0; i < 100; i++) {
      builder.put("schema_" + i, "attribute" + i);
    }
    ImmutableMap<String, String> configMap = builder.build();
    LdapConnectorConfig ldapConnectorConfig = new LdapConnectorConfig(configMap);
    Set<String> schema = ldapConnectorConfig.getSchema();
    assertEquals(LdapHandler.DN_ATTRIBUTE, ldapConnectorConfig.getSchemaKey());
    assertTrue(schema.contains(LdapHandler.DN_ATTRIBUTE));
    //100 from the for loop and 1 for the DN
    assertEquals(101, schema.size());
    //Test all attributes are present.
    for (int i = 0; i < 100; i++) {
      assertTrue(schema.contains("attribute" + i));
    }

  }


}
