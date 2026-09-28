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

import java.time.ZonedDateTime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.string.StringHelper;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.ddd.DocumentDetails;
import com.helger.ddd.DocumentDetailsJsonHelper;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.json.IJsonObject;
import com.helger.phive.api.validity.EExtendedValidity;
import com.helger.peppolid.factory.IIdentifierFactory;
import com.helger.phive.api.result.ValidationResultList;
import com.helger.phive.result.json.CPhiveJson;
import com.helger.phive.result.json.PhiveJsonHelper;

/**
 * The result of a phorm validation call - <code>/api/validate/{vesid}</code>,
 * <code>/api/dd_and_validate</code> or <code>/api/hybrid_validate</code>.
 * <p>
 * An <em>invalid document</em> is a regular result, not an error: {@link #isSuccess()} is then
 * <code>false</code> and {@link #getValidationResultList()} holds the findings. Only problems that
 * prevented validation from happening at all are reported as {@link PhormClientException}.
 *
 * @author Philip Helger
 */
@Immutable
public class PhormValidationResult
{
  private final PhormRawResponse m_aRawResponse;
  private final IJsonObject m_aJson;
  private final DocumentDetails m_aDocumentDetails;
  private final ValidationResultList m_aValidationResultList;
  private final EPhormHybridCountry m_eCountry;

  /**
   * Constructor
   *
   * @param aRawResponse
   *        The underlying HTTP response. May not be <code>null</code>.
   * @param aJson
   *        The parsed response body. May not be <code>null</code>.
   * @param aDocumentDetails
   *        The determined document details. May be <code>null</code> - only the determining APIs
   *        deliver them.
   * @param aValidationResultList
   *        The validation results converted to the phive model. May be <code>null</code> if the
   *        conversion failed, e.g. because the server reported an exception instead of a result.
   * @param eCountry
   *        The country whose rules were applied. May be <code>null</code> - only
   *        <code>/api/hybrid_validate</code> reports one.
   */
  public PhormValidationResult (@NonNull final PhormRawResponse aRawResponse,
                                @NonNull final IJsonObject aJson,
                                @Nullable final DocumentDetails aDocumentDetails,
                                @Nullable final ValidationResultList aValidationResultList,
                                @Nullable final EPhormHybridCountry eCountry)
  {
    ValueEnforcer.notNull (aRawResponse, "RawResponse");
    ValueEnforcer.notNull (aJson, "Json");
    m_aRawResponse = aRawResponse;
    m_aJson = aJson;
    m_aDocumentDetails = aDocumentDetails;
    m_aValidationResultList = aValidationResultList;
    m_eCountry = eCountry;
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
   * @return <code>true</code> if the document is considered valid by the applied rule sets.
   */
  public boolean isSuccess ()
  {
    return m_aJson.getAsBoolean (CPhiveJson.JSON_SUCCESS, false);
  }

  /**
   * @return <code>true</code> if validation was interrupted before all layers were evaluated.
   */
  public boolean isInterrupted ()
  {
    return m_aJson.getAsBoolean (CPhiveJson.JSON_INTERRUPTED, false);
  }

  /**
   * @return The most severe error level as reported by phorm, e.g. <code>ERROR</code> or
   *         <code>SUCCESS</code>. May be <code>null</code>.
   */
  @Nullable
  public String getMostSevereErrorLevel ()
  {
    return m_aJson.getAsString (CPhiveJson.JSON_MOST_SEVERE_ERROR_LEVEL);
  }

  /**
   * @return <code>true</code> if document details were determined, which is the case for
   *         <code>/api/dd_and_validate</code> and <code>/api/hybrid_validate</code>.
   */
  public boolean hasDocumentDetails ()
  {
    return m_aDocumentDetails != null;
  }

  /**
   * @return The determined document details - sender, receiver, document type, VESID and so on. May
   *         be <code>null</code>.
   */
  @Nullable
  public DocumentDetails getDocumentDetails ()
  {
    return m_aDocumentDetails;
  }

  /**
   * @return The validation results as the regular phive model, one entry per validation layer. May
   *         be <code>null</code> if the response carried no convertible result.
   */
  @Nullable
  public ValidationResultList getValidationResultList ()
  {
    return m_aValidationResultList;
  }

  /**
   * @return The overall validity. Never <code>null</code>; {@link EExtendedValidity#UNCLEAR} if no
   *         validation result list could be extracted.
   */
  @NonNull
  public EExtendedValidity getOverallValidity ()
  {
    return m_aValidationResultList == null ? EExtendedValidity.UNCLEAR : m_aValidationResultList
                                                                                               .getOverallValidity ();
  }

  /**
   * @return All findings of error level <code>ERROR</code> and above, flattened across all
   *         validation layers. Never <code>null</code> but maybe empty. Use
   *         {@link #getAllFailures()} to include warnings.
   */
  @NonNull
  @ReturnsMutableCopy
  public ErrorList getAllErrors ()
  {
    return m_aValidationResultList == null ? new ErrorList () : m_aValidationResultList.getAllErrors ();
  }

  /**
   * @return All findings of error level <code>WARN</code> and above, flattened across all validation
   *         layers. Never <code>null</code> but maybe empty. This is the superset of
   *         {@link #getAllErrors()}.
   */
  @NonNull
  @ReturnsMutableCopy
  public ErrorList getAllFailures ()
  {
    return m_aValidationResultList == null ? new ErrorList () : m_aValidationResultList.getAllFailures ();
  }

  /**
   * @return The country whose rules were applied. May be <code>null</code> - only
   *         <code>/api/hybrid_validate</code> reports one.
   */
  @Nullable
  public EPhormHybridCountry getCountry ()
  {
    return m_eCountry;
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

  /**
   * @return The date and time the API was invoked, as delivered by the server. May be
   *         <code>null</code> if it is absent or unparsable.
   */
  @Nullable
  public ZonedDateTime getInvocationDateTime ()
  {
    final String sDT = m_aJson.getAsString (CPhormClient.JSON_INVOCATION_DATETIME);
    if (StringHelper.isEmpty (sDT))
      return null;

    try
    {
      return ZonedDateTime.parse (sDT);
    }
    catch (final RuntimeException ex)
    {
      return null;
    }
  }

  /**
   * @return The overall duration of the API invocation in milliseconds, or <code>-1</code> if it is
   *         absent.
   */
  public long getInvocationDurationMillis ()
  {
    return m_aJson.getAsLong (CPhormClient.JSON_INVOCATION_DURATION_MILLIS, -1);
  }

  /**
   * Build a result from a phorm JSON response. This is what the client does internally for every
   * validating API.
   *
   * @param aResponse
   *        The HTTP response to convert. May not be <code>null</code>.
   * @param aIdentifierFactory
   *        The identifier factory used to parse the identifiers of the document details, if the
   *        response carries any. May not be <code>null</code>.
   * @return The converted result. Never <code>null</code>.
   * @throws PhormClientException
   *         If the response body is not a JSON object.
   */
  @NonNull
  public static PhormValidationResult createFromResponse (@NonNull final PhormRawResponse aResponse,
                                                          @NonNull final IIdentifierFactory aIdentifierFactory) throws PhormClientException
  {
    ValueEnforcer.notNull (aResponse, "Response");
    ValueEnforcer.notNull (aIdentifierFactory, "IdentifierFactory");

    final IJsonObject aJson = aResponse.getBodyAsJsonObjectOrThrow ();

    // Only the determining APIs deliver document details
    final DocumentDetails aDD = DocumentDetailsJsonHelper.getAsDocumentDetails (aJson.getAsObject (CPhormClient.JSON_DOCUMENT_DETAILS),
                                                                               aIdentifierFactory);
    // Only the hybrid API reports a country
    final EPhormHybridCountry eCountry = EPhormHybridCountry.getFromIDOrNull (aJson.getAsString (CPhormClient.JSON_COUNTRY));

    // May be null, e.g. if the server reported an exception instead of a result
    final ValidationResultList aVRL = PhiveJsonHelper.getAsValidationResultList (aJson);

    return new PhormValidationResult (aResponse, aJson, aDD, aVRL, eCountry);
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("RawResponse", m_aRawResponse)
                                       .appendIfNotNull ("DocumentDetails", m_aDocumentDetails)
                                       .appendIfNotNull ("ValidationResultList", m_aValidationResultList)
                                       .appendIfNotNull ("Country", m_eCountry)
                                       .getToString ();
  }
}
