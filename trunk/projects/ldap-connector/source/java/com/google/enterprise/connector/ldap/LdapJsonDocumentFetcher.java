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
import com.google.common.base.Functions;
import com.google.common.base.Supplier;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Multimap;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.util.Base16;
import com.google.enterprise.connector.util.diffing.SnapshotRepositoryRuntimeException;

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

  private volatile int waitcounter = 0;

  /** default wait times are 1, 2, 4, 8 and 15 minutes, assigned in milliseconds.
   */
  private final int[] waitTimes; 

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
    this.waitTimes = new int[] { 1 * 60 * 1000, 2 * 60 * 1000, 4 * 60 * 1000, 
    		  8 * 60 * 1000, 15 * 60 * 1000 }; 
  }

  public LdapJsonDocumentFetcher(
          Supplier<Map<String, Multimap<String, String>>> mapOfMultimapsSupplier, 
          int [] waitTimes) {
        this.mapOfMultimapsSupplier = mapOfMultimapsSupplier;
        this.waitTimes = waitTimes;
  }

  private static Function<Entry<String, Multimap<String, String>>, Multimap<String, String>> addDocid =
      new Function<Entry<String, Multimap<String, String>>, Multimap<String, String>>() {
    @Override
    public Multimap<String, String> apply(Entry<String, Multimap<String, String>> e) {
      Multimap<String, String> person = ArrayListMultimap.create(e.getValue());
      String key = e.getKey();
      key = cleanLdapKey(key);
      person.put(SpiConstants.PROPNAME_DOCID, key);
      person.put(SpiConstants.PROPNAME_LOCK, "true");
      return person;
    }
  };

  @Override
  public Iterator<JsonDocument> iterator() {
    Map<String, Multimap<String, String>> results;

    try {
      results = mapOfMultimapsSupplier.get();
      // reset wait counter
      waitcounter = 0;
    } catch (IllegalStateException e) {
      LOG.log(Level.SEVERE, "Encountered IllegalStateException, will wait and continue.", e);
      try {
        // wait for some time to check if the unavailable ldap source would be
        // back
        long sleepTime;
        if (waitcounter < waitTimes.length) {
          sleepTime = waitTimes[waitcounter];
        } else {
          sleepTime = waitTimes[waitTimes.length - 1];
        }
        LOG.info("Waiting for " + sleepTime + " milliseconds.");
        Thread.sleep(sleepTime);
        waitcounter++;
      } catch (InterruptedException e1) {
        LOG.fine("Interrupted while waiting after IllegalStateException.");
      }
      // The full stack trace was logged above, before the wait period,
      // so pass a null cause rather than e.
      throw new SnapshotRepositoryRuntimeException(e.getMessage(), null);
    }

    return Iterators.transform(results.entrySet().iterator(),
        Functions.compose(JsonDocument.buildFromMultimap, addDocid));
  }

  /**
   * Creates a URL-safe encoding of the key that mostly preserves
   * binary order.
   * We do this by encoding to UTF-8, then hex encoding the byte sequence.
   * This method does not preserve the sort order of the
   * input strings in the presence of Unicode supplementary
   * characters.
   **/
  /*
   * TODO(jlacey): The original assumption here (that Java's UTF-8 is
   * really CESU-8) was wrong. This code only preserves order if the
   * original order was compatible with Unicode code points, or no
   * supplementary characters are used (which is the case in
   * I18NLdapJsonDocumentFetcherTest and other tests that extend
   * JsonDocumentFetcherTestCase).
   */
  @VisibleForTesting
  static String cleanLdapKey(String key) {
    return Base16.lowerCase().encode(key);
  }
}
