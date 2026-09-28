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

import com.helger.annotation.Nonempty;
import com.helger.base.id.IHasID;
import com.helger.base.lang.EnumHelper;

/**
 * The kind of problem that prevented a phorm API call from yielding a result. Every
 * {@link PhormClientException} carries one, so that a caller can tell a misconfiguration apart from
 * an outage without inspecting status codes - which matters for anybody who has to decide whether
 * retrying the same call later can help.
 * <p>
 * None of these means that the document was found to be invalid. A <em>content wise invalid</em>
 * document is a regular {@link PhormValidationResult} and never an exception.
 *
 * @author Philip Helger
 */
public enum EPhormErrorType implements IHasID <String>
{
  /**
   * The request was faulty and phorm never produced a verdict: the payload could not be read, the
   * VESID could not be parsed or resolved, the payload was not the expected format, or the
   * <code>X-Token</code> was missing or wrong. Repeating the identical call does not help - the
   * caller or its configuration has to change.
   */
  REQUEST_ERROR ("request"),
  /**
   * phorm could not be reached at all - a transport level problem - or it reported itself as
   * unavailable with an HTTP 5xx status. Repeating the call later may well succeed.
   */
  SERVICE_UNAVAILABLE ("unavailable"),
  /**
   * phorm answered, but the answer could not be used: an unexpected status code, or a body that is
   * not the requested representation. Use {@link PhormClientException#getResponse()} to see what
   * was actually received.
   */
  RESPONSE_ERROR ("response");

  private final String m_sID;

  EPhormErrorType (@NonNull @Nonempty final String sID)
  {
    m_sID = sID;
  }

  @NonNull
  @Nonempty
  public String getID ()
  {
    return m_sID;
  }

  @Nullable
  public static EPhormErrorType getFromIDOrNull (@Nullable final String sID)
  {
    return EnumHelper.getFromIDOrNull (EPhormErrorType.class, sID);
  }
}
