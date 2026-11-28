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
import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.hashcode.HashCodeGenerator;
import com.helger.base.string.StringHelper;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.json.IJsonObject;

/**
 * A single external repository as reported by the admin API <code>/api/admin/repositories</code>.
 *
 * @author Philip Helger
 * @since 1.1.0
 */
@Immutable
public class PhormRepository
{
  private final String m_sID;
  private final long m_nVESCount;

  /**
   * Constructor
   *
   * @param sID
   *        The repository ID. May neither be <code>null</code> nor empty.
   * @param nVESCount
   *        The number of VES loaded from this repository. Must be &ge; 0.
   */
  public PhormRepository (@NonNull @Nonempty final String sID, final long nVESCount)
  {
    ValueEnforcer.notEmpty (sID, "ID");
    ValueEnforcer.isGE0 (nVESCount, "VESCount");
    m_sID = sID;
    m_nVESCount = nVESCount;
  }

  /**
   * @return The repository ID, as used by {@link PhormVESID#getRepositoryID()}. Never
   *         <code>null</code> nor empty.
   */
  @NonNull
  @Nonempty
  public String getID ()
  {
    return m_sID;
  }

  /**
   * @return The number of VES loaded from this repository. Always &ge; 0.
   */
  public long getVESCount ()
  {
    return m_nVESCount;
  }

  @Override
  public boolean equals (final Object o)
  {
    if (o == this)
      return true;
    if (o == null || !getClass ().equals (o.getClass ()))
      return false;
    final PhormRepository rhs = (PhormRepository) o;
    return m_sID.equals (rhs.m_sID) && m_nVESCount == rhs.m_nVESCount;
  }

  @Override
  public int hashCode ()
  {
    return new HashCodeGenerator (this).append (m_sID).append (m_nVESCount).getHashCode ();
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("ID", m_sID).append ("VESCount", m_nVESCount).getToString ();
  }

  /**
   * Convert a single entry of the <code>repositories</code> array of the admin API response.
   *
   * @param aObj
   *        The JSON object to be converted. May be <code>null</code>.
   * @return <code>null</code> if the object is <code>null</code> or carries no repository ID.
   */
  @Nullable
  public static PhormRepository createFromJson (@Nullable final IJsonObject aObj)
  {
    if (aObj == null)
      return null;

    final String sID = aObj.getAsString (CPhormClient.JSON_ID);
    if (StringHelper.isEmpty (sID))
      return null;

    return new PhormRepository (sID, Math.max (0, aObj.getAsLong (CPhormClient.JSON_VES_COUNT, 0)));
  }
}
