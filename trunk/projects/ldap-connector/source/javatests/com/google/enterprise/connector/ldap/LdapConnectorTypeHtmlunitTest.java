// Copyright 2011 Google Inc. All Rights Reserved.
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
import com.google.enterprise.connector.ldap.MockLdapHandlers.SimpleMockLdapHandler;
import com.google.enterprise.connector.spi.ConfigureResponse;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlHiddenInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import junit.framework.TestCase;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * @author sveldurthi@google.com (Srinivas Veldurthi)
 * 
 */
public class LdapConnectorTypeHtmlunitTest extends TestCase {

  public void testLdapConnectorHtmlPageDeleteAttribute() throws FailingHttpStatusCodeException,
      MalformedURLException, IOException {

    SimpleMockLdapHandler basicMock = MockLdapHandlers.getBasicMock();
    LdapConnectorType lct = new LdapConnectorType(basicMock);
    ResourceBundle b = lct.getResourceBundle(Locale.US);

    ImmutableMap<String, String> Config =
        ImmutableMap.<String, String>builder().put("googlePropertiesVersion", "3")
            .put("authtype", "ANONYMOUS").put("hostname", "ldap.realistic-looking-domain.com")
            .put("googleConnectorName", "x").put("password", "test").put("username", "admin")
            .put("schema_9", "employeestatus").put("schema_8", "employeenumber")
            .put("method", "STANDARD").put("basedn", "ou=people,dc=example,dc=com")
            .put("filter", "ou=people").build();

    ConfigureResponse cr = lct.getPopulatedConfigForm(Config, Locale.US);

    Map<String, String> configData = cr.getConfigData();
    assertTrue(configData == null || configData.isEmpty());

    String message = cr.getMessage();
    assertTrue(message == null || message.length() < 1);

    HtmlUnitTestPage htmlUnitPage = new HtmlUnitTestPage(cr);

    assertTrue(htmlUnitPage.getWebClient().isJavaScriptEnabled());

    HtmlHiddenInput hiddenSchemaValueField = null;
    hiddenSchemaValueField = htmlUnitPage.getHtmlForm().getInputByName("schemavalue");
    assertEquals("[\"employeestatus\",\"employeenumber\"]",
        hiddenSchemaValueField.getValueAttribute());

    // de-select employeestatus checkbox and verify results
    HtmlCheckBoxInput chkFieldBefore = htmlUnitPage.getHtmlForm().getInputByValue("employeestatus");
    assertTrue(chkFieldBefore.isChecked());
    HtmlPage page2 = chkFieldBefore.click();
    HtmlCheckBoxInput chkFieldAfter = htmlUnitPage.getHtmlForm().getInputByValue("employeestatus");
    hiddenSchemaValueField = htmlUnitPage.getHtmlForm().getInputByName("schemavalue");
    assertFalse(chkFieldAfter.isChecked());
    assertEquals("[\"employeenumber\"]", hiddenSchemaValueField.getValueAttribute());

    // de-select employeenumber checkbox and verify results
    chkFieldBefore = htmlUnitPage.getHtmlForm().getInputByValue("employeenumber");
    assertTrue(chkFieldBefore.isChecked());
    HtmlPage page3 = chkFieldBefore.click();
    chkFieldAfter = htmlUnitPage.getHtmlForm().getInputByValue("employeenumber");
    hiddenSchemaValueField = htmlUnitPage.getHtmlForm().getInputByName("schemavalue");
    assertFalse(chkFieldAfter.isChecked());
    assertEquals("[]", hiddenSchemaValueField.getValueAttribute());

    htmlUnitPage.getWebClient().closeAllWindows();
  }
}
