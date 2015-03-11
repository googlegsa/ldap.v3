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

import com.google.common.base.Supplier;
import com.google.common.collect.Multimap;
import com.google.enterprise.connector.ldap.LdapConstants.LdapConnectionError;
import com.google.enterprise.connector.ldap.LdapHandler.LdapConnectionSettings;
import com.google.enterprise.connector.ldap.LdapHandler.LdapRule;

import java.util.Map;
import java.util.Set;

public interface LdapHandlerI extends Supplier<Map<String, Multimap<String, String>>> {

  public void setLdapConnectionSettings(LdapConnectionSettings ldapConnectionSettings);

  public void setQueryParameters(LdapRule rule, Set<String> schema, String schemaKey, int maxResults);

  public Map<LdapConnectionError, Throwable> getErrors();

}
