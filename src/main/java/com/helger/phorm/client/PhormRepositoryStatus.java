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

import java.time.OffsetDateTime;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.string.StringHelper;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.json.IJson;
import com.helger.json.IJsonArray;
import com.helger.json.IJsonObject;
import com.helger.phive.result.json.CPhiveJson;

/**
 * The state of the external repositories of a phorm instance, as reported by the admin API
 * <code>GET /api/admin/repositories</code>. The same information is part of a successful
 * {@link PhormRepositoryReloadResult}.
 * <p>
 * Only the VES loaded from external repositories are listed here. The built-in VES are part of
 * {@link PhormClient#getAllVESIDs(boolean)} only.
 *
 * @author Philip Helger
 * @since 1.1.0
 */
@Immutable
public class PhormRepositoryStatus
{
  private final PhormRawResponse m_aRawResponse;
  private final IJsonObject m_aJson;
  private final String m_sConfigFile;
  private final OffsetDateTime m_aLoadDateTime;
  private final ICommonsList <PhormRepository> m_aRepositories;
  private final ICommonsOrderedMap <DVRCoordinate, String> m_aVESIDs;

  /**
   * Constructor
   *
   * @param aRawResponse
   *        The underlying HTTP response. May not be <code>null</code>.
   * @param aJson
   *        The parsed response body. May not be <code>null</code>.
   * @param sConfigFile
   *        The absolute path of the repository configuration file on the server. May be
   *        <code>null</code> if none is configured.
   * @param aLoadDateTime
   *        The date and time the external repositories were last loaded successfully. May be
   *        <code>null</code> if they were never loaded.
   * @param aRepositories
   *        The external repositories. May not be <code>null</code>.
   * @param aVESIDs
   *        All VESIDs loaded from external repositories, mapped to the ID of the repository they
   *        were loaded from. May not be <code>null</code>.
   */
  public PhormRepositoryStatus (@NonNull final PhormRawResponse aRawResponse,
                                @NonNull final IJsonObject aJson,
                                @Nullable final String sConfigFile,
                                @Nullable final OffsetDateTime aLoadDateTime,
                                @NonNull final Iterable <PhormRepository> aRepositories,
                                @NonNull final Map <DVRCoordinate, String> aVESIDs)
  {
    ValueEnforcer.notNull (aRawResponse, "RawResponse");
    ValueEnforcer.notNull (aJson, "Json");
    ValueEnforcer.notNullNoNullValue (aRepositories, "Repositories");
    ValueEnforcer.notNull (aVESIDs, "VESIDs");
    m_aRawResponse = aRawResponse;
    m_aJson = aJson;
    m_sConfigFile = sConfigFile;
    m_aLoadDateTime = aLoadDateTime;
    m_aRepositories = new CommonsArrayList <> (aRepositories);
    m_aVESIDs = new CommonsLinkedHashMap <> (aVESIDs);
  }

  /**
   * @return The unparsed HTTP response this status was built from. Never <code>null</code>. Use it
   *         to reach anything this class does not expose.
   */
  @NonNull
  public PhormRawResponse getRawResponse ()
  {
    return m_aRawResponse;
  }

  /**
   * @return The complete response body as JSON. Never <code>null</code>.
   */
  @NonNull
  public IJsonObject getAsJson ()
  {
    return m_aJson;
  }

  /**
   * @return The absolute path of the repository configuration file on the server, as configured in
   *         <code>phorm.repositories.file</code>. May be <code>null</code> if none is configured.
   */
  @Nullable
  public String getConfigFile ()
  {
    return m_sConfigFile;
  }

  /**
   * @return The date and time the external repositories were last loaded successfully. May be
   *         <code>null</code> if they were never loaded, or if the value is unparsable.
   */
  @Nullable
  public OffsetDateTime getLoadDateTime ()
  {
    return m_aLoadDateTime;
  }

  /**
   * @return All external repositories, in the order reported by phorm. Never <code>null</code> but
   *         maybe empty.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <PhormRepository> getAllRepositories ()
  {
    return m_aRepositories.getClone ();
  }

  /**
   * @return All VESIDs loaded from external repositories, mapped to the ID of the repository they
   *         were loaded from, in the order reported by phorm. Never <code>null</code> but maybe
   *         empty.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsOrderedMap <DVRCoordinate, String> getAllVESIDs ()
  {
    return m_aVESIDs.getClone ();
  }

  /**
   * @return The server side exception, if phorm failed while producing the status. May be
   *         <code>null</code>, which is the regular case.
   */
  @Nullable
  public IJsonObject getServerException ()
  {
    return m_aJson.getAsObject (CPhiveJson.JSON_EXCEPTION);
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("RawResponse", m_aRawResponse)
                                       .appendIfNotNull ("ConfigFile", m_sConfigFile)
                                       .appendIfNotNull ("LoadDateTime", m_aLoadDateTime)
                                       .append ("Repositories", m_aRepositories)
                                       .append ("VESIDs", m_aVESIDs)
                                       .getToString ();
  }

  /**
   * Build a status from an already parsed admin API response. The reload API delivers the same
   * fields, which is why this is separate from {@link #createFromResponse(PhormRawResponse)}.
   *
   * @param aResponse
   *        The HTTP response the JSON was parsed from. May not be <code>null</code>.
   * @param aJson
   *        The parsed response body. May not be <code>null</code>.
   * @return Never <code>null</code>.
   */
  @NonNull
  static PhormRepositoryStatus createFromJson (@NonNull final PhormRawResponse aResponse,
                                               @NonNull final IJsonObject aJson)
  {
    OffsetDateTime aLoadDT = null;
    final String sLoadDT = aJson.getAsString (CPhormClient.JSON_LOAD_DATETIME);
    if (StringHelper.isNotEmpty (sLoadDT))
      try
      {
        aLoadDT = OffsetDateTime.parse (sLoadDT);
      }
      catch (final RuntimeException ex)
      {
        // Unparsable - leave it null
      }

    final ICommonsList <PhormRepository> aRepositories = new CommonsArrayList <> ();
    final IJsonArray aJsonRepos = aJson.getAsArray (CPhormClient.JSON_REPOSITORIES);
    if (aJsonRepos != null)
      for (final IJson aItem : aJsonRepos)
      {
        final PhormRepository aRepo = PhormRepository.createFromJson (aItem.getAsObject ());
        if (aRepo != null)
          aRepositories.add (aRepo);
      }

    final ICommonsOrderedMap <DVRCoordinate, String> aVESIDs = new CommonsLinkedHashMap <> ();
    final IJsonArray aJsonVESIDs = aJson.getAsArray (CPhormClient.JSON_VESIDS);
    if (aJsonVESIDs != null)
      for (final IJson aItem : aJsonVESIDs)
      {
        final IJsonObject aObj = aItem.getAsObject ();
        if (aObj != null)
        {
          final DVRCoordinate aVESID = DVRCoordinate.parseOrNull (aObj.getAsString (CPhormClient.JSON_VESID));
          final String sRepoID = aObj.getAsString (CPhormClient.JSON_REPOSITORY);
          if (aVESID != null && StringHelper.isNotEmpty (sRepoID))
            aVESIDs.put (aVESID, sRepoID);
        }
      }

    return new PhormRepositoryStatus (aResponse,
                                      aJson,
                                      aJson.getAsString (CPhormClient.JSON_CONFIG_FILE),
                                      aLoadDT,
                                      aRepositories,
                                      aVESIDs);
  }

  /**
   * Build a status from a <code>GET /api/admin/repositories</code> response. This is what the
   * client does internally.
   *
   * @param aResponse
   *        The HTTP response to convert. May not be <code>null</code>.
   * @return The converted status. Never <code>null</code>.
   * @throws PhormClientException
   *         If the response body is not a JSON object.
   */
  @NonNull
  public static PhormRepositoryStatus createFromResponse (@NonNull final PhormRawResponse aResponse) throws PhormClientException
  {
    ValueEnforcer.notNull (aResponse, "Response");
    return createFromJson (aResponse, aResponse.getBodyAsJsonObjectOrThrow ());
  }
}
