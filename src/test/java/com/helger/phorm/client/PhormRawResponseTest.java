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

import org.junit.Test;

import com.helger.mime.CMimeType;

/**
 * Test class for class {@link PhormRawResponse}.
 *
 * @author Philip Helger
 */
public final class PhormRawResponseTest
{
  @Test
  public void testJsonBody ()
  {
    final byte [] aBody = "{\"success\":true}".getBytes (StandardCharsets.UTF_8);
    final PhormRawResponse aResp = new PhormRawResponse (200,
                                                         "application/json; charset=UTF-8",
                                                         StandardCharsets.UTF_8,
                                                         aBody);
    assertEquals (200, aResp.getStatusCode ());
    assertTrue (aResp.isStatusSuccess ());
    assertTrue (aResp.hasBody ());
    assertTrue (aResp.hasMimeType (CMimeType.APPLICATION_JSON));
    assertFalse (aResp.hasMimeType (CMimeType.APPLICATION_XML));
    assertEquals ("{\"success\":true}", aResp.getBodyAsString ());
    assertNotNull (aResp.getBodyAsJsonObject ());
    assertTrue (aResp.getBodyAsJsonObject ().getAsBoolean ("success", false));
    // Independent copies
    assertNotNull (aResp.getBody ());
    assertTrue (aResp.getBody () != aResp.directGetBody ());
  }

  @Test
  public void testMimeTypeWithoutParameters ()
  {
    final PhormRawResponse aResp = new PhormRawResponse (200, "APPLICATION/XML", StandardCharsets.UTF_8, new byte [0]);
    assertTrue (aResp.hasMimeType (CMimeType.APPLICATION_XML));
  }

  @Test
  public void testXmlBody ()
  {
    final byte [] aBody = "<validationResults><success>false</success></validationResults>".getBytes (StandardCharsets.UTF_8);
    final PhormRawResponse aResp = new PhormRawResponse (400, "application/xml", StandardCharsets.UTF_8, aBody);
    assertFalse (aResp.isStatusSuccess ());
    assertNotNull (aResp.getBodyAsMicroDocument ());
    assertEquals ("validationResults", aResp.getBodyAsMicroDocument ().getDocumentElement ().getTagName ());
    // Not JSON
    assertNull (aResp.getBodyAsJsonObject ());
  }

  @Test
  public void testNoContent ()
  {
    final PhormRawResponse aResp = PhormRawResponse.createNoContent (204);
    assertEquals (204, aResp.getStatusCode ());
    assertTrue (aResp.isStatusSuccess ());
    assertFalse (aResp.hasBody ());
    assertNull (aResp.getBody ());
    assertNull (aResp.getBodyAsString ());
    assertNull (aResp.getBodyAsJsonObject ());
    assertNull (aResp.getBodyAsMicroDocument ());
    assertFalse (aResp.hasMimeType (CMimeType.APPLICATION_JSON));
  }

  @Test
  public void testGetBodyAsJsonObjectOrThrow ()
  {
    final PhormRawResponse aResp = new PhormRawResponse (400,
                                                         "text/plain",
                                                         StandardCharsets.UTF_8,
                                                         "Failed to read the message body as XML".getBytes (StandardCharsets.UTF_8));
    try
    {
      aResp.getBodyAsJsonObjectOrThrow ();
      throw new IllegalStateException ("Expected a PhormClientException");
    }
    catch (final PhormClientException ex)
    {
      assertTrue (ex.hasResponse ());
      assertEquals (400, ex.getStatusCode ());
      assertEquals ("Failed to read the message body as XML", ex.getResponse ().getBodyAsString ());
    }
  }
}
