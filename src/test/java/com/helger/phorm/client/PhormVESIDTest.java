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

import org.junit.Test;

import com.helger.json.IJsonObject;
import com.helger.json.JsonObject;

/**
 * Test class for class {@link PhormVESID}.
 *
 * @author Philip Helger
 */
public final class PhormVESIDTest
{
  @Test
  public void testCreateFromJson ()
  {
    final IJsonObject aObj = new JsonObject ().add (CPhormClient.JSON_VESID, "eu.peppol.bis3:invoice:2024.5")
                                              .add (CPhormClient.JSON_NAME, "Peppol BIS Billing UBL Invoice V3")
                                              .add (CPhormClient.JSON_DEPRECATED, false)
                                              .add (CPhormClient.JSON_LATEST, true);
    final PhormVESID aVESID = PhormVESID.createFromJson (aObj);
    assertNotNull (aVESID);
    assertEquals ("eu.peppol.bis3:invoice:2024.5", aVESID.getVESID ().getAsSingleID ());
    assertEquals ("Peppol BIS Billing UBL Invoice V3", aVESID.getName ());
    assertFalse (aVESID.isDeprecated ());
    assertTrue (aVESID.isLatest ());
    assertEquals (aVESID, PhormVESID.createFromJson (aObj));
    assertEquals (aVESID.hashCode (), PhormVESID.createFromJson (aObj).hashCode ());
    assertNotNull (aVESID.toString ());
  }

  @Test
  public void testCreateFromJsonWithoutOptionalFields ()
  {
    // "latest" is only present if it is true
    final PhormVESID aVESID = PhormVESID.createFromJson (new JsonObject ().add (CPhormClient.JSON_VESID,
                                                                                "eu.peppol.bis3:invoice:1.0")
                                                                          .add (CPhormClient.JSON_DEPRECATED, true));
    assertNotNull (aVESID);
    assertTrue (aVESID.isDeprecated ());
    assertFalse (aVESID.isLatest ());
    assertNull (aVESID.getName ());
  }

  @Test
  public void testCreateFromJsonInvalid ()
  {
    assertNull (PhormVESID.createFromJson (null));
    assertNull (PhormVESID.createFromJson (new JsonObject ()));
    assertNull (PhormVESID.createFromJson (new JsonObject ().add (CPhormClient.JSON_VESID, "not a coordinate")));
  }
}
