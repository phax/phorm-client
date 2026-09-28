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
import com.helger.mime.CMimeType;
import com.helger.mime.IMimeType;

/**
 * The representation in which phorm should deliver a response. It is selected via the HTTP
 * <code>Accept</code> header. JSON is the default, because it is the most compact of the three and
 * the only one this library can convert into a typed result.
 *
 * @author Philip Helger
 */
public enum EPhormResponseFormat implements IHasID <String>
{
  /** JSON - the phorm default and the internal default of this library */
  JSON ("json", CMimeType.APPLICATION_JSON),
  /** XML */
  XML ("xml", CMimeType.APPLICATION_XML),
  /** A ready to render HTML report */
  HTML ("html", CMimeType.TEXT_HTML);

  private final String m_sID;
  private final IMimeType m_aMimeType;

  EPhormResponseFormat (@NonNull @Nonempty final String sID, @NonNull final IMimeType aMimeType)
  {
    m_sID = sID;
    m_aMimeType = aMimeType;
  }

  @NonNull
  @Nonempty
  public String getID ()
  {
    return m_sID;
  }

  /**
   * @return The MIME type to be sent in the HTTP <code>Accept</code> header. Never
   *         <code>null</code>.
   */
  @NonNull
  public IMimeType getMimeType ()
  {
    return m_aMimeType;
  }

  @Nullable
  public static EPhormResponseFormat getFromIDOrNull (@Nullable final String sID)
  {
    return EnumHelper.getFromIDOrNull (EPhormResponseFormat.class, sID);
  }
}
