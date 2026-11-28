# phorm-client

<!-- ph-badge-start -->
[![Sonatype Central](https://maven-badges.sml.io/sonatype-central/com.helger/phorm-client/badge.svg)](https://maven-badges.sml.io/sonatype-central/com.helger/phorm-client/)
[![javadoc](https://javadoc.io/badge2/com.helger/phorm-client/javadoc.svg)](https://javadoc.io/doc/com.helger/phorm-client)

> If this project saved you some time or made your day a little easier, a star would mean a lot — it helps others find it too.
<!-- ph-badge-end -->

Java client library for the REST API of [phorm](https://github.com/phax/phorm), the standalone XML
document validation service built on [phive](https://github.com/phax/phive).

It covers all five phorm validation APIs, the version API and the two admin APIs, hands back the regular phive and
[ddd](https://github.com/phax/ddd) object models, and keeps the unparsed HTTP response reachable for
everything the typed model does not cover.

* Requires Java 17 or newer
* Built on [ph-httpclient](https://github.com/phax/ph-web) and [ph-json](https://github.com/phax/ph-commons)

# Usage

Create one client and reuse it - it holds an HTTP connection pool.

```java
try (final PhormClient aClient = PhormClient.builder ()
                                            .baseURL ("http://localhost:8080")
                                            .token ("phorm-dev-token")
                                            .build ())
{
  final PhormValidationResult aResult = aClient.determineAndValidate (aXmlBytes);

  System.out.println (aResult.getDocumentDetails ().getProfileName ());
  if (!aResult.isSuccess ())
    for (final IError aError : aResult.getAllFailures ())
      System.out.println (aError.getErrorLevel ().getID () + " " + aError.getErrorID ());
}
```

## An invalid document is not an error

This is the single most important thing to know about the phorm API. With the server side default
`phorm.api.response.onfailure.http400=true`, phorm answers a *content wise invalid* document with
HTTP 400 **and a regular result body**. A plain HTTP client throws on that status and loses the
findings.

This library therefore distinguishes the two cases:

* A document that was validated and found invalid is an ordinary `PhormValidationResult` with
  `isSuccess()` returning `false` and the findings in `getAllErrors()` / `getAllFailures()`.
* Only a problem that prevented validation from happening at all - a transport failure, a rejected
  `X-Token`, a payload that is not XML, an unresolvable VESID - raises a `PhormClientException`.
  It carries the offending `PhormRawResponse` including its unparsed body.

## Telling the failure modes apart

Not every `PhormClientException` means the same thing, and the difference decides whether retrying
the identical call can ever help. Every exception therefore carries an `EPhormErrorType`:

| `getErrorType ()` | Meaning | Examples |
|---|---|---|
| `REQUEST_ERROR` | phorm never produced a verdict because the request was faulty. Repeating it unchanged cannot help. | unreadable payload, unparsable or unresolvable VESID, payload that is not XML, missing or wrong `X-Token` (HTTP 403), any other 4xx |
| `SERVICE_UNAVAILABLE` | phorm could not be reached, or reported itself as broken. Repeating the call later may succeed. | connection refused, timeout, any 5xx |
| `RESPONSE_ERROR` | phorm answered, but the answer could not be used. | a body that is not the requested representation, an unexpected status code |

This matters for a caller that has to decide between deferring a document and rejecting it - a
verification service that is merely down must not turn into a rejected document.

## Reaching the errors

| Method | Returns |
|---|---|
| `isSuccess()` | whether the document is considered valid |
| `getOverallValidity()` | the phive `EExtendedValidity` |
| `getAllErrors()` | flattened findings of level `ERROR` and above |
| `getAllFailures()` | flattened findings of level `WARN` and above |
| `getValidationResultList()` | the full phive `ValidationResultList`, one entry per validation layer |
| `getDocumentDetails()` | the ddd `DocumentDetails` - sender, receiver, doctype, VESID, ... |
| `getServerException()` | the server side stack trace, if phorm itself failed |
| `getAsJson()` | the complete response body as `IJsonObject` |
| `getRawResponse()` | status code, content type and unparsed bytes |

# API coverage

| phorm API | Method |
|---|---|
| `GET /ping` | `isAlive ()` |
| `GET /ready` | `isReady ()` |
| `GET /api/get/version` | `getServerVersion ()`, `getServerVersionAsText ()` |
| `GET /api/get/vesids` | `getAllVESIDs ()` / `getAllVESIDs (boolean)` |
| `POST /api/validate/{vesid}` | `validate (...)`, `validateAsXML (...)`, `validateAsHtml (...)` |
| `POST /api/determinedoctype` | `determineDocumentDetails (...)`, `determineDocumentDetailsAsXML (...)` |
| `POST /api/dd_and_validate` | `determineAndValidate (...)`, `determineAndValidateAsXML (...)`, `determineAndValidateAsHtml (...)` |
| `POST /api/hybrid_validate` | `hybridValidate (...)`, `hybridValidateAsXML (...)`, `hybridValidateAsHtml (...)` |
| `GET /api/admin/repositories` | `getAdminRepositories ()` |
| `POST /api/admin/repositories/reload` | `reloadAdminRepositories ()` |

`getServerVersion ()` returns the major and minor version of phorm, so a caller can check for a
feature before using it - `isAtLeast (2, 3)` for the admin APIs, for example. phorm does not disclose
the micro version. `getServerVersionAsText ()` requests the
`text/plain` representation and returns it as is, e.g. `2.3`. A phorm without this API answers HTTP
404, and both methods then return `null`.
The API exists from phorm 2.2.18 on ([phorm #25](https://github.com/phax/phorm/issues/25)).
Feature checks therefore only work against phorm 2.2.18 or later; `null` means older than that.

`isAlive ()` is the liveness check: it calls `GET /ping` and returns `true` if phorm answers with
the plain text `pong`. phorm does this as soon as it runs, also while it is still loading its
validation rules. Anything else - not reachable, an HTTP error or a different body - is a
`PhormClientException`. Like `/ready`, `/ping` is relative to the base URL, and no token is sent.

`isReady ()` tells whether phorm can currently serve validation requests, e.g. for a readiness check.
It returns `true` for HTTP 200 with `{"ready":true}` and `false` for any HTTP 503 - phorm answers
that while it is still loading its validation rules after the startup. A phorm that cannot be reached
at all is a `PhormClientException` of type `SERVICE_UNAVAILABLE`. In contrast to all other APIs,
`/ready` is relative to the base URL and not to the API path, and no token is sent.
The API exists from phorm 2.2.18 on ([phorm #26](https://github.com/phax/phorm/issues/26)). An older
phorm answers HTTP 404, and `isReady ()` then throws a `PhormClientException` of type `REQUEST_ERROR`
that names the reason, using `getServerVersion ()` to tell an old phorm from a wrong base URL.

Each `PhormVESID` returned by `getAllVESIDs` carries `getRepositoryID ()`: the ID of the external
repository the rule set was loaded from, or `null` for a rule set built into phorm (`isBuiltIn ()`).

The country of `hybrid_validate` can be given as the `EPhormHybridCountry` enum - `DE`, `FR` or
`OTHER`, which is what phorm knows today - or as a plain `String`. Use the `String` variant when the
code comes from the processed data rather than from a decision in the code: a country that phorm
learns about after this release is then passed on without a new version of this library, and phorm
falls back to its default for anything it does not know.

Every method that returns a typed result accepts the payload as a `byte []` or as an
`IHasInputStream`. The latter is **streamed** to phorm, so a large document is never held in memory
as a whole - `IReadableResource` is an `IHasInputStream`, and `HasInputStream.multiple (...)` builds
one from any `InputStream` supplier:

```java
aClient.determineAndValidate (HasInputStream.multiple ( () -> aStore.openForRead (sPath)));
```

A payload that can be read more than once yields a repeatable HTTP entity, so the client may replay
the request; a read-once payload is sent as a plain stream. The `...AsXML` and `...AsHtml` variants
take a `byte []` only.

`validate` additionally accepts the VESID as a `String` besides the `DVRCoordinate`.

Internally JSON is always requested, because it is the most compact of the three representations
phorm offers and the only one convertible into the phive and ddd models. The `...AsXML` and
`...AsHtml` variants exist for callers that want to store or display the server rendered
representation instead; they return an `IMicroDocument` and a `String` respectively.

`determineDocumentDetails` returns `null` when phorm cannot determine the document type - it answers
that case with HTTP 204.

## Admin APIs

The admin APIs check the `X-Token` header against the server side `phorm.api.admin.token`, not
against `phorm.api.requiredtoken`. Set it with `adminToken (...)` on the builder; it is sent to the
admin APIs only. Without it, an admin call fails with a `REQUEST_ERROR` before anything is sent.
phorm only enables the admin APIs if `phorm.api.admin.token` is set, and answers HTTP 404 otherwise,
which is also reported as a `REQUEST_ERROR`.

```java
try (final PhormClient aClient = PhormClient.builder ()
                                            .baseURL ("http://localhost:8080")
                                            .adminToken ("phorm-admin-token")
                                            .build ())
{
  final PhormRepositoryReloadResult aResult = aClient.reloadAdminRepositories ();
  if (aResult.isSuccess ())
    System.out.println ("Added " + aResult.getAllAddedVESIDs () + ", removed " + aResult.getAllRemovedVESIDs ());
  else
    for (final IError aError : aResult.getErrors ())
      System.out.println (aError.getAsString (Locale.US));
}
```

A failed reload is a regular result, just like an invalid document: phorm answers it with HTTP 500
and keeps the previously loaded rule sets active, `isSuccess ()` is `false` and `getErrors ()` holds
the problems. `getErrors ()` may also contain warnings after a successful reload.

# Configuration

Everything but the base URL has a default:

```java
PhormClient.builder ()
           .baseURL ("https://phorm.example.org")   // mandatory
           .token ("...")                           // the X-Token header value
           .adminToken ("...")                      // the X-Token header value of the admin APIs
           .apiPath ("/api")                        // default
           .identifierFactory (SimpleIdentifierFactory.INSTANCE) // default
           .httpClientSettings (aHCS)               // timeouts, proxy, TLS
           .httpClientManager (aHCM)                // bring your own pool; not closed by close()
           .build ();
```

The identifier factory parses the participant, document type and process identifiers of the
determined document details. The default `SimpleIdentifierFactory` accepts any identifier; pass
`PeppolIdentifierFactory` from `peppol-id` to have the Peppol constraints enforced - that artifact
is deliberately not a dependency of this library.

# Maven usage

Add the following to your `pom.xml` to use this artifact, replacing `x.y.z` with the latest version:

```xml
<dependency>
  <groupId>com.helger</groupId>
  <artifactId>phorm-client</artifactId>
  <version>x.y.z</version>
</dependency>
```

# News and noteworthy

v1.1.0 - work in progress
* Added the admin APIs `GET /api/admin/repositories` (`getAdminRepositories ()`) and
  `POST /api/admin/repositories/reload` (`reloadAdminRepositories ()`), using the new builder
  property `adminToken`
* A failed repository reload is a regular `PhormRepositoryReloadResult` with the problems in
  `getErrors ()`, not an exception
* Added `getServerVersion ()` for the `GET /api/get/version` API of phorm 2.2.18 and later
  ([phorm #25](https://github.com/phax/phorm/issues/25)), returning the major and minor version as
  `PhormServerVersion`, or `null` for a phorm without this API. `getServerVersionAsText ()` returns
  the `text/plain` representation, e.g. `2.3`
* Added `isAlive ()` for the liveness API `GET /ping`, returning `true` if phorm answers `pong`
* Added `isReady ()` for the readiness API `GET /ready` of phorm 2.2.18 and later
  ([phorm #26](https://github.com/phax/phorm/issues/26)), returning `true` for HTTP 200 and `false`
  for HTTP 503
* Added `PhormVESID.getRepositoryID ()` and `isBuiltIn ()`, filled from the `repository` field of
  `/api/get/vesids`

v1.0.0 - 2026-09-28
* Initial release
* Covers all five phorm REST APIs in JSON, XML and HTML representation
* Returns the regular phive `ValidationResultList` and ddd `DocumentDetails` object models
* Treats a content wise invalid document as a regular result instead of an error, so the findings of
  an HTTP 400 answer are not lost
* Payloads may be passed as `IHasInputStream` and are then streamed, so a large document is never
  held in memory as a whole. This replaces the previous `IReadableResource` overloads, which
  materialized the payload
* The hybrid PDF country may be passed as a plain `String` besides `EPhormHybridCountry`, so a
  country added to the phorm side rules later needs no new version of this library
* `PhormClientException` carries an `EPhormErrorType` that tells a faulty request, an unavailable
  service and an unusable answer apart

---

My personal [Coding Styleguide](https://github.com/phax/meta/blob/master/CodingStyleguide.md) |
It is appreciated if you star the GitHub project if you like it.
