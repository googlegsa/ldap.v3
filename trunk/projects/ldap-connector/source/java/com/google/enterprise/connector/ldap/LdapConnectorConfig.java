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

import com.google.common.collect.ImmutableSet;
import com.google.enterprise.connector.ldap.LdapConstants.AuthType;
import com.google.enterprise.connector.ldap.LdapConstants.ConfigName;
import com.google.enterprise.connector.ldap.LdapConstants.Method;
import com.google.enterprise.connector.ldap.LdapHandler.LdapConnectionSettings;
import com.google.enterprise.connector.ldap.LdapHandler.LdapRule;
import com.google.enterprise.connector.ldap.LdapHandler.LdapRule.Scope;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An encapsulation of all the config needed for a working Ldap Connector
 * instance.
 * This class is the bridge between {@link LdapConnectorType} and
 * {@link LdapDocumentSnapshotRepositoryList}, and thus between the
 * connectorInstance.xml and the connectorType.xml.
 * This is a simple, immutable value class.
 */
public class LdapConnectorConfig {

  public static final Logger LOG = Logger.getLogger(LdapConnectorConfig.class.getName());

  private final String hostname;
  private final int port;
  private final AuthType authtype;
  private final String username;
  private final String password;
  private final Method method;
  private final String basedn;
  private final String filter;
  private final String schemaKey;

  private final Set<String> schema;
  private final LdapRule rule;

  private final LdapConnectionSettings settings;

  /**
   * Sole constructor. This is the injection point for stored config, via the
   * connectorInstance.xml.
   *
   * @param config A Map<String, String> The config keys for this map come from
   *        the {@link ConfigName} enumeration, plus the pseudo-keys for schema.
   *        The values are interpreted depending on the types of the
   *        corresponding
   *        configuration points, which are accessed through public getters.
   *        There
   *        can be at most {@code MAX_SCHEMA_ELEMENTS} pseudo-keys to define the
   *        schema: these are of the form {@code ConfigName.SCHEMA.toString() +
   *        "_" + i}, that is, {@code SCHEMA_0, SCHEMA_1,} etc.
   */
  public LdapConnectorConfig(Map<String, String> config) {

    String hostname = getTrimmedValueFromConfig(config, ConfigName.HOSTNAME);
    String portString = getTrimmedValueFromConfig(config, ConfigName.PORT);
    String authtypeString = getTrimmedValueFromConfig(config, ConfigName.AUTHTYPE);
    String username = getTrimmedValueFromConfig(config, ConfigName.USERNAME);
    if(username == null) {
      username = "";
    }
    String password = getTrimmedValueFromConfig(config, ConfigName.PASSWORD);
    if(password == null) {
      password = "";
    }    
    String methodString = getTrimmedValueFromConfig(config, ConfigName.METHOD);
    String basedn = getTrimmedValueFromConfig(config, ConfigName.BASEDN);
    String filter = getTrimmedValueFromConfig(config, ConfigName.FILTER);
    String schemaKey = getTrimmedValueFromConfig(config, ConfigName.SCHEMA_KEY);
    //Since we removed this attribute from UI in 2.6.4 we need to add 
    //a default value here.
    if (schemaKey == null) {
      schemaKey = LdapHandler.DN_ATTRIBUTE;
    }
    Set<String> tempSchema = new TreeSet<String>();
    addSchemaFromConfig(config, tempSchema);
    
    /**
     * Note: if the schema is not empty (at least one schema_xx keys was
     * specified in the config)
     * then the schemaKey will be added to the schema. Otherwise the schema will
     * remain empty, to signify that this config is not complete.
     */
    if (tempSchema.size() > 0 && !tempSchema.contains(schemaKey)) {
      tempSchema.add(schemaKey);
    }

    ImmutableSet<String> schema = ImmutableSet.copyOf(tempSchema);

    this.hostname = hostname;

    Integer p;
    try {
      p = Integer.valueOf(portString);
    } catch (NumberFormatException e) {
      LOG.warning("Found illegal port value: " + portString + " defaulting to 389");
      p = 389;
    }
    this.port = p;

    AuthType authtype = AuthType.ANONYMOUS;
    if (authtypeString != null) {
      try {
        authtype = Enum.valueOf(AuthType.class, authtypeString);
      } catch (IllegalArgumentException e) {
      LOG.warning("Found illegal authtype value: " + authtypeString + " defaulting to "
          + AuthType.ANONYMOUS.toString());
      }
    }

    this.authtype = authtype;
    this.username = username;
    this.password = password;

    Method method = Method.STANDARD;
    if (methodString != null) {
      try {
        method = Enum.valueOf(Method.class, methodString);
      } catch (IllegalArgumentException e) {
        LOG.warning("Found illegal method value: " + methodString + " defaulting to "
            + Method.STANDARD.toString());
      }
    }

    this.method = method;
    this.basedn = basedn;
    this.schemaKey = schemaKey;
    this.filter = filter;
    this.schema = schema;

    this.settings =
        new LdapConnectionSettings(this.method, this.hostname, this.port, this.basedn,
        this.authtype, this.username, this.password);
    LOG.fine("this.settings: " + this.settings);

    // only create an LdapRule if one was supplied
    this.rule = (this.filter == null) ? null : new LdapRule(Scope.SUBTREE, this.filter);
  }

  /**
   * Gets the attribute which has the appended schema attributes and splits them
   * to add individual items in the provided set.
   * @param config Config of user entered entries
   * @param tempSchema Set which holds the schema attributes.
   */
  private void addSchemaFromConfig(Map<String, String> config,
      Set<String> tempSchema) {
   
    String schemaValues = getJsonStringForSelectedAttributes(config);
    if (schemaValues != null && schemaValues.trim().length() != 0) {
      JSONArray arr;
      try {
        arr = new JSONArray(schemaValues);
        for (int i = 0; i < arr.length(); i++) {
          tempSchema.add(arr.getString(i));
        }
      } catch (JSONException e) {
        LOG.warning("Did not get any selected attributes...");
      }
    }
    LOG.fine("Selected attributes: " + tempSchema);
  }

  /**
   * Assigns value to schemavalue from config form. Adds DN_ATTRIBUTE if not
   * present already.
   * 
   * @param config Config values entered on the form
   * @return SchemaValue in as String
   */
  static String getSchemaValueFromConfig(Map<String, String> config) {

    String configSchemaValue = null;
    LOG.fine("Original SchemaValue - " + config.get(ConfigName.SCHEMAVALUE.toString()));

    Set<String> schemaValue = new TreeSet<String>();
    LOG.fine("Trying to recover attributes from schema checkboxes");
    StringBuffer schemaKey = new StringBuffer();
    schemaKey.append(ConfigName.SCHEMA.toString()).append("_").append("\\d");
    Pattern keyPattern = Pattern.compile(schemaKey.toString());
    Set<String> configKeySet = config.keySet();

    for (String configKey : configKeySet) {
      Matcher matcher = keyPattern.matcher(configKey);
      if (matcher.find()) {
        String schemaAttribute = config.get(configKey);
        if (schemaAttribute != null && schemaAttribute.trim().length() != 0) {
          schemaValue.add(config.get(configKey));
        }
      }
    }

    // always add dn attribute to schema. 'dn' checkbox is hidden so can't be 
    // read and added to schemaValue in the code above.
    schemaValue.add(LdapHandler.DN_ATTRIBUTE);

    try {
      configSchemaValue = (new JSONArray(schemaValue.toString())).toString();
      LOG.fine("From config SchemaValue - " + configSchemaValue);

    } catch (JSONException e) {
      LOG.log(Level.WARNING, "JSONException trace:", e);
    }

    return configSchemaValue;
  }

  private String getTrimmedValueFromConfig(Map<String, String> config, ConfigName name) {
    String value = getTrimmedValue(config.get(name.toString()));
    return value;
  }

  private static String getTrimmedValue(String value) {
    if (value == null || value.length() < 1) {
      return null;
    }
    String trimmedValue = value.trim();
    if (trimmedValue.length() < 1) {
      return null;
    }
    return trimmedValue;
  }

  public String getHostname() {
    return hostname;
  }

  public int getPort() {
    return port;
  }

  public AuthType getAuthtype() {
    return authtype;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public Method getMethod() {
    return method;
  }

  public String getBasedn() {
    return basedn;
  }

  public String getFilter() {
    return filter;
  }

  public Set<String> getSchema() {
    return schema;
  }

  public LdapConnectionSettings getSettings() {
    return settings;
  }

  public LdapRule getRule() {
    return rule;
  }

  public String getSchemaKey() {
    return schemaKey;
  }

  /**
   * Returns the string that represents the selected attributes in a json
   * understandable way. 
   * First tries to search if the json string is passed by the config. If it is
   * not found then we try to create the json string from original LDAP
   * configuration format.  The original format is individual keys for 
   * each value.
   * @return String json parsable string representation of the selected 
   *         attributes 
   */
  static String getJsonStringForSelectedAttributes(
      Map <String, String> config) {
    String schemaValue = config.get(ConfigName.SCHEMAVALUE.toString());
    if (schemaValue == null || schemaValue.equals("[]")) {
      LOG.info("Trying to recover attributes from individual checkboxes");
      StringBuffer schemaKey = new StringBuffer();
      schemaKey.append(ConfigName.SCHEMA.toString()).append("_").append("\\d");
      Pattern keyPattern = Pattern.compile(schemaKey.toString());
      Set<String> configKeySet = config.keySet();
      JSONArray arr = new JSONArray();
      for (String configKey: configKeySet) {
        Matcher matcher = keyPattern.matcher(configKey);
        if (matcher.find()) {
          String schemaAttribute = config.get(configKey);
          if (schemaAttribute != null && schemaAttribute.trim().length() != 0) {
            arr.put(config.get(configKey));
          }
        }
      }
      if (arr.length() != 0) {
        schemaValue = arr.toString();
      } else {
        schemaValue = "";
      }
    }
    LOG.info("The appended string for selected attributes is: " + schemaValue);
    return schemaValue;
  }

}
