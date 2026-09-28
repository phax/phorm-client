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
 * The country specific rule set to be applied by the hybrid PDF validation API. It drives the
 * <code>BR-HYBRID-DE-*</code> and <code>BR-HYBRID-FR-*</code> business rules.
 *
 * @author Philip Helger
 */
public enum EPhormHybridCountry implements IHasID <String>
{
  /** Germany */
  DE ("DE"),
  /** France */
  FR ("FR"),
  /** Any other country - the phorm side default */
  OTHER ("OTHER");

  private final String m_sID;

  EPhormHybridCountry (@NonNull @Nonempty final String sID)
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
  public static EPhormHybridCountry getFromIDOrNull (@Nullable final String sID)
  {
    return EnumHelper.getFromIDOrNull (EPhormHybridCountry.class, sID);
  }
}
