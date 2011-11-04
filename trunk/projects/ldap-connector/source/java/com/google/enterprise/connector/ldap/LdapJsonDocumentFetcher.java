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

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Supplier;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Multimap;
import com.google.enterprise.connector.spi.SpiConstants;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Uses an an LdapHandler to implement JsonDocumentFetcher.
 */
public class LdapJsonDocumentFetcher implements JsonDocumentFetcher {

  private static final Logger LOG = Logger.getLogger(LdapJsonDocumentFetcher.class.getName());
  
  // It's okay for multiple connector instances to use this same counter,
  // it only affects the duration of wait
  private static volatile int waitcounter = 0; 
  
  private final Supplier<Map<String, Multimap<String, String>>> mapOfMultimapsSupplier;


  /**
   * Creates a JsonDocument fetcher from something that provides a sorted map of
   * Multimaps
   *
   * @param mapOfMultimapsSupplier An object that can supply a sorted Map of
   *        Multimaps. This supplier must permit multiple calls to get() on the
   *        same instance. Calls may return different results. The map must be
   *        in natural key order, that is, the order of the entrySet() must be
   *        ascending by key. The values (the individual multimaps) represent a
   *        document as a bag of (metadata-name,value) pairs.
   */
  public LdapJsonDocumentFetcher(
      Supplier<Map<String, Multimap<String, String>>> mapOfMultimapsSupplier) {
    this.mapOfMultimapsSupplier = mapOfMultimapsSupplier;
  }

  private static Function<Entry<String, Multimap<String, String>>, Multimap<String, String>> addDocid =
      new Function<Entry<String, Multimap<String, String>>, Multimap<String, String>>() {
    /* @Override */
    public Multimap<String, String> apply(Entry<String, Multimap<String, String>> e) {
      Multimap<String, String> person = ArrayListMultimap.create(e.getValue());
      String key = e.getKey();
      key = cleanLdapKey(key);
      person.put(SpiConstants.PROPNAME_DOCID, key);
      person.put(SpiConstants.PROPNAME_LOCK, "true");
      return person;
    }
  };

  /* @Override */
  public Iterator<JsonDocument> iterator() {

    Map<String, Multimap<String, String>> results;

    try {
      results = mapOfMultimapsSupplier.get();
      // reset wait counter
      waitcounter = 0;
    } catch (LdapTransientException e) {
      LOG.log(Level.SEVERE, "Encountered IllegalStateException, will wait and continue.", e);
      results =  Collections.emptyMap();
      try {
        // wait for some time to check if the unavailable ldap source would be
        // back
        long sleepTime;
        if (waitcounter < 4) {
          sleepTime = (long) (60 * 1000 * java.lang.Math.pow(2, waitcounter));
        } else {
          sleepTime = (15 * 60 * 1000); // wait for 15 minutes max
        }
        LOG.info("Waiting for " + sleepTime + " milliseconds.");
        Thread.sleep(sleepTime);
        waitcounter++;
      } catch (InterruptedException e1) {
        LOG.info("InterruptedException at CommunicationException catch..");
      }
    }

    return Iterators.transform(results.entrySet().iterator(),
        Functions.compose(JsonDocument.buildFromMultimap, addDocid));
  }

  /**
   * Cleans the key before using it as the docid (which becomes part of the
   * url), while preserving collation sequence.
   *
   * Due to some inconsistency of responsibility in url-encoding the key value,
   * the diffing infrastructure is current encoding url-unsafe keys for delete
   * in a different way from how it does it when they're new. TODO(Max): chase
   * this phenomenon down and regularize it - a portion of the problem is in the
   * CM and there are possibly some upgrade issues. In the meantime, since this
   * connector is new, it's safest to handle it locally. This routine creates a
   * url-safe encoding of the key that preserves collation sequence. Since the
   * keys are already in sorted order, we need to encode them in a way that
   * preserves order.
   *
   * We do this by encoding to UTF-8, then as hex of the byte sequence. The
   * reasons why this preserves order are complicated. See
   * http://unicode.org/reports/tr26/ for more, and note that this is tested by
   * I18NLdapJsonDocumentFetcherTest and other tests that extend
   * JsonDocumentFetcherTestCase.
   **/
  public static String cleanLdapKey(String key) {
    StringBuffer sb = new StringBuffer();
    byte[] charArray;
    try {
      charArray = key.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      LOG.log(Level.SEVERE, "Impossible: UTF-8 not supported", e);
      return key;
    }
    for (byte b : charArray) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
