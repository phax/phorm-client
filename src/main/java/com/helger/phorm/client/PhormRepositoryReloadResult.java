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
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.diagnostics.error.IError;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.json.IJson;
import com.helger.json.IJsonArray;
import com.helger.json.IJsonObject;
import com.helger.phive.result.json.CPhiveJson;
import com.helger.phive.result.json.PhiveJsonHelper;

/**
 * The result of the admin API <code>POST /api/admin/repositories/reload</code>.
 * <p>
 * A failed reload is a regular result, not an error: {@link #isSuccess()} is then
 * <code>false</code>, {@link #getErrors()} holds the problems, and phorm keeps the previously
 * loaded VES active. phorm answers that case with HTTP 500. Only problems that prevented the reload
 * from being attempted at all are reported as {@link PhormClientException}.
 *
 * @author Philip Helger
 * @since 1.1.0
 */
@Immutable
public class PhormRepositoryReloadResult
{
  private final PhormRawResponse m_aRawResponse;
  private final IJsonObject m_aJson;
  private final ICommonsList <DVRCoordinate> m_aAdded;
  private final ICommonsList <DVRCoordinate> m_aRemoved;
  private final ErrorList m_aErrors;
  private final PhormRepositoryStatus m_aStatus;

  /**
   * Constructor
   *
   * @param aRawResponse
   *        The underlying HTTP response. May not be <code>null</code>.
   * @param aJson
   *        The parsed response body. May not be <code>null</code>.
   * @param aAdded
   *        The VESIDs that were added by the reload. May not be <code>null</code>.
   * @param aRemoved
   *        The VESIDs that were removed by the reload. May not be <code>null</code>.
   * @param aErrors
   *        The problems that occurred. May also contain warnings on success. May not be
   *        <code>null</code>.
   * @param aStatus
   *        The state of the external repositories after the reload. May be <code>null</code> - phorm
   *        reports it on success only.
   */
  public PhormRepositoryReloadResult (@NonNull final PhormRawResponse aRawResponse,
                                      @NonNull final IJsonObject aJson,
                                      @NonNull final Iterable <DVRCoordinate> aAdded,
                                      @NonNull final Iterable <DVRCoordinate> aRemoved,
                                      @NonNull final Iterable <? extends IError> aErrors,
                                      @Nullable final PhormRepositoryStatus aStatus)
  {
    ValueEnforcer.notNull (aRawResponse, "RawResponse");
    ValueEnforcer.notNull (aJson, "Json");
    ValueEnforcer.notNullNoNullValue (aAdded, "Added");
    ValueEnforcer.notNullNoNullValue (aRemoved, "Removed");
    ValueEnforcer.notNullNoNullValue (aErrors, "Errors");
    m_aRawResponse = aRawResponse;
    m_aJson = aJson;
    m_aAdded = new CommonsArrayList <> (aAdded);
    m_aRemoved = new CommonsArrayList <> (aRemoved);
    m_aErrors = new ErrorList (aErrors);
    m_aStatus = aStatus;
  }

  @NonNull
  private static ICommonsList <DVRCoordinate> _getVESIDs (@Nullable final IJsonArray aArray)
  {
    final ICommonsList <DVRCoordinate> ret = new CommonsArrayList <> ();
    if (aArray != null)
      for (final IJson aItem : aArray)
        if (aItem.isValue ())
        {
          final DVRCoordinate aVESID = DVRCoordinate.parseOrNull (aItem.getAsValue ().getAsString ());
          if (aVESID != null)
            ret.add (aVESID);
        }
    return ret;
  }

  /**
   * @return The unparsed HTTP response this result was built from. Never <code>null</code>. Use it
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
   * @return <code>true</code> if the reload succeeded. On <code>false</code> phorm keeps the
   *         previously loaded VES active and {@link #getErrors()} explains why.
   */
  public boolean isSuccess ()
  {
    return m_aJson.getAsBoolean (CPhiveJson.JSON_SUCCESS, false);
  }

  /**
   * @return The VESIDs that are available after the reload but were not before, sorted. Never
   *         <code>null</code>; empty if nothing was added or the reload failed.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <DVRCoordinate> getAllAddedVESIDs ()
  {
    return m_aAdded.getClone ();
  }

  /**
   * @return The VESIDs that were available before the reload but are not any more, sorted. Never
   *         <code>null</code>; empty if nothing was removed or the reload failed.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <DVRCoordinate> getAllRemovedVESIDs ()
  {
    return m_aRemoved.getClone ();
  }

  /**
   * @return All problems reported by phorm. On success it may still contain warnings. Never
   *         <code>null</code> but maybe empty.
   */
  @NonNull
  @ReturnsMutableCopy
  public ErrorList getErrors ()
  {
    return m_aErrors.getClone ();
  }

  /**
   * @return The state of the external repositories after the reload. May be <code>null</code> -
   *         phorm reports it on success only.
   */
  @Nullable
  public PhormRepositoryStatus getStatus ()
  {
    return m_aStatus;
  }

  /**
   * @return The server side exception, if phorm failed while producing the result. May be
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
                                       .append ("Added", m_aAdded)
                                       .append ("Removed", m_aRemoved)
                                       .append ("Errors", m_aErrors)
                                       .appendIfNotNull ("Status", m_aStatus)
                                       .getToString ();
  }

  /**
   * Build a result from a <code>POST /api/admin/repositories/reload</code> response. This is what
   * the client does internally.
   *
   * @param aResponse
   *        The HTTP response to convert. May not be <code>null</code>.
   * @return The converted result. Never <code>null</code>.
   * @throws PhormClientException
   *         If the response body is not a JSON object.
   */
  @NonNull
  public static PhormRepositoryReloadResult createFromResponse (@NonNull final PhormRawResponse aResponse) throws PhormClientException
  {
    ValueEnforcer.notNull (aResponse, "Response");

    final IJsonObject aJson = aResponse.getBodyAsJsonObjectOrThrow ();
    final boolean bSuccess = aJson.getAsBoolean (CPhiveJson.JSON_SUCCESS, false);

    final ErrorList aErrors = new ErrorList ();
    final IJsonArray aJsonErrors = aJson.getAsArray (CPhormClient.JSON_ERRORS);
    if (aJsonErrors != null)
      for (final IJson aItem : aJsonErrors)
      {
        final IJsonObject aObj = aItem.getAsObject ();
        if (aObj != null)
          aErrors.add (PhiveJsonHelper.getAsIError (aObj));
      }

    // The repository state is only reported on success
    final PhormRepositoryStatus aStatus = bSuccess ? PhormRepositoryStatus.createFromJson (aResponse, aJson) : null;

    return new PhormRepositoryReloadResult (aResponse,
                                            aJson,
                                            _getVESIDs (aJson.getAsArray (CPhormClient.JSON_ADDED)),
                                            _getVESIDs (aJson.getAsArray (CPhormClient.JSON_REMOVED)),
                                            aErrors,
                                            aStatus);
  }
}
