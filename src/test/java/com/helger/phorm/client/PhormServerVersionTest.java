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

import com.helger.json.IJson;
import com.helger.json.JsonObject;
import com.helger.json.serialize.JsonReader;

/**
 * Test class for class {@link PhormServerVersion}.
 *
 * @author Philip Helger
 */
public final class PhormServerVersionTest
{
  @Test
  public void testCreateFromJson ()
  {
    final IJson aJson = JsonReader.builder ()
                                  .source ("{\"major\":2,\"minor\":3,\"invocationDateTime\":\"2026-11-27T09:20:00Z\",\"invocationDurationMillis\":0}")
                                  .read ();
    assertNotNull (aJson);
    final PhormServerVersion aVersion = PhormServerVersion.createFromJson (aJson.getAsObject ());
    assertNotNull (aVersion);
    assertEquals (2, aVersion.getMajor ());
    assertEquals (3, aVersion.getMinor ());
    assertEquals ("2.3", aVersion.getAsString ());
    assertEquals (new PhormServerVersion (2, 3), aVersion);
    assertEquals (new PhormServerVersion (2, 3).hashCode (), aVersion.hashCode ());
    assertNotNull (aVersion.toString ());
  }

  @Test
  public void testCreateFromJsonInvalid ()
  {
    assertNull (PhormServerVersion.createFromJson (null));
    assertNull (PhormServerVersion.createFromJson (new JsonObject ()));
    assertNull (PhormServerVersion.createFromJson (new JsonObject ().add (CPhormClient.JSON_MAJOR, 2)));
    assertNull (PhormServerVersion.createFromJson (new JsonObject ().add (CPhormClient.JSON_MAJOR, 2)
                                                                    .add (CPhormClient.JSON_MINOR, -1)));
  }

  @Test
  public void testIsAtLeast ()
  {
    final PhormServerVersion aVersion = new PhormServerVersion (2, 3);
    assertTrue (aVersion.isAtLeast (2, 3));
    assertTrue (aVersion.isAtLeast (2, 2));
    assertTrue (aVersion.isAtLeast (1, 9));
    assertFalse (aVersion.isAtLeast (2, 4));
    assertFalse (aVersion.isAtLeast (3, 0));
  }

  @Test
  public void testCompareTo ()
  {
    assertTrue (new PhormServerVersion (2, 3).compareTo (new PhormServerVersion (2, 10)) < 0);
    assertTrue (new PhormServerVersion (3, 0).compareTo (new PhormServerVersion (2, 10)) > 0);
    assertEquals (0, new PhormServerVersion (2, 3).compareTo (new PhormServerVersion (2, 3)));
  }
}
