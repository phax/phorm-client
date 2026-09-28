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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.enforce.ValueEnforcer;

/**
 * Thrown for every phorm API invocation that did not yield a usable result - a transport problem, a
 * rejected token, an unparsable payload or an unexpected HTTP status.
 * <p>
 * A document that is merely <em>invalid</em> is not an error. phorm answers it with HTTP 400 and a
 * regular result body (see the <code>phorm.api.response.onfailure.http400</code> setting), and this
 * library returns it as an ordinary {@link PhormValidationResult} with
 * {@link PhormValidationResult#isSuccess()} being <code>false</code>.
 *
 * @author Philip Helger
 */
public class PhormClientException extends Exception
{
  private final transient PhormRawResponse m_aResponse;

  /**
   * Constructor without an HTTP response, e.g. for transport level problems.
   *
   * @param sMessage
   *        The error message. May not be <code>null</code>.
   */
  public PhormClientException (@NonNull final String sMessage)
  {
    super (sMessage);
    m_aResponse = null;
  }

  /**
   * Constructor without an HTTP response, e.g. for transport level problems.
   *
   * @param sMessage
   *        The error message. May not be <code>null</code>.
   * @param aCause
   *        The causing exception. May be <code>null</code>.
   */
  public PhormClientException (@NonNull final String sMessage, @Nullable final Throwable aCause)
  {
    super (sMessage, aCause);
    m_aResponse = null;
  }

  /**
   * Constructor for a problem that is described by an HTTP response.
   *
   * @param sMessage
   *        The error message. May not be <code>null</code>.
   * @param aResponse
   *        The offending HTTP response. May not be <code>null</code>.
   */
  public PhormClientException (@NonNull final String sMessage, @NonNull final PhormRawResponse aResponse)
  {
    super (sMessage + " [HTTP " + aResponse.getStatusCode () + "]");
    ValueEnforcer.notNull (aResponse, "Response");
    m_aResponse = aResponse;
  }

  /**
   * @return <code>true</code> if this exception was caused by an HTTP response.
   */
  public final boolean hasResponse ()
  {
    return m_aResponse != null;
  }

  /**
   * @return The HTTP response that caused this exception, including its unparsed body. May be
   *         <code>null</code> for transport level problems.
   */
  @Nullable
  public final PhormRawResponse getResponse ()
  {
    return m_aResponse;
  }

  /**
   * @return The HTTP status code, or <code>-1</code> if this exception was not caused by an HTTP
   *         response.
   */
  public final int getStatusCode ()
  {
    return m_aResponse == null ? -1 : m_aResponse.getStatusCode ();
  }
}
