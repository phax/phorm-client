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
package com.helger.phorm.client.supplementary.tools;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.io.resource.FileSystemResource;
import com.helger.phorm.client.PhormClient;
import com.helger.phorm.client.PhormVESID;
import com.helger.phorm.client.PhormValidationResult;

/**
 * Example code showing how to use the {@link PhormClient}. Start a phorm instance first, e.g. with
 * <code>com.helger.phorm.jetty.RunInJettyPhorm</code>, and adjust the constants below.
 *
 * @author Philip Helger
 */
public final class MainRunPhormClient
{
  private static final Logger LOGGER = LoggerFactory.getLogger (MainRunPhormClient.class);

  private static final String BASE_URL = "http://localhost:8080";
  private static final String API_TOKEN = "phorm-dev-token";
  private static final String INPUT_XML_FILE = "src/test/resources/external/example.xml";

  private MainRunPhormClient ()
  {}

  public static void main (final String [] aArgs) throws Exception
  {
    try (final PhormClient aClient = PhormClient.builder ().baseURL (BASE_URL).token (API_TOKEN).build ())
    {
      // 1. What can this instance validate?
      for (final PhormVESID aVESID : aClient.getAllVESIDs ())
        LOGGER.info (aVESID.getVESID ().getAsSingleID () + " - " + aVESID.getName ());

      // 2. Determine the document type and validate in one call
      final PhormValidationResult aResult = aClient.determineAndValidate (new FileSystemResource (INPUT_XML_FILE));

      if (aResult.hasDocumentDetails ())
        LOGGER.info ("Detected " +
                     aResult.getDocumentDetails ().getProfileName () +
                     " using VESID " +
                     aResult.getDocumentDetails ().getVESID ());

      // 3. An invalid document is a regular result - not an exception
      LOGGER.info ("Valid: " + aResult.isSuccess () + " (" + aResult.getOverallValidity () + ")");
      aResult.getAllFailures ()
             .forEach (x -> LOGGER.info ("  " +
                                         x.getErrorLevel ().getID () +
                                         " " +
                                         x.getErrorID () +
                                         ": " +
                                         x.getErrorText (Locale.US)));
    }
  }
}
