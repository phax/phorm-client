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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.diagnostics.error.IError;
import com.helger.diagnostics.error.level.EErrorLevel;
import com.helger.diagnostics.error.list.ErrorList;

/**
 * Test class for class {@link PhormRepositoryReloadResult}. It works on realistic
 * <code>/api/admin/repositories/reload</code> responses.
 *
 * @author Philip Helger
 */
public final class PhormRepositoryReloadResultTest
{
  private static final String RESPONSE_SUCCESS = """
      {
        "success": true,
        "added": [ "com.example:creditnote:2026.11" ],
        "removed": [ "com.example:invoice:2026.5" ],
        "configFile": "/opt/phorm/repositories.xml",
        "loadDateTime": "2026-11-27T10:15:30.123+01:00",
        "repositories": [ { "id": "example-repo", "vesCount": 2 } ],
        "vesids": [
          { "vesid": "com.example:creditnote:2026.11", "repository": "example-repo" },
          { "vesid": "com.example:invoice:2026.11", "repository": "example-repo" }
        ],
        "errors": [
          {
            "errorLevel": "WARN",
            "errorID": "ves-skipped",
            "errorText": "Skipped one VES"
          }
        ],
        "invocationDateTime": "2026-11-27T09:20:00.000Z",
        "invocationDurationMillis": 120
      }
      """;

  private static final String RESPONSE_FAILURE = """
      {
        "success": false,
        "errors": [
          {
            "errorLevel": "ERROR",
            "errorText": "Failed to read the repository configuration file"
          }
        ],
        "invocationDateTime": "2026-11-27T09:20:00.000Z",
        "invocationDurationMillis": 3
      }
      """;

  @NonNull
  private static PhormRawResponse _createResponse (final int nStatusCode, @NonNull final String sBody)
  {
    return new PhormRawResponse (nStatusCode,
                                 "application/json; charset=UTF-8",
                                 StandardCharsets.UTF_8,
                                 sBody.getBytes (StandardCharsets.UTF_8));
  }

  @Test
  public void testSuccess () throws Exception
  {
    final PhormRepositoryReloadResult aResult = PhormRepositoryReloadResult.createFromResponse (_createResponse (200,
                                                                                                                 RESPONSE_SUCCESS));
    assertTrue (aResult.isSuccess ());
    assertEquals (1, aResult.getAllAddedVESIDs ().size ());
    assertEquals ("com.example:creditnote:2026.11", aResult.getAllAddedVESIDs ().getFirstOrNull ().getAsSingleID ());
    assertEquals (1, aResult.getAllRemovedVESIDs ().size ());
    assertEquals ("com.example:invoice:2026.5", aResult.getAllRemovedVESIDs ().getFirstOrNull ().getAsSingleID ());

    // Warnings may be present on success too
    final ErrorList aErrors = aResult.getErrors ();
    assertEquals (1, aErrors.size ());
    final IError aError = aErrors.getFirstOrNull ();
    assertEquals (EErrorLevel.WARN, aError.getErrorLevel ());
    assertEquals ("ves-skipped", aError.getErrorID ());

    final PhormRepositoryStatus aStatus = aResult.getStatus ();
    assertNotNull (aStatus);
    assertEquals ("/opt/phorm/repositories.xml", aStatus.getConfigFile ());
    assertEquals (1, aStatus.getAllRepositories ().size ());
    assertEquals (2, aStatus.getAllVESIDs ().size ());
    assertNull (aResult.getServerException ());
    assertNotNull (aResult.toString ());
  }

  @Test
  public void testFailure () throws Exception
  {
    // phorm answers a failed reload with HTTP 500 and a regular body
    final PhormRepositoryReloadResult aResult = PhormRepositoryReloadResult.createFromResponse (_createResponse (500,
                                                                                                                 RESPONSE_FAILURE));
    assertFalse (aResult.isSuccess ());
    assertEquals (500, aResult.getRawResponse ().getStatusCode ());
    assertTrue (aResult.getAllAddedVESIDs ().isEmpty ());
    assertTrue (aResult.getAllRemovedVESIDs ().isEmpty ());
    assertNull (aResult.getStatus ());

    final ErrorList aErrors = aResult.getErrors ();
    assertEquals (1, aErrors.size ());
    assertEquals (EErrorLevel.ERROR, aErrors.getFirstOrNull ().getErrorLevel ());
  }
}
