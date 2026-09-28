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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.helger.peppolid.factory.SimpleIdentifierFactory;

/**
 * Test class for class {@link PhormClient}.
 *
 * @author Philip Helger
 */
public final class PhormClientTest
{
  @Test
  public void testBuilderDefaults ()
  {
    try (final PhormClient aClient = PhormClient.builder ()
                                                .baseURL ("http://localhost:8080")
                                                .token ("phorm-dev-token")
                                                .build ())
    {
      assertEquals ("http://localhost:8080", aClient.getBaseURL ());
      assertEquals (CPhormClient.DEFAULT_API_PATH, aClient.getAPIPath ());
      assertSame (SimpleIdentifierFactory.INSTANCE, aClient.getIdentifierFactory ());
    }
  }

  @Test
  public void testBuilderCustomized ()
  {
    try (final PhormClient aClient = PhormClient.builder ()
                                                .baseURL ("https://phorm.example.org/")
                                                .apiPath ("/rest")
                                                .token ("secret")
                                                .identifierFactory (SimpleIdentifierFactory.INSTANCE)
                                                .build ())
    {
      // The trailing slash is removed so that the paths can be appended blindly
      assertEquals ("https://phorm.example.org", aClient.getBaseURL ());
      assertEquals ("/rest", aClient.getAPIPath ());
      assertSame (SimpleIdentifierFactory.INSTANCE, aClient.getIdentifierFactory ());
    }
  }

  @Test
  public void testBuilderWithoutBaseURL ()
  {
    try
    {
      PhormClient.builder ().token ("secret").build ();
      fail ("Expected an IllegalStateException");
    }
    catch (final IllegalStateException ex)
    {
      // Expected
    }
  }

  @Test
  public void testConstructorWithoutBaseURL ()
  {
    try
    {
      new PhormClient ("", "secret").close ();
      fail ("Expected an IllegalArgumentException");
    }
    catch (final IllegalArgumentException ex)
    {
      // Expected
    }
  }

  @Test
  public void testUnparsableVESID () throws Exception
  {
    try (final PhormClient aClient = new PhormClient ("http://localhost:8080", "t"))
    {
      try
      {
        // Must fail before any HTTP connection is attempted
        aClient.validate ("this is no coordinate", new byte [0]);
        fail ("Expected a PhormClientException");
      }
      catch (final PhormClientException ex)
      {
        assertEquals (-1, ex.getStatusCode ());
      }
    }
  }
}
