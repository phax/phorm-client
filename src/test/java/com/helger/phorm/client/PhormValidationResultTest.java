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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.Test;

import com.helger.ddd.DocumentDetails;
import com.helger.diagnostics.error.level.EErrorLevel;
import com.helger.peppolid.factory.SimpleIdentifierFactory;
import com.helger.phive.api.result.ValidationResultList;
import com.helger.phive.api.validity.EExtendedValidity;

/**
 * Test class for class {@link PhormValidationResult}. It works on a realistic
 * <code>/api/dd_and_validate</code> response, so it covers the conversion into the ddd and phive
 * object models without requiring a running phorm instance.
 *
 * @author Philip Helger
 */
public final class PhormValidationResultTest
{
  private static final String RESPONSE_INVALID = """
      {
        "documentDetails": {
          "syntaxID": "ubl2-invoice",
          "syntaxVersion": "2.1",
          "sender": "iso6523-actorid-upis::0088:9482348239847239874",
          "receiver": "iso6523-actorid-upis::0002:FR23342",
          "doctype": "busdox-docid-qns::urn:oasis:names:specification:ubl:schema:xsd:Invoice-2::Invoice##urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0::2.1",
          "process": "cenbii-procid-ubl::urn:fdc:peppol.eu:2017:poacc:billing:01:1.0",
          "customizationID": "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0",
          "bdid": "Snippet1",
          "senderName": "SupplierTradingName Ltd.",
          "senderCountryCode": "GB",
          "receiverName": "BuyerTradingName AS",
          "receiverCountryCode": "SE",
          "vesid": "eu.peppol.bis3:invoice:latest-active",
          "profileName": "Peppol BIS Billing UBL Invoice V3"
        },
        "ves": {
          "vesid": "eu.peppol.bis3:invoice:2024.5",
          "name": "Peppol BIS Billing UBL Invoice V3",
          "deprecated": false
        },
        "validationDateTime": "2026-09-28T10:00:00Z",
        "success": false,
        "interrupted": false,
        "mostSevereErrorLevel": "ERROR",
        "results": [
          {
            "success": "TRUE",
            "validity": "valid",
            "artifactType": "xsd",
            "artifactPathType": "classpath",
            "artifactPath": "/schemas/UBL-Invoice-2.1.xsd",
            "items": [],
            "durationMS": 12
          },
          {
            "success": "FALSE",
            "validity": "invalid",
            "artifactType": "schematron-xslt2",
            "artifactPathType": "classpath",
            "artifactPath": "/rules/PEPPOL-EN16931-UBL.xslt",
            "items": [
              {
                "errorLevel": "ERROR",
                "errorID": "PEPPOL-EN16931-R001",
                "errorLocation": "/Invoice[1]",
                "errorText": "Business process MUST be provided.",
                "test": "exists(cbc:ProfileID)"
              },
              {
                "errorLevel": "WARN",
                "errorID": "PEPPOL-EN16931-R110",
                "errorLocation": "/Invoice[1]/cac:InvoicePeriod[1]",
                "errorText": "Start date MUST be earlier than end date."
              }
            ],
            "durationMS": 640
          }
        ],
        "durationMS": 652,
        "invocationDateTime": "2026-09-28T10:00:00.123Z",
        "invocationDurationMillis": 700
      }
      """;

  private static PhormRawResponse _resp (final int nStatusCode, final String sJson)
  {
    return new PhormRawResponse (nStatusCode,
                                 "application/json; charset=UTF-8",
                                 StandardCharsets.UTF_8,
                                 sJson.getBytes (StandardCharsets.UTF_8));
  }

  @Test
  public void testInvalidDocument () throws PhormClientException
  {
    // phorm answers an invalid document with HTTP 400 and a regular body
    final PhormValidationResult aResult = PhormValidationResult.createFromResponse (_resp (400, RESPONSE_INVALID),
                                                                                    SimpleIdentifierFactory.INSTANCE);

    assertFalse (aResult.isSuccess ());
    assertFalse (aResult.isInterrupted ());
    assertEquals ("ERROR", aResult.getMostSevereErrorLevel ());
    assertEquals (700, aResult.getInvocationDurationMillis ());
    assertNotNull (aResult.getInvocationDateTime ());
    assertNull (aResult.getServerException ());
    assertNull (aResult.getCountry ());
    assertNotNull (aResult.getAsJson ());
    assertEquals (400, aResult.getRawResponse ().getStatusCode ());

    // The document details of the determining APIs
    assertTrue (aResult.hasDocumentDetails ());
    final DocumentDetails aDD = aResult.getDocumentDetails ();
    assertNotNull (aDD);
    assertEquals ("ubl2-invoice", aDD.getSyntaxID ());
    assertEquals ("2.1", aDD.getSyntaxVersion ());
    assertEquals ("eu.peppol.bis3:invoice:latest-active", aDD.getVESID ());
    assertEquals ("Peppol BIS Billing UBL Invoice V3", aDD.getProfileName ());
    assertEquals ("GB", aDD.getSenderCountryCode ());
    assertNotNull (aDD.getSenderID ());
    assertEquals ("iso6523-actorid-upis::0088:9482348239847239874", aDD.getSenderID ().getURIEncoded ());

    // The validation results as the regular phive model
    final ValidationResultList aVRL = aResult.getValidationResultList ();
    assertNotNull (aVRL);
    assertEquals (2, aVRL.size ());
    assertEquals (EExtendedValidity.INVALID, aResult.getOverallValidity ());

    // One ERROR and one WARN across both layers. getAllErrors() is ERROR and above,
    // getAllFailures() is WARN and above - the phive semantics.
    assertEquals (1, aResult.getAllErrors ().size ());
    assertEquals (2, aResult.getAllFailures ().size ());
    assertEquals (EErrorLevel.ERROR, aResult.getAllErrors ().getMostSevereErrorLevel ());
    assertEquals ("PEPPOL-EN16931-R001", aResult.getAllErrors ().getFirstOrNull ().getErrorID ());
    assertEquals ("Business process MUST be provided.",
                  aResult.getAllErrors ().getFirstOrNull ().getErrorText (Locale.US));
    assertEquals ("PEPPOL-EN16931-R110", aResult.getAllFailures ().getAtIndex (1, null).getErrorID ());
  }

  @Test
  public void testValidDocument () throws PhormClientException
  {
    final String sJson = """
        {
          "validationDateTime": "2026-09-28T10:00:00Z",
          "success": true,
          "interrupted": false,
          "mostSevereErrorLevel": "SUCCESS",
          "results": [
            {
              "success": "TRUE",
              "validity": "valid",
              "artifactType": "xsd",
              "artifactPathType": "classpath",
              "artifactPath": "/schemas/UBL-Invoice-2.1.xsd",
              "items": [],
              "durationMS": 9
            }
          ],
          "durationMS": 9,
          "invocationDateTime": "2026-09-28T10:00:00.123Z",
          "invocationDurationMillis": 42
        }
        """;
    final PhormValidationResult aResult = PhormValidationResult.createFromResponse (_resp (200, sJson),
                                                                                    SimpleIdentifierFactory.INSTANCE);
    assertTrue (aResult.isSuccess ());
    assertFalse (aResult.hasDocumentDetails ());
    assertNull (aResult.getDocumentDetails ());
    assertNotNull (aResult.getValidationResultList ());
    assertEquals (EExtendedValidity.VALID, aResult.getOverallValidity ());
    assertTrue (aResult.getAllErrors ().isEmpty ());
    assertTrue (aResult.getAllFailures ().isEmpty ());
  }

  @Test
  public void testHybridCountry () throws PhormClientException
  {
    final String sJson = """
        {
          "country": "DE",
          "validationDateTime": "2026-09-28T10:00:00Z",
          "success": true,
          "interrupted": false,
          "results": [],
          "durationMS": 5
        }
        """;
    final PhormValidationResult aResult = PhormValidationResult.createFromResponse (_resp (200, sJson),
                                                                                    SimpleIdentifierFactory.INSTANCE);
    assertEquals (EPhormHybridCountry.DE, aResult.getCountry ());
    assertTrue (aResult.isSuccess ());
  }

  @Test
  public void testServerException () throws PhormClientException
  {
    // CommonAPIInvoker adds "success":false plus "exception" when phorm itself failed
    final String sJson = """
        {
          "success": false,
          "exception": {
            "class": "java.lang.IllegalStateException",
            "message": "Something went wrong"
          },
          "invocationDateTime": "2026-09-28T10:00:00.123Z",
          "invocationDurationMillis": 3
        }
        """;
    final PhormValidationResult aResult = PhormValidationResult.createFromResponse (_resp (200, sJson),
                                                                                    SimpleIdentifierFactory.INSTANCE);
    assertFalse (aResult.isSuccess ());
    assertNotNull (aResult.getServerException ());
    assertEquals ("Something went wrong", aResult.getServerException ().getAsString ("message"));
    // No "results" array means no convertible result list
    assertNull (aResult.getValidationResultList ());
    assertEquals (EExtendedValidity.UNCLEAR, aResult.getOverallValidity ());
    assertTrue (aResult.getAllErrors ().isEmpty ());
  }

  @Test
  public void testNonJsonBody ()
  {
    final PhormRawResponse aResp = new PhormRawResponse (400,
                                                         "text/plain",
                                                         StandardCharsets.UTF_8,
                                                         "The VESID 'x' could not be resolved.".getBytes (StandardCharsets.UTF_8));
    try
    {
      PhormValidationResult.createFromResponse (aResp, SimpleIdentifierFactory.INSTANCE);
      throw new IllegalStateException ("Expected a PhormClientException");
    }
    catch (final PhormClientException ex)
    {
      assertEquals (400, ex.getStatusCode ());
    }
  }
}
