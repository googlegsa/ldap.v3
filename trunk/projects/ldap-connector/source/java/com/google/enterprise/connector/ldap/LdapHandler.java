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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.enterprise.connector.ldap.LdapConstants.AuthType;
import com.google.enterprise.connector.ldap.LdapConstants.ErrorMessages;
import com.google.enterprise.connector.ldap.LdapConstants.LdapConnectionError;
import com.google.enterprise.connector.ldap.LdapConstants.Method;
import com.google.enterprise.connector.ldap.LdapConstants.ServerType;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.AuthenticationException;
import javax.naming.AuthenticationNotSupportedException;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;
import javax.naming.ldap.Rdn;

/**
 * This class encapsulates all interaction with jdni (javax.naming). No other
 * ldap connector class should need to import anything from jdni. All
 * javax.naming
 * exceptions are wrapped in RuntimeException, so callers need to be careful to
 * catch RuntimeException.
 */
public class LdapHandler implements LdapHandlerI {
  /**
   * Every object in LDAP has a "distinguished name" (DN). jndi does not treat
   * DN as an attribute, but we do. DN will always be present and will always be
   * unique.
   */
  public static final String DN_ATTRIBUTE = "dn";

  private static Logger LOG = Logger.getLogger(LdapHandler.class.getName());

  private LdapConnectionSettings ldapConnectionSettings = null;
  private String schemaKey = null;
  private Set<String> schema = null;
  private LdapRule rule = null;
  private int maxResults = 0;
  private String ldapConnectionTimeout = "-1";

  private LdapConnection connection = null;

  private static Function<String, String> toLower = new Function<String, String>() {
    /* @Override */
    public String apply(String s) {
      return s.toLowerCase();
    }
  };

  public LdapHandler() {
  }
  
  public void setConnectionTimeout(String ldapConnectionTimeout) {
    this.ldapConnectionTimeout = ldapConnectionTimeout;
  }

  public String getConnectionTimeout() {
    return this.ldapConnectionTimeout;
  }

  public void setQueryParameters(LdapRule rule, Set<String> schema, String schemaKey, int maxResults) {
    this.rule = rule;
    this.schemaKey = schemaKey;
    if (schema == null) {
      this.schema = null;
    } else {
      this.schema = Sets.newHashSet(Collections2.transform(schema, toLower));
    }
    this.maxResults = maxResults;
  }

  /* @Override */
  public void setLdapConnectionSettings(LdapConnectionSettings ldapConnectionSettings) {
    this.ldapConnectionSettings = ldapConnectionSettings;
    LOG.fine("settings " + this.ldapConnectionSettings);
    connection = new LdapConnection(ldapConnectionSettings, getConnectionTimeout());
  }

  /* @Override */
  public Map<LdapConnectionError, String> getErrors() {
    if (connection != null) {
      return connection.getErrors();
    }
    throw new IllegalStateException("Must successfully set connection config before getting error state");
  }

  /**
   * Convenience routine for setting up an LdapHandler from an LdapConnectorConfig.
   * This is expected to be called by Spring, for a production instance.
   */
  public static LdapHandlerI makeLdapHandlerFromConfig(LdapConnectorConfig ldapConnectorConfig) {
    LOG.fine("ldapConnectorConfig: " + ldapConnectorConfig);
    LdapHandlerI ldapHandler = new LdapHandler();
    LOG.fine("ldapHandler: " + ldapHandler);
    LdapConnectionSettings settings = ldapConnectorConfig.getSettings();
    LOG.fine("settings: " + settings);
    ldapHandler.setLdapConnectionSettings(settings);
    LdapRule rule = ldapConnectorConfig.getRule();
    Set<String> schema = ldapConnectorConfig.getSchema();
    String schemaKey = ldapConnectorConfig.getSchemaKey();
    ldapHandler.setQueryParameters(rule, schema, schemaKey, 0);
    return ldapHandler;
  }

  @VisibleForTesting
  LdapContext getLdapContext() {
    return connection.getLdapContext();
  }

  /**
   * Executes a rule. Note: the implementation of this class is based on GADS.
   * Note: execute should only be called once. To execute again, create a new
   * LdapHandler and execute that.
   *
   * @return a Map of results. The map is sorted by the schemaKey
   *         specified in the constructor. Each result is a Multimap of Strings
   *         to Strings, keyed by attributes in the schema. Results are
   *         Multimaps because ldap can store multiple values with an attribute,
   *         although in practice this is rare (except for a few attributes,
   *         like email aliases).
   */
  public Map<String, Multimap<String, String>> get() {
    LOG.fine("entering get " + ldapConnectionSettings);

    if (ldapConnectionSettings == null) {
      throw new IllegalStateException("Must successfully set LdapConnectionSettings before get");
    }

    connection = new LdapConnection(ldapConnectionSettings, getConnectionTimeout());

    LOG.fine("connection:" + connection);

    SortedMap<String, Multimap<String, String>> result =
        new TreeMap<String, Multimap<String, String>>();

    LdapContext ctx = connection.getLdapContext();

    LOG.fine("ctx:" + ctx);

    if (ctx == null) {
      throw new IllegalStateException(ErrorMessages.UNKNOWN_CONNECTION_ERROR.toString());
    }

    NamingEnumeration<SearchResult> ldapResults = null;
    int resultCount = 0;
    byte[] cookie = null;
    try {      
      
      do {
        SearchControls controls = makeControls(rule, schema);

        LOG.info("Ldap search begin");
        ldapResults = ctx.search("", // Filter is always relative to our base dn
            rule.getFilter(), controls);

        // Process results.
        while (ldapResults.hasMore()) {
          resultCount++;

          Multimap<String, String> thisResult = ArrayListMultimap.create();

          SearchResult searchResult = ldapResults.next();
          Attributes attributes = searchResult.getAttributes();

          // We don't see our DN as a normal attribute, we have to ask for it
          // separately.
          String canonicalDn = canonicalDn(searchResult.getNameInNamespace());
          thisResult.put(DN_ATTRIBUTE, canonicalDn);

          if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("ldap search result " + resultCount + " dn " + canonicalDn);
          }

          // Add all our attributes to this result object
          handleAttrs(thisResult, searchResult, result, attributes);

          String keyValue = getFirst(schemaKey, thisResult);
          if (keyValue == null) {
            LOG.warning("Ldap result" + canonicalDn +
                " is missing schema key attribute " + schemaKey + ": skipping");
          } else {
            result.put(keyValue, thisResult);
          }
          if (maxResults > 0 && resultCount >= maxResults) {
            break;
          }
        }

        if (LOG.isLoggable(Level.INFO)) {
          LOG.info("ldap search intermediate result count " + resultCount);
        }

        // Examine the paged results control response
        // This may be null if the server does not support paged results
        Control[] pagedControls = ctx.getResponseControls();
        if (controls != null && pagedControls != null) {
          for (int i = 0; i < pagedControls.length; i++) {
            if (pagedControls[i] instanceof PagedResultsResponseControl) {
              PagedResultsResponseControl prrc =
                  (PagedResultsResponseControl) pagedControls[i];
              cookie = prrc.getCookie();
            } else {
              // Handle other response controls (if any)
            }
          }
        }
        // Re-activate paged results
        // Note: this code is from GADS
        // TODO: decide whether this is really needed for the ldap connector
        ctx.setRequestControls(new Control[] {new PagedResultsControl(LdapConnection.PAGESIZE,
            cookie, Control.NONCRITICAL)});
      } while (!shouldStop(cookie));

    } catch (CommunicationException e) {
      throw new LdapTransientException(e);
    } catch (NameNotFoundException e) {
      throw new IllegalStateException(e);
    } catch (NamingException e) {
      throw new IllegalStateException(e);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } finally {
      // Clean up everything.
      if (ldapResults != null) {
        try {
          ldapResults.close();
        } catch (Exception e) {
          LOG.log(Level.WARNING, "ldap_connection_cleanup_error_on_results", e);
        }
      }
      if (ctx != null) {
        try {
          ctx.close();
        } catch (Exception e) {
          LOG.log(Level.WARNING, "ldap_connection_cleanup_error_on_context", e);
        }
      }
      connection = null;
    }
    LOG.info("ldap search final result count " + resultCount);
    return result;
  }

  private static boolean shouldStop(byte[] cookie) {
    boolean shouldStop = (cookie == null);
    if (shouldStop) {
      LOG.fine("No more paged results - stopping.");
    } else {
      LOG.fine("Looking for another page");
    }
    return shouldStop;
  }

  private String getFirst(String key, Multimap<String, String> m) {
    for (String value : m.get(key)) {
      return value;
    }
    return null;
  }

  private void handleAttrs(Multimap<String, String> thisResult,
      SearchResult searchResult, SortedMap<String, Multimap<String, String>> result,
      Attributes attributes) throws NamingException {

    NamingEnumeration<? extends Attribute> allAttrs = attributes.getAll();

    while (allAttrs.hasMore()) {
      Attribute attr = allAttrs.next();
      String attrName = attr.getID().toLowerCase();
      // treat a null schema by returning all attributes
      // otherwise only return attributes in the schema
      if (schema == null || schema.contains(attrName)) {
        // Add each attribute value (most only have one)
        for (int i = 0; i < attr.size(); i++) {
          Object attributeValue = attr.get(i);
          if (attributeValue instanceof String) {
            String value = (String) attr.get(i);
            thisResult.put(attrName, value);
          } else if (attributeValue.getClass().isAssignableFrom(byte[].class)) {
            // skip this attribute - we only deal with Strings
            // This means we can't deal with encrypted strings (e.g. passwords)
            // or byte arrays (photos)
            // TODO: maybe report this?
          }
        }
      }
    }
  }

  private SearchControls makeControls(LdapRule rule, Set<String> allNotableAttributes) {
    SearchControls controls = new SearchControls();
    // Set scope as appropriate from the rule.
    if (rule.getScope() == LdapRule.Scope.SUBTREE) {
      controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    } else if (rule.getScope() == LdapRule.Scope.OBJECT) {
      controls.setSearchScope(SearchControls.OBJECT_SCOPE);
    } else if (rule.getScope() == LdapRule.Scope.ONELEVEL) {
      controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
    } else {
      throw new RuntimeException("ldap_search_invalid_rule_scope " + rule.getScope());
    }
    if (allNotableAttributes == null || allNotableAttributes.size() == 0) {
      // Return all attributes
      controls.setReturningAttributes(null);
    } else {
      // Only return our specified attributes.
      String[] returnAttrs = new String[0];
      returnAttrs = allNotableAttributes.toArray(returnAttrs);
      controls.setReturningAttributes(returnAttrs);
    }
    return controls;
  }

  /**
   * Return a canonical form for the DN: DN lower-cased.
   * spaces around commas deleted
   * any \{2 hexdigits} sequences replaced with that byte (other than slash)
   * Note: this code is from GADS
   * TODO: decide whether this is really needed for the ldap connector
   */
  public static String canonicalDn(String origDn) {
    if (origDn == null) {
      return null;
    }
    origDn = origDn.toLowerCase();
    try {
      return Rdn.unescapeValue(origDn).toString().replaceAll("/", "%2F");
    } catch (IllegalArgumentException e) {
      LOG.log(Level.INFO, "Potentially invalid LDAP DN found: " + origDn, e);
    }
    // we only do this if the Rdn parsing above threw an exception
    return origDn.replaceAll(" *, *", ",").replaceAll("/", "%2F");
  }

  /**
   * A connection to an Ldap Server
   */
  private static class LdapConnection {

    private static final String COM_SUN_JNDI_LDAP_LDAP_CTX_FACTORY =
        "com.sun.jndi.ldap.LdapCtxFactory";
    private static final String COM_SUN_JNDI_LDAP_CONNECT_TIMEOUT =
        "com.sun.jndi.ldap.connect.timeout";
    
    private final LdapConnectionSettings settings;
    private LdapContext ldapContext = null;
    private final Map<LdapConnectionError, String> errors;
    private String connectionTimeOut;

    public static final int PAGESIZE = 1000;
    
    public LdapConnection(LdapConnectionSettings ldapConnectionSettings, String connectionTimeOut) {
      LOG.fine("Configuring LdapConnection with settings: " + ldapConnectionSettings);
      this.settings = ldapConnectionSettings;
      this.errors = Maps.newHashMap();
      this.connectionTimeOut = connectionTimeOut;
      Hashtable<String, String> env = configureLdapEnvironment();
      ldapContext = makeContext(env, PAGESIZE);
    }

    public LdapContext getLdapContext() {
      return ldapContext;
    }

    public Map<LdapConnectionError, String> getErrors() {
      return errors;
    }

    private LdapContext makeContext(Hashtable<String, String> env, int pageSize) {
      LdapContext ctx = null;
      try {
        ctx = new InitialLdapContext(env, null);
      } catch (CommunicationException e) {
        LOG.info("Communication error : " + e.toString());

        if (e.getCause() instanceof SocketTimeoutException) {
          errors.put(LdapConnectionError.CommunicationExceptionTimeout, e.getCause().getMessage());
        } else if (e.getCause() instanceof UnknownHostException) {
          errors.put(LdapConnectionError.CommunicationExceptionUnknownhost, e.getCause()
              .getMessage());
        } else {
          errors.put(LdapConnectionError.CommunicationException, e.getMessage());
        }
      } catch (AuthenticationNotSupportedException e) {
        errors.put(LdapConnectionError.AuthenticationNotSupported, e.getMessage());
      } catch (AuthenticationException e) {
        errors.put(LdapConnectionError.AuthenticationException, e.getMessage());
      } catch (NamingException e) {
        errors.put(LdapConnectionError.NamingException, e.getMessage());
      }
      if (ctx == null) {
        return null;
      }
      try {
        ctx.setRequestControls(new Control[] {new PagedResultsControl(pageSize, 
            Control.NONCRITICAL)});
      } catch (NamingException e) {
        errors.put(LdapConnectionError.NamingException, e.getMessage());
      } catch (IOException e) {
        errors.put(LdapConnectionError.IOException, e.getMessage());
      }
      return ctx;
    }

    private String makeLdapUrl() {
      String url;
      Method connectMethod =
          settings.getConnectMethod();
      if (connectMethod == Method.SSL) {
        url = "ldaps://"; //$NON-NLS-1$
      } else {
        url = "ldap://"; //$NON-NLS-1$
      }

      // Construct the full URL
      url = url + settings.getHostname();
      if (settings.getPort() > 0) {
        url = url + ":" + settings.getPort();
      }
      url = url + "/";

      if (settings.getBaseDN() != null) {
        url = url + encodeBaseDN(settings.getBaseDN());
      }
      return url;
    }

    /**
     * Initialize the Hashtable used to create an initial LDAP Context. Note
     * that we specifically require a Hashtable rather than a HashMap as the
     * parameter type in the InitialLDAPContext constructor
     *
     * @return initialized Hashtable suitable for constructing an
     *         InitiaLdaplContext
     */
    private Hashtable<String, String> configureLdapEnvironment() {
      Hashtable<String, String> env = new Hashtable<String, String>();

      // Use the built-in LDAP support.
      env.put(Context.INITIAL_CONTEXT_FACTORY, COM_SUN_JNDI_LDAP_LDAP_CTX_FACTORY);

      // property to indicate to the server how to handle referrals
      env.put(Context.REFERRAL, "follow");

      // force the following attributes to be returned as binary data
      env.put("java.naming.ldap.attributes.binary", "objectGUID objectSid");
      
      // Specify connection timeout, value of zero or less means use networks timeout value
      env.put(COM_SUN_JNDI_LDAP_CONNECT_TIMEOUT, connectionTimeOut);    

      // Set our authentication settings.
      AuthType authType = settings.getAuthType();
      if (authType == AuthType.SIMPLE) {
        env.put(Context.SECURITY_AUTHENTICATION, authType.toString()
            .toLowerCase());
        env.put(Context.SECURITY_PRINCIPAL, settings.getUsername());
        env.put(Context.SECURITY_CREDENTIALS, settings.getPassword());
        LOG.info("Using simple authentication.");
      } else {
        if (authType != AuthType.ANONYMOUS) {
          LOG.warning("Unknown authType - falling back to anonymous.");
        } else {
          LOG.info("Using anonymous authentication.");
        }
        env.put(Context.SECURITY_AUTHENTICATION, "none"); //$NON-NLS-1$
      }
      env.put(Context.PROVIDER_URL, makeLdapUrl());
      return env;
    }

    /**
     * We have to do some simple, naive escaping of the base DN. We CANNOT use
     * normal URL escaping, as '+' is not handled properly by the JNDI backend.
     */
    private String encodeBaseDN(String origValue) {
      origValue = origValue.replace(" ", "%20");
      return origValue;
    }

  }

  /**
   * Configuration for an ldap connection. Immutable, static data class.
   */
  public static class LdapConnectionSettings {

    private final String hostname;
    private final int port;
    private final AuthType authType;
    private final String username;
    private final String password;
    private final Method connectMethod;
    private final String baseDN;
    private final ServerType serverType;

    public LdapConnectionSettings(Method connectMethod, String hostname,
        int port, String baseDN, AuthType authType, String username, String password) {
      this.authType = authType;
      this.baseDN = baseDN;
      this.connectMethod = connectMethod;
      this.hostname = hostname;
      this.password = password;
      this.port = port;
      this.serverType = ServerType.GENERIC;
      this.username = username;
    }

    public LdapConnectionSettings(Method connectMethod, String hostname,
        int port, String baseDN) {
      this.authType = AuthType.ANONYMOUS;
      this.baseDN = baseDN;
      this.connectMethod = connectMethod;
      this.hostname = hostname;
      this.password = null;
      this.port = port;
      this.serverType = ServerType.GENERIC;
      this.username = null;
    }

    @Override
    public String toString() {
      String displayPassword;
      if (password == null) {
        displayPassword = "null";
      } else if (password.length() < 1) {
        displayPassword = "<empty>";
      } else {
        displayPassword = "####";
      }
      return "LdapConnectionSettings [authType=" + authType + ", baseDN=" + baseDN
          + ", connectMethod=" + connectMethod + ", hostname=" + hostname + ", password="
          + displayPassword + ", port=" + port + ", serverType=" + serverType + ", username=" + username
          + "]";
    }

    public AuthType getAuthType() {
      return authType;
    }

    public String getBaseDN() {
      return baseDN;
    }

    public Method getConnectMethod() {
      return connectMethod;
    }

    public String getHostname() {
      return hostname;
    }

    public String getPassword() {
      return password;
    }

    public int getPort() {
      return port;
    }

    public ServerType getServerType() {
      return serverType;
    }

    public String getUsername() {
      return username;
    }
  }

  /**
   * Configuration for an ldap rule (query). Immutable, static data class.
   */
  public static class LdapRule {
    public enum Scope {
      SUBTREE, ONELEVEL, OBJECT
    }

    private final Scope scope;
    private final String filter;

    public LdapRule(Scope scope, String filter) {
      this.scope = scope;
      this.filter = filter;
    }

    public Scope getScope() {
      return scope;
    }

    public String getFilter() {
      return filter;
    }
  }
}
