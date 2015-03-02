// Copyright 2011 Google Inc.
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

import com.google.enterprise.connector.ldap.MockLdapHandlers.ExceptionMockLdapHandler;

import junit.framework.TestCase;

/**
 * Tests for implementations of LdapTransientException handling in JsonDocumentFetcher iterator.
 */
public class JsonDocumentFetcherIteratorTest extends TestCase {

 /** wait timer in milliseconds. */
  private int[] waitTimer = new int[] { 500, 1000 };

  public JsonDocumentFetcher getJsonDocumentFetcher() {
    ExceptionMockLdapHandler excepMock = MockLdapHandlers.getExceptionMock();
    return new LdapJsonDocumentFetcher(excepMock, waitTimer);
  }

  public void testIterator() {

    final int MAXITERATIONS = 2;
    JsonDocumentFetcher jdf = getJsonDocumentFetcher();
    assertNotNull(jdf);

    for (int i = 0; i < waitTimer.length; i++) {
      long beforeTime = System.currentTimeMillis();
      jdf.iterator();
      long afterTime = System.currentTimeMillis();
      assertEquals((afterTime - beforeTime), waitTimer[i], 100);
    }

    // run again just to verify that it only waits for 1000 milliseconds ( the last 
    // wait time in array ).
    for (int i = 0; i < MAXITERATIONS; i++) {
      long beforeTime = System.currentTimeMillis();
      jdf.iterator();
      long afterTime = System.currentTimeMillis();
      assertEquals((afterTime - beforeTime), waitTimer[waitTimer.length - 1], 100);
    }

  }
}
