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

import com.google.enterprise.connector.spi.ConfigureResponse;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * @author sveldurthi@google.com (Srinivas Veldurthi)
 * 
 */
public class HtmlUnitTestPage {

  final private File tmpFile;
  final private WebClient webClient;
  final private HtmlPage htmlPage;
  final private HtmlForm htmlForm;
  
  public HtmlUnitTestPage(ConfigureResponse cr) 
      throws FileNotFoundException, IOException {

    String formSnippet = cr.getFormSnippet();
    final String FILE_NAME = "testFormSnippet";
    final String PRE_SNIPPET = "<html><body><form name=\"testForm\"><table>";
    final String POST_SNIPPET = "</table></form></body></html>";

    tmpFile = File.createTempFile(FILE_NAME, null);
    FileOutputStream fout = new FileOutputStream(tmpFile);
    new PrintStream(fout).println(PRE_SNIPPET + formSnippet + POST_SNIPPET);
    fout.close();
    tmpFile.deleteOnExit();

    webClient = new WebClient();
    htmlPage = webClient.getPage(tmpFile.toURI().toURL());
    htmlForm = htmlPage.getFormByName("testForm");

  }
  
  WebClient getWebClient() {
    return webClient;
  }

  HtmlPage getHtmlPage() {
    return htmlPage;
  }

  HtmlForm getHtmlForm() {
    return htmlForm;
  }

}
