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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.array.ArrayHelper;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.string.StringHelper;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.http.CHttp;
import com.helger.json.IJson;
import com.helger.json.IJsonObject;
import com.helger.json.serialize.JsonReader;
import com.helger.mime.IMimeType;
import com.helger.xml.microdom.IMicroDocument;
import com.helger.xml.microdom.serialize.MicroReader;

/**
 * The unparsed HTTP response of a phorm API call - status code, content type and body. Every typed
 * result of this library carries the raw response it was built from, and every
 * {@link PhormClientException} that was caused by an HTTP response carries it as well. It is the
 * escape hatch for everything the typed model does not expose.
 *
 * @author Philip Helger
 */
@Immutable
public class PhormRawResponse
{
  private final int m_nStatusCode;
  private final String m_sContentType;
  private final Charset m_aCharset;
  private final byte [] m_aBody;

  /**
   * Constructor
   *
   * @param nStatusCode
   *        The HTTP status code.
   * @param sContentType
   *        The value of the HTTP <code>Content-Type</code> header. May be <code>null</code>.
   * @param aCharset
   *        The charset the body is encoded in. May not be <code>null</code>.
   * @param aBody
   *        The response body. May be <code>null</code> for responses without content.
   */
  public PhormRawResponse (final int nStatusCode,
                           @Nullable final String sContentType,
                           @NonNull final Charset aCharset,
                           final byte @Nullable [] aBody)
  {
    ValueEnforcer.notNull (aCharset, "Charset");
    m_nStatusCode = nStatusCode;
    m_sContentType = sContentType;
    m_aCharset = aCharset;
    m_aBody = aBody;
  }

  /**
   * @return The HTTP status code as received.
   */
  public int getStatusCode ()
  {
    return m_nStatusCode;
  }

  /**
   * @return <code>true</code> if the status code is in the 2xx range.
   */
  public boolean isStatusSuccess ()
  {
    return m_nStatusCode >= CHttp.HTTP_OK && m_nStatusCode < CHttp.HTTP_MULTIPLE_CHOICES;
  }

  /**
   * @return The raw value of the HTTP <code>Content-Type</code> header. May be <code>null</code>.
   */
  @Nullable
  public String getContentType ()
  {
    return m_sContentType;
  }

  /**
   * Check if the response content type matches the provided MIME type, ignoring any parameters like
   * <code>charset</code>.
   *
   * @param aMimeType
   *        The MIME type to compare with. May not be <code>null</code>.
   * @return <code>true</code> if the content type matches.
   */
  public boolean hasMimeType (@NonNull final IMimeType aMimeType)
  {
    ValueEnforcer.notNull (aMimeType, "MimeType");
    if (StringHelper.isEmpty (m_sContentType))
      return false;

    final int nIndex = m_sContentType.indexOf (';');
    final String sPlain = nIndex < 0 ? m_sContentType : m_sContentType.substring (0, nIndex);
    return sPlain.trim ().equalsIgnoreCase (aMimeType.getAsString ());
  }

  /**
   * @return The charset the body is encoded in. Never <code>null</code>.
   */
  @NonNull
  public Charset getCharset ()
  {
    return m_aCharset;
  }

  /**
   * @return <code>true</code> if a non-empty response body is present.
   */
  public boolean hasBody ()
  {
    return m_aBody != null && m_aBody.length > 0;
  }

  /**
   * @return The response body without copying it. Handle with care. May be <code>null</code>.
   */
  public byte @Nullable [] directGetBody ()
  {
    return m_aBody;
  }

  /**
   * @return A copy of the response body. May be <code>null</code>.
   */
  @Nullable
  @ReturnsMutableCopy
  public byte [] getBody ()
  {
    return ArrayHelper.getCopy (m_aBody);
  }

  /**
   * @return The response body interpreted with {@link #getCharset()}. May be <code>null</code>.
   */
  @Nullable
  public String getBodyAsString ()
  {
    return m_aBody == null ? null : new String (m_aBody, m_aCharset);
  }

  /**
   * @return The response body parsed as a JSON object, or <code>null</code> if the body is absent
   *         or is not a JSON object.
   */
  @Nullable
  public IJsonObject getBodyAsJsonObject ()
  {
    if (!hasBody ())
      return null;

    final IJson aJson = JsonReader.builder ().source (m_aBody, m_aCharset).read ();
    return aJson == null ? null : aJson.getAsObject ();
  }

  /**
   * @return The response body parsed as a JSON object. Never <code>null</code>.
   * @throws PhormClientException
   *         If the body is absent or is not a JSON object.
   */
  @NonNull
  public IJsonObject getBodyAsJsonObjectOrThrow () throws PhormClientException
  {
    final IJsonObject ret = getBodyAsJsonObject ();
    if (ret == null)
      throw new PhormClientException ("The phorm response could not be parsed as a JSON object", this);
    return ret;
  }

  /**
   * @return The response body parsed as an XML document, or <code>null</code> if the body is absent
   *         or is not well-formed XML.
   */
  @Nullable
  public IMicroDocument getBodyAsMicroDocument ()
  {
    return hasBody () ? MicroReader.readMicroXML (m_aBody) : null;
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("StatusCode", m_nStatusCode)
                                       .appendIfNotNull ("ContentType", m_sContentType)
                                       .append ("Charset", m_aCharset)
                                       .append ("BodyBytes", m_aBody == null ? 0 : m_aBody.length)
                                       .getToString ();
  }

  /**
   * @param nStatusCode
   *        HTTP status code
   * @return A response representing "no content at all", e.g. for an HTTP 204 answer. Never
   *         <code>null</code>.
   */
  @NonNull
  public static PhormRawResponse createNoContent (final int nStatusCode)
  {
    return new PhormRawResponse (nStatusCode, null, StandardCharsets.UTF_8, null);
  }
}
