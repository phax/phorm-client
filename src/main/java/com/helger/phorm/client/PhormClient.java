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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityTemplate;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.Nonempty;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.annotation.style.VisibleForTesting;
import com.helger.base.builder.IBuilder;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.iface.IHasInputStream;
import com.helger.base.io.stream.StreamHelper;
import com.helger.base.string.StringHelper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.ddd.DocumentDetails;
import com.helger.ddd.DocumentDetailsJsonHelper;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.http.CHttp;
import com.helger.http.CHttpHeader;
import com.helger.httpclient.HttpClientHelper;
import com.helger.httpclient.HttpClientManager;
import com.helger.httpclient.HttpClientSettings;
import com.helger.json.IJson;
import com.helger.json.IJsonArray;
import com.helger.mime.CMimeType;
import com.helger.mime.IMimeType;
import com.helger.peppolid.factory.IIdentifierFactory;
import com.helger.peppolid.factory.SimpleIdentifierFactory;
import com.helger.xml.microdom.IMicroDocument;

/**
 * Client for the phorm REST API. It covers all five APIs phorm offers:
 * <ul>
 * <li><code>GET /api/get/vesids</code> - {@link #getAllVESIDs(boolean)}</li>
 * <li><code>POST /api/validate/{vesid}</code> - {@link #validate(DVRCoordinate, byte[])}</li>
 * <li><code>POST /api/determinedoctype</code> - {@link #determineDocumentDetails(byte[])}</li>
 * <li><code>POST /api/dd_and_validate</code> - {@link #determineAndValidate(byte[])}</li>
 * <li><code>POST /api/hybrid_validate</code> -
 * {@link #hybridValidate(byte[], EPhormHybridCountry)}</li>
 * </ul>
 * <p>
 * Internally JSON is always requested, because it is the most compact of the three representations
 * phorm offers and the only one that can be converted into the phive and ddd object models. The
 * <code>...AsXML</code> and <code>...AsHtml</code> variants exist for callers that want to store or
 * display the server rendered representation instead.
 * <p>
 * Every method that returns a typed result takes the payload as a <code>byte []</code> or as an
 * {@link IHasInputStream}. The latter is streamed to phorm, so a large document is never held in
 * memory as a whole - use {@link com.helger.base.io.stream.HasInputStream} to build one from an
 * arbitrary {@link InputStream} supplier. The <code>...AsXML</code> and <code>...AsHtml</code>
 * variants take a <code>byte []</code> only.
 * <p>
 * Create one with the {@link #builder()} and reuse it - it holds an HTTP connection pool:
 *
 * <pre>
 * try (final PhormClient aClient = PhormClient.builder ()
 *                                             .baseURL ("http://localhost:8080")
 *                                             .token ("phorm-dev-token")
 *                                             .build ())
 * {
 *   final PhormValidationResult aResult = aClient.determineAndValidate (aXmlBytes);
 *   if (!aResult.isSuccess ())
 *     aResult.getAllErrors ().forEach (x -&gt; System.out.println (x.getAsString (Locale.US)));
 * }
 * </pre>
 *
 * @author Philip Helger
 */
public class PhormClient implements AutoCloseable
{
  private static final Logger LOGGER = LoggerFactory.getLogger (PhormClient.class);
  /**
   * The characters a country code passed to the hybrid validation API may consist of. The value is
   * deliberately <b>not</b> checked against {@link EPhormHybridCountry}, so that a country added to
   * the phorm side hybrid rules later needs no new release of this library - only the URL safety is
   * enforced here.
   */
  private static final Pattern COUNTRY_PATTERN = Pattern.compile ("[0-9A-Za-z_-]{1,20}");

  private final String m_sBaseURL;
  private final String m_sAPIPath;
  private final String m_sToken;
  private final IIdentifierFactory m_aIdentifierFactory;
  private final HttpClientManager m_aHCM;
  private final boolean m_bOwnsHCM;

  /**
   * Response handler that never fails on the status code. phorm answers an invalid document with
   * HTTP 400 and a regular result body, so the decision whether a status is an error can only be
   * made once the content type is known.
   *
   * @author Philip Helger
   */
  private static final class PhormResponseHandler implements HttpClientResponseHandler <PhormRawResponse>
  {
    static final PhormResponseHandler INSTANCE = new PhormResponseHandler ();

    @NonNull
    public PhormRawResponse handleResponse (@NonNull final ClassicHttpResponse aHttpResponse) throws IOException
    {
      final int nStatusCode = aHttpResponse.getCode ();
      final HttpEntity aEntity = aHttpResponse.getEntity ();
      if (aEntity == null)
        return PhormRawResponse.createNoContent (nStatusCode);

      Charset aCharset = StandardCharsets.UTF_8;
      try
      {
        final ContentType aContentType = HttpClientHelper.getContentTypeOrDefault (aEntity);
        aCharset = HttpClientHelper.getCharset (aContentType, StandardCharsets.UTF_8);
      }
      catch (final RuntimeException ex)
      {
        // Unparsable or unsupported charset - stick with UTF-8
        LOGGER.warn ("Failed to determine the charset of the phorm response - using UTF-8 instead. Technical details: " +
                     ex.getMessage () +
                     " -- " +
                     ex.getClass ().getName ());
      }

      final byte [] aBody = EntityUtils.toByteArray (aEntity);
      return new PhormRawResponse (nStatusCode, aEntity.getContentType (), aCharset, aBody);
    }
  }

  /**
   * Constructor for a client with default HTTP settings, the default API path and the
   * {@link SimpleIdentifierFactory}.
   *
   * @param sBaseURL
   *        The base URL of the phorm instance, e.g. <code>http://localhost:8080</code>. A trailing
   *        slash is removed. May neither be <code>null</code> nor empty.
   * @param sToken
   *        The value of the <code>X-Token</code> HTTP header, matching the server side
   *        <code>phorm.api.requiredtoken</code>. May be <code>null</code> only if the target
   *        instance requires no token.
   */
  public PhormClient (@NonNull @Nonempty final String sBaseURL, @Nullable final String sToken)
  {
    this (sBaseURL, CPhormClient.DEFAULT_API_PATH, sToken, SimpleIdentifierFactory.INSTANCE, null, null);
  }

  /**
   * Full constructor. Prefer {@link #builder()}.
   *
   * @param sBaseURL
   *        The base URL of the phorm instance. May neither be <code>null</code> nor empty.
   * @param sAPIPath
   *        The path below which the APIs reside, usually {@link CPhormClient#DEFAULT_API_PATH}. May
   *        neither be <code>null</code> nor empty.
   * @param sToken
   *        The value of the <code>X-Token</code> HTTP header. May be <code>null</code>.
   * @param aIdentifierFactory
   *        The identifier factory used to parse the participant, document type and process
   *        identifiers of the determined document details. May not be <code>null</code>.
   * @param aHttpClientSettings
   *        The HTTP settings to create an own connection pool from. Ignored if
   *        <code>aHttpClientManager</code> is provided. May be <code>null</code>.
   * @param aHttpClientManager
   *        An externally managed connection pool. If provided, it is <b>not</b> closed by
   *        {@link #close()}. May be <code>null</code>.
   */
  protected PhormClient (@NonNull @Nonempty final String sBaseURL,
                         @NonNull @Nonempty final String sAPIPath,
                         @Nullable final String sToken,
                         @NonNull final IIdentifierFactory aIdentifierFactory,
                         @Nullable final HttpClientSettings aHttpClientSettings,
                         @Nullable final HttpClientManager aHttpClientManager)
  {
    ValueEnforcer.notEmpty (sBaseURL, "BaseURL");
    ValueEnforcer.notEmpty (sAPIPath, "APIPath");
    ValueEnforcer.notNull (aIdentifierFactory, "IdentifierFactory");

    m_sBaseURL = StringHelper.trimEnd (sBaseURL, "/");
    m_sAPIPath = sAPIPath;
    m_sToken = sToken;
    m_aIdentifierFactory = aIdentifierFactory;
    if (aHttpClientManager != null)
    {
      m_aHCM = aHttpClientManager;
      m_bOwnsHCM = false;
      if (aHttpClientSettings != null)
        LOGGER.warn ("Passing HttpClientManager and HttpClientSettings - only the HttpClientManager is used");
    }
    else
    {
      m_aHCM = HttpClientManager.create (aHttpClientSettings != null ? aHttpClientSettings : new HttpClientSettings ());
      m_bOwnsHCM = true;
    }
  }

  @NonNull
  @Nonempty
  private String _buildURL (@NonNull @Nonempty final String sPath, @Nullable final String sQuery)
  {
    final String sURL = m_sBaseURL + m_sAPIPath + sPath;
    return StringHelper.isEmpty (sQuery) ? sURL : sURL + "?" + sQuery;
  }

  @NonNull
  private PhormRawResponse _execute (@NonNull final HttpUriRequest aRequest,
                                     @NonNull final EPhormResponseFormat eFormat) throws PhormClientException
  {
    aRequest.addHeader (CHttpHeader.ACCEPT, eFormat.getMimeType ().getAsString ());
    if (StringHelper.isNotEmpty (m_sToken))
      aRequest.addHeader (CPhormClient.HEADER_X_TOKEN, m_sToken);

    try
    {
      return m_aHCM.execute (aRequest, PhormResponseHandler.INSTANCE);
    }
    catch (final IOException ex)
    {
      throw new PhormClientException (EPhormErrorType.SERVICE_UNAVAILABLE,
                                      "Failed to invoke the phorm API at '" + m_sBaseURL + "'",
                                      ex);
    }
  }

  @NonNull
  private PhormRawResponse _get (@NonNull @Nonempty final String sPath,
                                 @Nullable final String sQuery,
                                 @NonNull final EPhormResponseFormat eFormat) throws PhormClientException
  {
    final String sURL = _buildURL (sPath, sQuery);
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Invoking phorm API 'GET " + sURL + "'");
    return _execute (new HttpGet (sURL), eFormat);
  }

  @NonNull
  private PhormRawResponse _post (@NonNull @Nonempty final String sPath,
                                  @Nullable final String sQuery,
                                  @NonNull final HttpEntity aEntity,
                                  @NonNull final EPhormResponseFormat eFormat) throws PhormClientException
  {
    final String sURL = _buildURL (sPath, sQuery);
    if (LOGGER.isDebugEnabled ())
    {
      final long nContentLength = aEntity.getContentLength ();
      LOGGER.debug ("Invoking phorm API 'POST " +
                    sURL +
                    "' with " +
                    (nContentLength < 0 ? "a streamed payload" : nContentLength + " bytes"));
    }

    final HttpPost aPost = new HttpPost (sURL);
    aPost.setEntity (aEntity);
    return _execute (aPost, eFormat);
  }

  /**
   * Create the HTTP entity for a payload that is already in memory.
   *
   * @param aPayload
   *        The payload bytes. May not be <code>null</code>.
   * @param aContentType
   *        The content type to be sent. May not be <code>null</code>.
   * @return Never <code>null</code>.
   */
  @NonNull
  private static HttpEntity _createEntity (final byte @NonNull [] aPayload, @NonNull final IMimeType aContentType)
  {
    ValueEnforcer.notNull (aPayload, "Payload");
    return new ByteArrayEntity (aPayload, ContentType.create (aContentType.getAsString ()));
  }

  /**
   * Create the HTTP entity for a payload that is delivered as a stream, so that a large document
   * never has to be held in memory as a whole. A payload that can be read more than once - see
   * {@link IHasInputStream#isReadMultiple()} - yields a repeatable entity, so that the HTTP client
   * may replay the request; a payload that can be read only once yields a plain streaming entity.
   *
   * @param aPayload
   *        The payload provider. May not be <code>null</code>.
   * @param aContentType
   *        The content type to be sent. May not be <code>null</code>.
   * @return Never <code>null</code>.
   * @throws PhormClientException
   *         If the {@link InputStream} of a read-once payload cannot be opened.
   */
  @VisibleForTesting
  @NonNull
  static HttpEntity createStreamingEntity (@NonNull final IHasInputStream aPayload,
                                           @NonNull final IMimeType aContentType) throws PhormClientException
  {
    ValueEnforcer.notNull (aPayload, "Payload");
    final ContentType aCT = ContentType.create (aContentType.getAsString ());

    // A payload that can be read more than once delivers a new InputStream for every write, so
    // the entity may be replayed by the HTTP client
    if (aPayload.isReadMultiple ())
      return new EntityTemplate (aCT, aOS -> {
        try (final InputStream aIS = aPayload.getInputStream ())
        {
          if (aIS == null)
            throw new IOException ("Failed to open the InputStream of the payload " + aPayload);
          aIS.transferTo (aOS);
        }
      });

    // Only a single read is possible, so the stream must be opened up front
    final InputStream aIS = aPayload.getInputStream ();
    if (aIS == null)
      throw new PhormClientException (EPhormErrorType.REQUEST_ERROR,
                                      "Failed to open the InputStream of the payload " + aPayload);
    return new InputStreamEntity (aIS, aCT);
  }

  /**
   * Classify an HTTP status code that did not deliver a usable result. A 4xx means phorm understood
   * the request and refused it - the payload, the VESID or the token is at fault and repeating the
   * identical call cannot help. A 5xx means phorm reported itself as broken. Anything else is an
   * answer this library does not know what to do with.
   *
   * @param nStatusCode
   *        The HTTP status code as received.
   * @return Never <code>null</code>.
   */
  @NonNull
  private static EPhormErrorType _getErrorType (final int nStatusCode)
  {
    if (nStatusCode >= CHttp.HTTP_INTERNAL_SERVER_ERROR)
      return EPhormErrorType.SERVICE_UNAVAILABLE;
    if (nStatusCode >= CHttp.HTTP_BAD_REQUEST)
      return EPhormErrorType.REQUEST_ERROR;
    return EPhormErrorType.RESPONSE_ERROR;
  }

  private static void _checkForError (@NonNull final PhormRawResponse aResponse,
                                      @Nullable final EPhormResponseFormat eExpectedFormat) throws PhormClientException
  {
    if (aResponse.isStatusSuccess ())
      return;

    // A content wise invalid document is answered with HTTP 400 and a regular result body, if the
    // server side "phorm.api.response.onfailure.http400" is enabled. That is not an error.
    if (aResponse.getStatusCode () == CHttp.HTTP_BAD_REQUEST &&
        eExpectedFormat != null &&
        aResponse.hasMimeType (eExpectedFormat.getMimeType ()))
      return;

    if (aResponse.getStatusCode () == CHttp.HTTP_FORBIDDEN)
      throw new PhormClientException (EPhormErrorType.REQUEST_ERROR,
                                      "phorm rejected the value of the '" +
                                                                     CPhormClient.HEADER_X_TOKEN +
                                                                     "' HTTP header",
                                      aResponse);

    final String sBody = aResponse.getBodyAsString ();
    final String sDetails;
    if (sBody == null)
      sDetails = "<no response body>";
    else
      sDetails = sBody.length () > 500 ? sBody.substring (0, 500) + "..." : sBody;
    throw new PhormClientException (_getErrorType (aResponse.getStatusCode ()),
                                    "phorm returned an unexpected response: " + sDetails,
                                    aResponse);
  }

  @NonNull
  private PhormValidationResult _postValidation (@NonNull @Nonempty final String sPath,
                                                 @Nullable final String sQuery,
                                                 @NonNull final HttpEntity aEntity) throws PhormClientException
  {
    final PhormRawResponse aResponse = _post (sPath, sQuery, aEntity, EPhormResponseFormat.JSON);
    _checkForError (aResponse, EPhormResponseFormat.JSON);
    return PhormValidationResult.createFromResponse (aResponse, m_aIdentifierFactory);
  }

  @NonNull
  private IMicroDocument _postAsXML (@NonNull @Nonempty final String sPath,
                                     @Nullable final String sQuery,
                                     @NonNull final HttpEntity aEntity) throws PhormClientException
  {
    final PhormRawResponse aResponse = _post (sPath, sQuery, aEntity, EPhormResponseFormat.XML);
    _checkForError (aResponse, EPhormResponseFormat.XML);

    final IMicroDocument ret = aResponse.getBodyAsMicroDocument ();
    if (ret == null)
      throw new PhormClientException (EPhormErrorType.RESPONSE_ERROR,
                                      "The phorm response could not be parsed as XML",
                                      aResponse);
    return ret;
  }

  @NonNull
  private String _postAsHtml (@NonNull @Nonempty final String sPath,
                              @Nullable final String sQuery,
                              @NonNull final HttpEntity aEntity) throws PhormClientException
  {
    final PhormRawResponse aResponse = _post (sPath, sQuery, aEntity, EPhormResponseFormat.HTML);
    _checkForError (aResponse, EPhormResponseFormat.HTML);

    final String ret = aResponse.getBodyAsString ();
    if (ret == null)
      throw new PhormClientException (EPhormErrorType.RESPONSE_ERROR,
                                      "The phorm response carries no HTML body",
                                      aResponse);
    return ret;
  }

  /**
   * Invoke the document type determination API with an already created request entity.
   *
   * @param aEntity
   *        The request entity. May not be <code>null</code>.
   * @return The determined document details, or <code>null</code> if phorm answered HTTP 204.
   * @throws PhormClientException
   *         On a transport problem, a rejected token or an unparsable payload.
   */
  @Nullable
  private DocumentDetails _determineDocumentDetails (@NonNull final HttpEntity aEntity) throws PhormClientException
  {
    final PhormRawResponse aResponse = _post (CPhormClient.PATH_DETERMINE_DOCTYPE,
                                              null,
                                              aEntity,
                                              EPhormResponseFormat.JSON);
    _checkForError (aResponse, null);
    if (aResponse.getStatusCode () == CHttp.HTTP_NO_CONTENT)
      return null;

    return DocumentDetailsJsonHelper.getAsDocumentDetails (aResponse.getBodyAsJsonObjectOrThrow (),
                                                           m_aIdentifierFactory);
  }

  /**
   * Build the <code>country</code> query parameter of the hybrid validation API. The value is
   * deliberately <b>not</b> checked against {@link EPhormHybridCountry}: a country that phorm
   * learns about after this library was released must be passable without a change in here, and
   * phorm falls back to its default for a country it does not know. Only the URL safety is
   * enforced.
   *
   * @param sCountryID
   *        The ID of the country. May be <code>null</code> or empty for none.
   * @return <code>null</code> if no country parameter is to be sent.
   * @throws PhormClientException
   *         If the country code is not a plain code.
   */
  @VisibleForTesting
  @Nullable
  static String getCountryQuery (@Nullable final String sCountryID) throws PhormClientException
  {
    if (StringHelper.isEmpty (sCountryID))
      return null;

    // The value ends up in the URL as-is, so anything that is not a plain code is refused
    if (!COUNTRY_PATTERN.matcher (sCountryID).matches ())
      throw new PhormClientException (EPhormErrorType.REQUEST_ERROR,
                                      "The country code '" + sCountryID + "' contains unsupported characters");
    return CPhormClient.QUERY_PARAM_COUNTRY + "=" + sCountryID;
  }

  @Nullable
  private static String _getCountryQuery (@Nullable final EPhormHybridCountry eCountry) throws PhormClientException
  {
    return eCountry == null ? null : getCountryQuery (eCountry.getID ());
  }

  /**
   * @return The base URL of the phorm instance, without a trailing slash. Never <code>null</code>.
   */
  @NonNull
  @Nonempty
  public final String getBaseURL ()
  {
    return m_sBaseURL;
  }

  /**
   * @return The path below which the APIs reside. Never <code>null</code>.
   */
  @NonNull
  @Nonempty
  public final String getAPIPath ()
  {
    return m_sAPIPath;
  }

  /**
   * @return The identifier factory used to parse the identifiers of the determined document
   *         details. Never <code>null</code>.
   */
  @NonNull
  public final IIdentifierFactory getIdentifierFactory ()
  {
    return m_aIdentifierFactory;
  }

  /**
   * Close the underlying HTTP connection pool, unless it was provided externally.
   */
  public void close ()
  {
    if (m_bOwnsHCM)
      StreamHelper.close (m_aHCM);
  }

  /**
   * Get all non-deprecated validation rule sets registered in the target phorm instance. Calls
   * <code>GET /api/get/vesids</code>.
   *
   * @return The list of rule sets, never <code>null</code>.
   * @throws PhormClientException
   *         On a transport problem or an unexpected response.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <PhormVESID> getAllVESIDs () throws PhormClientException
  {
    return getAllVESIDs (false);
  }

  /**
   * Get all validation rule sets registered in the target phorm instance. Calls
   * <code>GET /api/get/vesids</code>.
   *
   * @param bIncludeDeprecated
   *        <code>true</code> to also return deprecated rule sets.
   * @return The list of rule sets, never <code>null</code>.
   * @throws PhormClientException
   *         On a transport problem or an unexpected response.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <PhormVESID> getAllVESIDs (final boolean bIncludeDeprecated) throws PhormClientException
  {
    final String sQuery = bIncludeDeprecated ? CPhormClient.QUERY_PARAM_INCLUDE_DEPRECATED : null;
    final PhormRawResponse aResponse = _get (CPhormClient.PATH_GET_VESIDS, sQuery, EPhormResponseFormat.JSON);
    _checkForError (aResponse, EPhormResponseFormat.JSON);

    final ICommonsList <PhormVESID> ret = new CommonsArrayList <> ();
    final IJsonArray aArray = aResponse.getBodyAsJsonObjectOrThrow ().getAsArray (CPhormClient.JSON_VESIDS);
    if (aArray != null)
      for (final IJson aItem : aArray)
      {
        final PhormVESID aVESID = PhormVESID.createFromJson (aItem.getAsObject ());
        if (aVESID != null)
          ret.add (aVESID);
      }
    return ret;
  }

  /**
   * Validate an XML document against a specific validation rule set. Calls
   * <code>POST /api/validate/{vesid}</code>.
   *
   * @param aVESID
   *        The rule set to validate against. May not be <code>null</code>.
   * @param aPayload
   *        The XML document to be validated. May not be <code>null</code>.
   * @return The validation result. An invalid document is a regular result, not an exception.
   * @throws PhormClientException
   *         On a transport problem, a rejected token, an unparsable payload or an unresolvable
   *         VESID.
   */
  @NonNull
  public PhormValidationResult validate (@NonNull final DVRCoordinate aVESID,
                                         final byte @NonNull [] aPayload) throws PhormClientException
  {
    ValueEnforcer.notNull (aVESID, "VESID");
    return _postValidation (CPhormClient.PATH_VALIDATE + aVESID.getAsSingleID (),
                            null,
                            _createEntity (aPayload, CMimeType.APPLICATION_XML));
  }

  /**
   * Validate an XML document against a specific validation rule set. Calls
   * <code>POST /api/validate/{vesid}</code>.
   *
   * @param sVESID
   *        The rule set to validate against, in its single string form, e.g.
   *        <code>eu.peppol.bis3:invoice:latest</code>. May neither be <code>null</code> nor empty.
   * @param aPayload
   *        The XML document to be validated. May not be <code>null</code>.
   * @return The validation result. An invalid document is a regular result, not an exception.
   * @throws PhormClientException
   *         If the VESID cannot be parsed, or on any of the problems listed at
   *         {@link #validate(DVRCoordinate, byte[])}.
   */
  @NonNull
  public PhormValidationResult validate (@NonNull @Nonempty final String sVESID,
                                         final byte @NonNull [] aPayload) throws PhormClientException
  {
    final DVRCoordinate aVESID = DVRCoordinate.parseOrNull (sVESID);
    if (aVESID == null)
      throw new PhormClientException (EPhormErrorType.REQUEST_ERROR, "The VESID '" + sVESID + "' could not be parsed");
    return validate (aVESID, aPayload);
  }

  /**
   * Validate an XML document against a specific validation rule set.
   *
   * @param aVESID
   *        The rule set to validate against. May not be <code>null</code>.
   * @param aPayload
   *        The provider of the XML document. It is streamed to phorm, so a large document is never
   *        held in memory as a whole. May not be <code>null</code>.
   * @return The validation result. An invalid document is a regular result, not an exception.
   * @throws PhormClientException
   *         If the payload cannot be opened, or on any of the problems listed at
   *         {@link #validate(DVRCoordinate, byte[])}.
   */
  @NonNull
  public PhormValidationResult validate (@NonNull final DVRCoordinate aVESID,
                                         @NonNull final IHasInputStream aPayload) throws PhormClientException
  {
    ValueEnforcer.notNull (aVESID, "VESID");
    return _postValidation (CPhormClient.PATH_VALIDATE + aVESID.getAsSingleID (),
                            null,
                            createStreamingEntity (aPayload, CMimeType.APPLICATION_XML));
  }

  /**
   * Validate an XML document and get the server rendered XML representation of the result. Calls
   * <code>POST /api/validate/{vesid}</code> with <code>Accept: application/xml</code>.
   *
   * @param aVESID
   *        The rule set to validate against. May not be <code>null</code>.
   * @param aPayload
   *        The XML document to be validated. May not be <code>null</code>.
   * @return The <code>validationResults</code> XML document. Never <code>null</code>.
   * @throws PhormClientException
   *         On any of the problems listed at {@link #validate(DVRCoordinate, byte[])}.
   */
  @NonNull
  public IMicroDocument validateAsXML (@NonNull final DVRCoordinate aVESID,
                                       final byte @NonNull [] aPayload) throws PhormClientException
  {
    ValueEnforcer.notNull (aVESID, "VESID");
    return _postAsXML (CPhormClient.PATH_VALIDATE + aVESID.getAsSingleID (),
                       null,
                       _createEntity (aPayload, CMimeType.APPLICATION_XML));
  }

  /**
   * Validate an XML document and get the server rendered HTML report. Calls
   * <code>POST /api/validate/{vesid}</code> with <code>Accept: text/html</code>.
   *
   * @param aVESID
   *        The rule set to validate against. May not be <code>null</code>.
   * @param aPayload
   *        The XML document to be validated. May not be <code>null</code>.
   * @return The ready to render HTML report. Never <code>null</code>.
   * @throws PhormClientException
   *         On any of the problems listed at {@link #validate(DVRCoordinate, byte[])}.
   */
  @NonNull
  public String validateAsHtml (@NonNull final DVRCoordinate aVESID,
                                final byte @NonNull [] aPayload) throws PhormClientException
  {
    ValueEnforcer.notNull (aVESID, "VESID");
    return _postAsHtml (CPhormClient.PATH_VALIDATE + aVESID.getAsSingleID (),
                        null,
                        _createEntity (aPayload, CMimeType.APPLICATION_XML));
  }

  /**
   * Determine the format and payload specifics of an XML document, without validating it. Calls
   * <code>POST /api/determinedoctype</code>.
   *
   * @param aPayload
   *        The XML document to be inspected. May not be <code>null</code>.
   * @return The determined document details, or <code>null</code> if phorm could not determine them
   *         - it answers that case with HTTP 204.
   * @throws PhormClientException
   *         On a transport problem, a rejected token or an unparsable payload.
   */
  @Nullable
  public DocumentDetails determineDocumentDetails (final byte @NonNull [] aPayload) throws PhormClientException
  {
    return _determineDocumentDetails (_createEntity (aPayload, CMimeType.APPLICATION_XML));
  }

  /**
   * Determine the format and payload specifics of an XML document, without validating it.
   *
   * @param aPayload
   *        The provider of the XML document. It is streamed to phorm, so a large document is never
   *        held in memory as a whole. May not be <code>null</code>.
   * @return The determined document details, or <code>null</code> if phorm could not determine
   *         them.
   * @throws PhormClientException
   *         If the payload cannot be opened, or on any of the problems listed at
   *         {@link #determineDocumentDetails(byte[])}.
   */
  @Nullable
  public DocumentDetails determineDocumentDetails (@NonNull final IHasInputStream aPayload) throws PhormClientException
  {
    return _determineDocumentDetails (createStreamingEntity (aPayload, CMimeType.APPLICATION_XML));
  }

  /**
   * Determine the document details and get the server rendered XML representation. Calls
   * <code>POST /api/determinedoctype</code> with <code>Accept: application/xml</code>.
   *
   * @param aPayload
   *        The XML document to be inspected. May not be <code>null</code>.
   * @return The <code>documentDetails</code> XML document, or <code>null</code> if phorm could not
   *         determine them.
   * @throws PhormClientException
   *         On any of the problems listed at {@link #determineDocumentDetails(byte[])}.
   */
  @Nullable
  public IMicroDocument determineDocumentDetailsAsXML (final byte @NonNull [] aPayload) throws PhormClientException
  {
    final PhormRawResponse aResponse = _post (CPhormClient.PATH_DETERMINE_DOCTYPE,
                                              null,
                                              _createEntity (aPayload, CMimeType.APPLICATION_XML),
                                              EPhormResponseFormat.XML);
    _checkForError (aResponse, null);
    if (aResponse.getStatusCode () == CHttp.HTTP_NO_CONTENT)
      return null;

    final IMicroDocument ret = aResponse.getBodyAsMicroDocument ();
    if (ret == null)
      throw new PhormClientException (EPhormErrorType.RESPONSE_ERROR,
                                      "The phorm response could not be parsed as XML",
                                      aResponse);
    return ret;
  }

  /**
   * Determine the document type and validate against the matching rule set in one call, so the
   * caller does not need to know the VESID upfront. Calls <code>POST /api/dd_and_validate</code>.
   * <p>
   * An SBDH or XHE wrapped payload is unwrapped automatically, and a Peppol SBDH is validated as an
   * additional first layer of the result.
   *
   * @param aPayload
   *        The XML document to be validated. May not be <code>null</code>.
   * @return The validation result including the determined document details. An invalid document is
   *         a regular result, not an exception.
   * @throws PhormClientException
   *         On a transport problem, a rejected token, an unparsable payload or an undeterminable
   *         document type.
   */
  @NonNull
  public PhormValidationResult determineAndValidate (final byte @NonNull [] aPayload) throws PhormClientException
  {
    return _postValidation (CPhormClient.PATH_DD_AND_VALIDATE,
                            null,
                            _createEntity (aPayload, CMimeType.APPLICATION_XML));
  }

  /**
   * Determine the document type and validate against the matching rule set in one call.
   *
   * @param aPayload
   *        The provider of the XML document. It is streamed to phorm, so a large document is never
   *        held in memory as a whole. May not be <code>null</code>.
   * @return The validation result including the determined document details.
   * @throws PhormClientException
   *         If the payload cannot be opened, or on any of the problems listed at
   *         {@link #determineAndValidate(byte[])}.
   */
  @NonNull
  public PhormValidationResult determineAndValidate (@NonNull final IHasInputStream aPayload) throws PhormClientException
  {
    return _postValidation (CPhormClient.PATH_DD_AND_VALIDATE,
                            null,
                            createStreamingEntity (aPayload, CMimeType.APPLICATION_XML));
  }

  /**
   * Determine the document type, validate, and get the server rendered XML representation. Calls
   * <code>POST /api/dd_and_validate</code> with <code>Accept: application/xml</code>.
   *
   * @param aPayload
   *        The XML document to be validated. May not be <code>null</code>.
   * @return The <code>validationResults</code> XML document. Never <code>null</code>.
   * @throws PhormClientException
   *         On any of the problems listed at {@link #determineAndValidate(byte[])}.
   */
  @NonNull
  public IMicroDocument determineAndValidateAsXML (final byte @NonNull [] aPayload) throws PhormClientException
  {
    return _postAsXML (CPhormClient.PATH_DD_AND_VALIDATE, null, _createEntity (aPayload, CMimeType.APPLICATION_XML));
  }

  /**
   * Determine the document type, validate, and get the server rendered HTML report. Calls
   * <code>POST /api/dd_and_validate</code> with <code>Accept: text/html</code>.
   *
   * @param aPayload
   *        The XML document to be validated. May not be <code>null</code>.
   * @return The ready to render HTML report. Never <code>null</code>.
   * @throws PhormClientException
   *         On any of the problems listed at {@link #determineAndValidate(byte[])}.
   */
  @NonNull
  public String determineAndValidateAsHtml (final byte @NonNull [] aPayload) throws PhormClientException
  {
    return _postAsHtml (CPhormClient.PATH_DD_AND_VALIDATE, null, _createEntity (aPayload, CMimeType.APPLICATION_XML));
  }

  /**
   * Validate a ZUGFeRD / Factur-X hybrid PDF invoice with the phorm side default country rules.
   *
   * @param aPayload
   *        The PDF document to be validated. May not be <code>null</code>.
   * @return The validation result, carrying the PDF carrier layers first.
   * @throws PhormClientException
   *         On any of the problems listed at {@link #hybridValidate(byte[], EPhormHybridCountry)}.
   */
  @NonNull
  public PhormValidationResult hybridValidate (final byte @NonNull [] aPayload) throws PhormClientException
  {
    return hybridValidate (aPayload, (EPhormHybridCountry) null);
  }

  /**
   * Validate a ZUGFeRD / Factur-X hybrid PDF invoice. Calls <code>POST /api/hybrid_validate</code>.
   * The carrier side rules and the PDF/A-3 conformance are evaluated first, then the embedded XML
   * is extracted, its document type determined and its business rules applied. All layers are
   * returned in a single result.
   *
   * @param aPayload
   *        The PDF document to be validated. May not be <code>null</code>.
   * @param eCountry
   *        The country whose specific rules shall be applied. May be <code>null</code> to use the
   *        phorm side default.
   * @return The validation result, carrying the PDF carrier layers first. An invalid document is a
   *         regular result, not an exception.
   * @throws PhormClientException
   *         On a transport problem, a rejected token or an unusable PDF.
   */
  @NonNull
  public PhormValidationResult hybridValidate (final byte @NonNull [] aPayload,
                                               @Nullable final EPhormHybridCountry eCountry) throws PhormClientException
  {
    return _postValidation (CPhormClient.PATH_HYBRID_VALIDATE,
                            _getCountryQuery (eCountry),
                            _createEntity (aPayload, CMimeType.APPLICATION_PDF));
  }

  /**
   * Validate a ZUGFeRD / Factur-X hybrid PDF invoice against the rules of an arbitrary country. Use
   * this instead of {@link #hybridValidate(byte[], EPhormHybridCountry)} if the country code comes
   * from the processed data rather than from a decision in the code - a country that phorm learns
   * about after this library was released is then passed on without a change in here. phorm falls
   * back to its default for a country it does not know.
   *
   * @param aPayload
   *        The PDF document to be validated. May not be <code>null</code>.
   * @param sCountryID
   *        The ID of the country whose specific rules shall be applied, e.g. <code>DE</code>. May
   *        be <code>null</code> or empty to use the phorm side default.
   * @return The validation result, carrying the PDF carrier layers first. An invalid document is a
   *         regular result, not an exception.
   * @throws PhormClientException
   *         If the country code is not a plain code, or on any of the problems listed at
   *         {@link #hybridValidate(byte[], EPhormHybridCountry)}.
   */
  @NonNull
  public PhormValidationResult hybridValidate (final byte @NonNull [] aPayload,
                                               @Nullable final String sCountryID) throws PhormClientException
  {
    return _postValidation (CPhormClient.PATH_HYBRID_VALIDATE,
                            getCountryQuery (sCountryID),
                            _createEntity (aPayload, CMimeType.APPLICATION_PDF));
  }

  /**
   * Validate a ZUGFeRD / Factur-X hybrid PDF invoice with the phorm side default country rules.
   *
   * @param aPayload
   *        The provider of the PDF document. It is streamed to phorm, so a large document is never
   *        held in memory as a whole. May not be <code>null</code>.
   * @return The validation result, carrying the PDF carrier layers first.
   * @throws PhormClientException
   *         On any of the problems listed at {@link #hybridValidate(byte[], EPhormHybridCountry)}.
   */
  @NonNull
  public PhormValidationResult hybridValidate (@NonNull final IHasInputStream aPayload) throws PhormClientException
  {
    return hybridValidate (aPayload, (EPhormHybridCountry) null);
  }

  /**
   * Validate a ZUGFeRD / Factur-X hybrid PDF invoice.
   *
   * @param aPayload
   *        The provider of the PDF document. It is streamed to phorm, so a large document is never
   *        held in memory as a whole. May not be <code>null</code>.
   * @param eCountry
   *        The country whose specific rules shall be applied. May be <code>null</code>.
   * @return The validation result.
   * @throws PhormClientException
   *         If the payload cannot be opened, or on any of the problems listed at
   *         {@link #hybridValidate(byte[], EPhormHybridCountry)}.
   */
  @NonNull
  public PhormValidationResult hybridValidate (@NonNull final IHasInputStream aPayload,
                                               @Nullable final EPhormHybridCountry eCountry) throws PhormClientException
  {
    return _postValidation (CPhormClient.PATH_HYBRID_VALIDATE,
                            _getCountryQuery (eCountry),
                            createStreamingEntity (aPayload, CMimeType.APPLICATION_PDF));
  }

  /**
   * Validate a ZUGFeRD / Factur-X hybrid PDF invoice against the rules of an arbitrary country.
   *
   * @param aPayload
   *        The provider of the PDF document. It is streamed to phorm, so a large document is never
   *        held in memory as a whole. May not be <code>null</code>.
   * @param sCountryID
   *        The ID of the country whose specific rules shall be applied. May be <code>null</code> or
   *        empty to use the phorm side default. See {@link #hybridValidate(byte[], String)} for why
   *        this exists.
   * @return The validation result.
   * @throws PhormClientException
   *         If the payload cannot be opened, if the country code is not a plain code, or on any of
   *         the problems listed at {@link #hybridValidate(byte[], EPhormHybridCountry)}.
   */
  @NonNull
  public PhormValidationResult hybridValidate (@NonNull final IHasInputStream aPayload,
                                               @Nullable final String sCountryID) throws PhormClientException
  {
    return _postValidation (CPhormClient.PATH_HYBRID_VALIDATE,
                            getCountryQuery (sCountryID),
                            createStreamingEntity (aPayload, CMimeType.APPLICATION_PDF));
  }

  /**
   * Validate a hybrid PDF invoice with the phorm side default country rules and get the server
   * rendered XML representation.
   *
   * @param aPayload
   *        The PDF document to be validated. May not be <code>null</code>.
   * @return The <code>validationResults</code> XML document. Never <code>null</code>.
   * @throws PhormClientException
   *         On any of the problems listed at {@link #hybridValidate(byte[], EPhormHybridCountry)}.
   */
  @NonNull
  public IMicroDocument hybridValidateAsXML (final byte @NonNull [] aPayload) throws PhormClientException
  {
    return hybridValidateAsXML (aPayload, (EPhormHybridCountry) null);
  }

  /**
   * Validate a hybrid PDF invoice and get the server rendered XML representation. Calls
   * <code>POST /api/hybrid_validate</code> with <code>Accept: application/xml</code>.
   *
   * @param aPayload
   *        The PDF document to be validated. May not be <code>null</code>.
   * @param eCountry
   *        The country whose specific rules shall be applied. May be <code>null</code>.
   * @return The <code>validationResults</code> XML document. Never <code>null</code>.
   * @throws PhormClientException
   *         On any of the problems listed at {@link #hybridValidate(byte[], EPhormHybridCountry)}.
   */
  @NonNull
  public IMicroDocument hybridValidateAsXML (final byte @NonNull [] aPayload,
                                             @Nullable final EPhormHybridCountry eCountry) throws PhormClientException
  {
    return _postAsXML (CPhormClient.PATH_HYBRID_VALIDATE,
                       _getCountryQuery (eCountry),
                       _createEntity (aPayload, CMimeType.APPLICATION_PDF));
  }

  /**
   * Validate a hybrid PDF invoice against the rules of an arbitrary country and get the server
   * rendered XML representation.
   *
   * @param aPayload
   *        The PDF document to be validated. May not be <code>null</code>.
   * @param sCountryID
   *        The ID of the country whose specific rules shall be applied. May be <code>null</code> or
   *        empty to use the phorm side default. See {@link #hybridValidate(byte[], String)} for why
   *        this exists.
   * @return The <code>validationResults</code> XML document. Never <code>null</code>.
   * @throws PhormClientException
   *         If the country code is not a plain code, or on any of the problems listed at
   *         {@link #hybridValidate(byte[], EPhormHybridCountry)}.
   */
  @NonNull
  public IMicroDocument hybridValidateAsXML (final byte @NonNull [] aPayload,
                                             @Nullable final String sCountryID) throws PhormClientException
  {
    return _postAsXML (CPhormClient.PATH_HYBRID_VALIDATE,
                       getCountryQuery (sCountryID),
                       _createEntity (aPayload, CMimeType.APPLICATION_PDF));
  }

  /**
   * Validate a hybrid PDF invoice with the phorm side default country rules and get the server
   * rendered HTML report.
   *
   * @param aPayload
   *        The PDF document to be validated. May not be <code>null</code>.
   * @return The ready to render HTML report. Never <code>null</code>.
   * @throws PhormClientException
   *         On any of the problems listed at {@link #hybridValidate(byte[], EPhormHybridCountry)}.
   */
  @NonNull
  public String hybridValidateAsHtml (final byte @NonNull [] aPayload) throws PhormClientException
  {
    return hybridValidateAsHtml (aPayload, (EPhormHybridCountry) null);
  }

  /**
   * Validate a hybrid PDF invoice and get the server rendered HTML report. Calls
   * <code>POST /api/hybrid_validate</code> with <code>Accept: text/html</code>.
   *
   * @param aPayload
   *        The PDF document to be validated. May not be <code>null</code>.
   * @param eCountry
   *        The country whose specific rules shall be applied. May be <code>null</code>.
   * @return The ready to render HTML report. Never <code>null</code>.
   * @throws PhormClientException
   *         On any of the problems listed at {@link #hybridValidate(byte[], EPhormHybridCountry)}.
   */
  @NonNull
  public String hybridValidateAsHtml (final byte @NonNull [] aPayload,
                                      @Nullable final EPhormHybridCountry eCountry) throws PhormClientException
  {
    return _postAsHtml (CPhormClient.PATH_HYBRID_VALIDATE,
                        _getCountryQuery (eCountry),
                        _createEntity (aPayload, CMimeType.APPLICATION_PDF));
  }

  /**
   * Validate a hybrid PDF invoice against the rules of an arbitrary country and get the server
   * rendered HTML report.
   *
   * @param aPayload
   *        The PDF document to be validated. May not be <code>null</code>.
   * @param sCountryID
   *        The ID of the country whose specific rules shall be applied. May be <code>null</code> or
   *        empty to use the phorm side default. See {@link #hybridValidate(byte[], String)} for why
   *        this exists.
   * @return The ready to render HTML report. Never <code>null</code>.
   * @throws PhormClientException
   *         If the country code is not a plain code, or on any of the problems listed at
   *         {@link #hybridValidate(byte[], EPhormHybridCountry)}.
   */
  @NonNull
  public String hybridValidateAsHtml (final byte @NonNull [] aPayload,
                                      @Nullable final String sCountryID) throws PhormClientException
  {
    return _postAsHtml (CPhormClient.PATH_HYBRID_VALIDATE,
                        getCountryQuery (sCountryID),
                        _createEntity (aPayload, CMimeType.APPLICATION_PDF));
  }

  /**
   * @return A new empty Builder. Never <code>null</code>.
   */
  @NonNull
  public static Builder builder ()
  {
    return new Builder ();
  }

  /**
   * Builder for {@link PhormClient}. The base URL is mandatory, everything else has a sensible
   * default.
   *
   * @author Philip Helger
   */
  public static class Builder implements IBuilder <PhormClient>
  {
    private String m_sBaseURL;
    private String m_sAPIPath = CPhormClient.DEFAULT_API_PATH;
    private String m_sToken;
    private IIdentifierFactory m_aIdentifierFactory = SimpleIdentifierFactory.INSTANCE;
    private HttpClientSettings m_aHttpClientSettings;
    private HttpClientManager m_aHttpClientManager;

    /**
     * Builder constructor with all fields empty.
     */
    public Builder ()
    {}

    /**
     * @param s
     *        The base URL of the phorm instance, e.g. <code>http://localhost:8080</code>. A
     *        trailing slash is removed. Mandatory.
     * @return this for chaining
     */
    @NonNull
    public Builder baseURL (@Nullable final String s)
    {
      m_sBaseURL = s;
      return this;
    }

    /**
     * @param s
     *        The path below which the APIs reside. Defaults to
     *        {@link CPhormClient#DEFAULT_API_PATH}.
     * @return this for chaining
     */
    @NonNull
    public Builder apiPath (@Nullable final String s)
    {
      m_sAPIPath = s;
      return this;
    }

    /**
     * @param s
     *        The value of the <code>X-Token</code> HTTP header, matching the server side
     *        <code>phorm.api.requiredtoken</code>. Omit it only if the target instance requires no
     *        token.
     * @return this for chaining
     */
    @NonNull
    public Builder token (@Nullable final String s)
    {
      m_sToken = s;
      return this;
    }

    /**
     * @param a
     *        The identifier factory used to parse the identifiers of the determined document
     *        details. Defaults to {@link SimpleIdentifierFactory}, which accepts any identifier.
     *        Use <code>PeppolIdentifierFactory</code> to have Peppol constraints enforced.
     * @return this for chaining
     */
    @NonNull
    public Builder identifierFactory (@Nullable final IIdentifierFactory a)
    {
      m_aIdentifierFactory = a;
      return this;
    }

    /**
     * @param a
     *        The HTTP settings - timeouts, proxy, TLS - to create an own connection pool from.
     *        Ignored if an {@link HttpClientManager} is provided.
     * @return this for chaining
     */
    @NonNull
    public Builder httpClientSettings (@Nullable final HttpClientSettings a)
    {
      m_aHttpClientSettings = a;
      return this;
    }

    /**
     * @param a
     *        An externally managed connection pool. It is <b>not</b> closed by
     *        {@link PhormClient#close()}.
     * @return this for chaining
     */
    @NonNull
    public Builder httpClientManager (@Nullable final HttpClientManager a)
    {
      m_aHttpClientManager = a;
      return this;
    }

    @NonNull
    public PhormClient build ()
    {
      if (StringHelper.isEmpty (m_sBaseURL))
        throw new IllegalStateException ("The phorm base URL is required");
      if (StringHelper.isEmpty (m_sAPIPath))
        throw new IllegalStateException ("The phorm API path is required");
      if (m_aIdentifierFactory == null)
        throw new IllegalStateException ("The identifier factory is required");

      return new PhormClient (m_sBaseURL,
                              m_sAPIPath,
                              m_sToken,
                              m_aIdentifierFactory,
                              m_aHttpClientSettings,
                              m_aHttpClientManager);
    }
  }
}
