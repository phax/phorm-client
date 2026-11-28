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

import com.helger.annotation.concurrent.Immutable;

/**
 * Constants for the phorm REST API.
 *
 * @author Philip Helger
 */
@Immutable
public final class CPhormClient
{
  /** The HTTP header carrying the API token */
  public static final String HEADER_X_TOKEN = "X-Token";

  /** The default path below which all APIs reside */
  public static final String DEFAULT_API_PATH = "/api";

  /**
   * Path of the liveness API. In contrast to all other paths it is relative to the base URL and not
   * to the API path.
   */
  public static final String PATH_PING = "/ping";
  /** The plain text body of the liveness API */
  public static final String PING_RESPONSE = "pong";

  /**
   * Path of the readiness API. In contrast to all other paths it is relative to the base URL and
   * not to the API path.
   */
  public static final String PATH_READY = "/ready";

  /** Path of the "validate against a specific VESID" API. The VESID is appended. */
  public static final String PATH_VALIDATE = "/validate/";
  /** Path of the "get all VESIDs" API */
  public static final String PATH_GET_VESIDS = "/get/vesids";
  /** Path of the "get the major and minor version" API */
  public static final String PATH_GET_VERSION = "/get/version";
  /** Path of the "determine document type" API */
  public static final String PATH_DETERMINE_DOCTYPE = "/determinedoctype";
  /** Path of the "determine document type and validate" API */
  public static final String PATH_DD_AND_VALIDATE = "/dd_and_validate";
  /** Path of the "validate a hybrid PDF" API */
  public static final String PATH_HYBRID_VALIDATE = "/hybrid_validate";
  /** Path of the admin "get the external repositories" API */
  public static final String PATH_ADMIN_REPOSITORIES = "/admin/repositories";
  /** Path of the admin "reload the external repositories" API */
  public static final String PATH_ADMIN_REPOSITORIES_RELOAD = "/admin/repositories/reload";

  /** URL parameter of {@link #PATH_GET_VESIDS} to also return deprecated VESIDs */
  public static final String QUERY_PARAM_INCLUDE_DEPRECATED = "include-deprecated";
  /** URL parameter of {@link #PATH_HYBRID_VALIDATE} to select the country specific rules */
  public static final String QUERY_PARAM_COUNTRY = "country";

  /** JSON field with the document details, present in dd_and_validate and hybrid_validate */
  public static final String JSON_DOCUMENT_DETAILS = "documentDetails";
  /** JSON field with the applied country, present in hybrid_validate only */
  public static final String JSON_COUNTRY = "country";
  /** JSON field with the date and time the API was invoked */
  public static final String JSON_INVOCATION_DATETIME = "invocationDateTime";
  /** JSON field with the overall API invocation duration in milliseconds */
  public static final String JSON_INVOCATION_DURATION_MILLIS = "invocationDurationMillis";

  /** JSON field with the number of returned VESIDs */
  public static final String JSON_COUNT = "count";
  /** JSON field with the array of returned VESIDs */
  public static final String JSON_VESIDS = "vesids";
  /** JSON field of a single VESID entry holding the coordinate */
  public static final String JSON_VESID = "vesid";
  /** JSON field of a single VESID entry holding the display name */
  public static final String JSON_NAME = "name";
  /** JSON field of a single VESID entry indicating deprecation */
  public static final String JSON_DEPRECATED = "deprecated";
  /** JSON field of a single VESID entry indicating it is the latest version */
  public static final String JSON_LATEST = "latest";
  /**
   * JSON field of a single VESID entry holding the ID of the external repository it was loaded
   * from. Absent for a built-in VESID.
   */
  public static final String JSON_REPOSITORY = "repository";

  /** JSON field of the version API with the major version */
  public static final String JSON_MAJOR = "major";
  /** JSON field of the version API with the minor version */
  public static final String JSON_MINOR = "minor";

  /** JSON field of the readiness API */
  public static final String JSON_READY = "ready";

  /** JSON field of the admin APIs with the absolute path of the repository configuration file */
  public static final String JSON_CONFIG_FILE = "configFile";
  /** JSON field of the admin APIs with the date and time of the last successful load */
  public static final String JSON_LOAD_DATETIME = "loadDateTime";
  /** JSON field of the admin APIs with the array of external repositories */
  public static final String JSON_REPOSITORIES = "repositories";
  /** JSON field of a single repository entry holding its ID */
  public static final String JSON_ID = "id";
  /** JSON field of a single repository entry holding the number of VES loaded from it */
  public static final String JSON_VES_COUNT = "vesCount";
  /** JSON field of the reload API with the array of VESIDs that were added */
  public static final String JSON_ADDED = "added";
  /** JSON field of the reload API with the array of VESIDs that were removed */
  public static final String JSON_REMOVED = "removed";
  /** JSON field of the reload API with the array of problems that occurred */
  public static final String JSON_ERRORS = "errors";

  private CPhormClient ()
  {}
}
