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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.HttpEntity;
import org.junit.Test;

import com.helger.base.io.nonblocking.NonBlockingByteArrayInputStream;
import com.helger.base.io.nonblocking.NonBlockingByteArrayOutputStream;
import com.helger.base.io.stream.HasInputStream;
import com.helger.mime.CMimeType;
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
        assertEquals (EPhormErrorType.REQUEST_ERROR, ex.getErrorType ());
        assertEquals (-1, ex.getStatusCode ());
      }
    }
  }

  @Test
  public void testUnsupportedCountryCode ()
  {
    try (final PhormClient aClient = new PhormClient ("http://localhost:8080", "t"))
    {
      try
      {
        // Must fail before any HTTP connection is attempted
        aClient.hybridValidate (new byte [0], "DE&other=1");
        fail ("Expected a PhormClientException");
      }
      catch (final PhormClientException ex)
      {
        assertEquals (EPhormErrorType.REQUEST_ERROR, ex.getErrorType ());
        assertEquals (-1, ex.getStatusCode ());
      }
    }
  }

  @Test
  public void testCountryQuery () throws Exception
  {
    assertNull (PhormClient.getCountryQuery (null));
    assertNull (PhormClient.getCountryQuery (""));
    assertEquals ("country=DE", PhormClient.getCountryQuery (EPhormHybridCountry.DE.getID ()));
    // A country that phorm learns about after this release must pass through unchanged, instead of
    // requiring a new version of this library
    assertEquals ("country=AT", PhormClient.getCountryQuery ("AT"));
  }

  @Test
  public void testStreamingEntityReadMultiple () throws Exception
  {
    final byte [] aPayload = "<Invoice/>".getBytes (StandardCharsets.UTF_8);
    final HttpEntity aEntity = PhormClient.createStreamingEntity (HasInputStream.multiple (() -> new NonBlockingByteArrayInputStream (aPayload)),
                                                                  CMimeType.APPLICATION_XML);
    // Repeatable, so that the HTTP client may replay the request
    assertTrue (aEntity.isRepeatable ());
    // Unknown up front - the payload is sent chunked
    assertEquals (-1, aEntity.getContentLength ());
    assertEquals (CMimeType.APPLICATION_XML.getAsString (), aEntity.getContentType ());

    // Twice, because "repeatable" is only worth something if it actually is
    for (int i = 0; i < 2; ++i)
      try (final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ())
      {
        aEntity.writeTo (aBAOS);
        assertArrayEquals (aPayload, aBAOS.toByteArray ());
      }
  }

  @Test
  public void testStreamingEntityReadOnce () throws Exception
  {
    final byte [] aPayload = "%PDF-1.7".getBytes (StandardCharsets.UTF_8);
    final HttpEntity aEntity = PhormClient.createStreamingEntity (HasInputStream.once (() -> new NonBlockingByteArrayInputStream (aPayload)),
                                                                  CMimeType.APPLICATION_PDF);
    assertFalse (aEntity.isRepeatable ());
    assertEquals (-1, aEntity.getContentLength ());

    try (final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ())
    {
      aEntity.writeTo (aBAOS);
      assertArrayEquals (aPayload, aBAOS.toByteArray ());
    }
  }

  @Test
  public void testStreamingEntityUnopenablePayload ()
  {
    try
    {
      PhormClient.createStreamingEntity (HasInputStream.once (() -> null), CMimeType.APPLICATION_XML);
      fail ("Expected a PhormClientException");
    }
    catch (final PhormClientException ex)
    {
      // Nothing was sent, so this is a faulty request and not an unavailable service
      assertEquals (EPhormErrorType.REQUEST_ERROR, ex.getErrorType ());
      assertFalse (ex.hasResponse ());
    }
  }
}
