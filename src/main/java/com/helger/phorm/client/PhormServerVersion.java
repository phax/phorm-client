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

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.hashcode.HashCodeGenerator;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.json.IJsonObject;

/**
 * The major and minor version of a phorm instance, as reported by <code>GET /api/get/version</code>.
 * phorm deliberately does not disclose the micro version: features only change with a minor
 * version, and that is what this class is meant for - see {@link #isAtLeast(int, int)}.
 *
 * @author Philip Helger
 * @since 1.1.0
 */
@Immutable
public class PhormServerVersion implements Comparable <PhormServerVersion>
{
  private final int m_nMajor;
  private final int m_nMinor;

  /**
   * Constructor
   *
   * @param nMajor
   *        The major version. Must be &ge; 0.
   * @param nMinor
   *        The minor version. Must be &ge; 0.
   */
  public PhormServerVersion (@Nonnegative final int nMajor, @Nonnegative final int nMinor)
  {
    ValueEnforcer.isGE0 (nMajor, "Major");
    ValueEnforcer.isGE0 (nMinor, "Minor");
    m_nMajor = nMajor;
    m_nMinor = nMinor;
  }

  /**
   * @return The major version. Always &ge; 0.
   */
  @Nonnegative
  public int getMajor ()
  {
    return m_nMajor;
  }

  /**
   * @return The minor version. Always &ge; 0.
   */
  @Nonnegative
  public int getMinor ()
  {
    return m_nMinor;
  }

  /**
   * Check if this version is the provided one or a later one. Use it to check whether a feature is
   * available, e.g. <code>isAtLeast (2, 3)</code> for the admin APIs.
   *
   * @param nMajor
   *        The minimum major version.
   * @param nMinor
   *        The minimum minor version.
   * @return <code>true</code> if this version is &ge; the provided one.
   */
  public boolean isAtLeast (final int nMajor, final int nMinor)
  {
    return m_nMajor > nMajor || (m_nMajor == nMajor && m_nMinor >= nMinor);
  }

  /**
   * @return The version as <code>major.minor</code>, e.g. <code>2.3</code>. Never
   *         <code>null</code>.
   */
  @NonNull
  public String getAsString ()
  {
    return m_nMajor + "." + m_nMinor;
  }

  public int compareTo (@NonNull final PhormServerVersion aOther)
  {
    final int ret = Integer.compare (m_nMajor, aOther.m_nMajor);
    return ret != 0 ? ret : Integer.compare (m_nMinor, aOther.m_nMinor);
  }

  @Override
  public boolean equals (final Object o)
  {
    if (o == this)
      return true;
    if (o == null || !getClass ().equals (o.getClass ()))
      return false;
    final PhormServerVersion rhs = (PhormServerVersion) o;
    return m_nMajor == rhs.m_nMajor && m_nMinor == rhs.m_nMinor;
  }

  @Override
  public int hashCode ()
  {
    return new HashCodeGenerator (this).append (m_nMajor).append (m_nMinor).getHashCode ();
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("Major", m_nMajor).append ("Minor", m_nMinor).getToString ();
  }

  /**
   * Convert the <code>/api/get/version</code> response.
   *
   * @param aObj
   *        The JSON object to be converted. May be <code>null</code>.
   * @return <code>null</code> if the object is <code>null</code>, or if the major or minor version
   *         is missing or negative.
   */
  @Nullable
  public static PhormServerVersion createFromJson (@Nullable final IJsonObject aObj)
  {
    if (aObj == null)
      return null;

    final int nMajor = aObj.getAsInt (CPhormClient.JSON_MAJOR, -1);
    final int nMinor = aObj.getAsInt (CPhormClient.JSON_MINOR, -1);
    if (nMajor < 0 || nMinor < 0)
      return null;

    return new PhormServerVersion (nMajor, nMinor);
  }
}
