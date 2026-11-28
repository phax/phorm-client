/*
 * Copyright (C) 2026 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phorm.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.diver.api.coord.DVRCoordinate;

/**
 * Test class for class {@link PhormRepositoryStatus}. It works on a realistic
 * <code>/api/admin/repositories</code> response.
 *
 * @author Philip Helger
 */
public final class PhormRepositoryStatusTest
{
  private static final String RESPONSE = """
      {
        "configFile": "/opt/phorm/repositories.xml",
        "loadDateTime": "2026-11-27T10:15:30.123+01:00",
        "repositories": [
          { "id": "example-repo", "vesCount": 2 },
          { "id": "empty-repo", "vesCount": 0 }
        ],
        "vesids": [
          { "vesid": "com.example:invoice:2026.11", "repository": "example-repo" },
          { "vesid": "com.example:creditnote:2026.11", "repository": "example-repo" }
        ],
        "invocationDateTime": "2026-11-27T09:20:00.000Z",
        "invocationDurationMillis": 1
      }
      """;

  @NonNull
  private static PhormRawResponse _createResponse (@NonNull final String sBody)
  {
    return new PhormRawResponse (200, "application/json", StandardCharsets.UTF_8, sBody.getBytes (StandardCharsets.UTF_8));
  }

  @Test
  public void testCreateFromResponse () throws Exception
  {
    final PhormRepositoryStatus aStatus = PhormRepositoryStatus.createFromResponse (_createResponse (RESPONSE));
    assertEquals ("/opt/phorm/repositories.xml", aStatus.getConfigFile ());
    assertEquals (OffsetDateTime.parse ("2026-11-27T10:15:30.123+01:00"), aStatus.getLoadDateTime ());
    assertNull (aStatus.getServerException ());

    final ICommonsList <PhormRepository> aRepos = aStatus.getAllRepositories ();
    assertEquals (2, aRepos.size ());
    assertEquals (new PhormRepository ("example-repo", 2), aRepos.get (0));
    assertEquals (new PhormRepository ("empty-repo", 0), aRepos.get (1));

    final ICommonsOrderedMap <DVRCoordinate, String> aVESIDs = aStatus.getAllVESIDs ();
    assertEquals (2, aVESIDs.size ());
    assertEquals ("com.example:invoice:2026.11", aVESIDs.getFirstKey ().getAsSingleID ());
    assertEquals ("example-repo", aVESIDs.get (DVRCoordinate.parseOrNull ("com.example:creditnote:2026.11")));
    assertNotNull (aStatus.toString ());
  }

  @Test
  public void testCreateFromResponseNothingConfigured () throws Exception
  {
    // No repository file configured and nothing loaded yet - only the arrays are present
    final PhormRepositoryStatus aStatus = PhormRepositoryStatus.createFromResponse (_createResponse ("{\"repositories\":[],\"vesids\":[]}"));
    assertNull (aStatus.getConfigFile ());
    assertNull (aStatus.getLoadDateTime ());
    assertTrue (aStatus.getAllRepositories ().isEmpty ());
    assertTrue (aStatus.getAllVESIDs ().isEmpty ());
  }

  @Test
  public void testCreateFromResponseNoJson ()
  {
    try
    {
      PhormRepositoryStatus.createFromResponse (_createResponse ("<html/>"));
      fail ("Expected a PhormClientException");
    }
    catch (final PhormClientException ex)
    {
      assertEquals (EPhormErrorType.RESPONSE_ERROR, ex.getErrorType ());
    }
  }
}
