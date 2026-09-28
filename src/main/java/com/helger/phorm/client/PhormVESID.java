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

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.equals.EqualsHelper;
import com.helger.base.hashcode.HashCodeGenerator;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.json.IJsonObject;

/**
 * A single entry of the <code>/api/get/vesids</code> response - one validation rule set registered
 * in the target phorm instance.
 *
 * @author Philip Helger
 */
@Immutable
public class PhormVESID
{
  private final DVRCoordinate m_aVESID;
  private final String m_sName;
  private final boolean m_bDeprecated;
  private final boolean m_bLatest;

  /**
   * Constructor
   *
   * @param aVESID
   *        The validation executor set coordinate. May not be <code>null</code>.
   * @param sName
   *        The human readable name. May be <code>null</code>.
   * @param bDeprecated
   *        <code>true</code> if the rule set is deprecated.
   * @param bLatest
   *        <code>true</code> if this is the latest version of the rule set.
   */
  public PhormVESID (@NonNull final DVRCoordinate aVESID,
                     @Nullable final String sName,
                     final boolean bDeprecated,
                     final boolean bLatest)
  {
    ValueEnforcer.notNull (aVESID, "VESID");
    m_aVESID = aVESID;
    m_sName = sName;
    m_bDeprecated = bDeprecated;
    m_bLatest = bLatest;
  }

  /**
   * @return The validation executor set coordinate. Never <code>null</code>.
   */
  @NonNull
  public DVRCoordinate getVESID ()
  {
    return m_aVESID;
  }

  /**
   * @return The human readable name of the rule set. May be <code>null</code>.
   */
  @Nullable
  public String getName ()
  {
    return m_sName;
  }

  /**
   * @return <code>true</code> if the rule set is deprecated. Deprecated rule sets are only returned
   *         if they were explicitly requested.
   */
  public boolean isDeprecated ()
  {
    return m_bDeprecated;
  }

  /**
   * @return <code>true</code> if this is the latest version of the rule set.
   */
  public boolean isLatest ()
  {
    return m_bLatest;
  }

  @Override
  public boolean equals (final Object o)
  {
    if (o == this)
      return true;
    if (o == null || !getClass ().equals (o.getClass ()))
      return false;
    final PhormVESID rhs = (PhormVESID) o;
    return m_aVESID.equals (rhs.m_aVESID) &&
           EqualsHelper.equals (m_sName, rhs.m_sName) &&
           m_bDeprecated == rhs.m_bDeprecated &&
           m_bLatest == rhs.m_bLatest;
  }

  @Override
  public int hashCode ()
  {
    return new HashCodeGenerator (this).append (m_aVESID)
                                       .append (m_sName)
                                       .append (m_bDeprecated)
                                       .append (m_bLatest)
                                       .getHashCode ();
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("VESID", m_aVESID)
                                       .appendIfNotNull ("Name", m_sName)
                                       .append ("Deprecated", m_bDeprecated)
                                       .append ("Latest", m_bLatest)
                                       .getToString ();
  }

  /**
   * Convert a single JSON entry of the <code>/api/get/vesids</code> response.
   *
   * @param aObj
   *        The JSON object to be converted. May be <code>null</code>.
   * @return <code>null</code> if the object is <code>null</code> or carries no parsable VESID.
   */
  @Nullable
  public static PhormVESID createFromJson (@Nullable final IJsonObject aObj)
  {
    if (aObj == null)
      return null;

    final DVRCoordinate aVESID = DVRCoordinate.parseOrNull (aObj.getAsString (CPhormClient.JSON_VESID));
    if (aVESID == null)
      return null;

    return new PhormVESID (aVESID,
                           aObj.getAsString (CPhormClient.JSON_NAME),
                           aObj.getAsBoolean (CPhormClient.JSON_DEPRECATED, false),
                           aObj.getAsBoolean (CPhormClient.JSON_LATEST, false));
  }
}
