# phorm-client

<!-- ph-badge-start -->
[![Sonatype Central](https://maven-badges.sml.io/sonatype-central/com.helger/phorm-client/badge.svg)](https://maven-badges.sml.io/sonatype-central/com.helger/phorm-client/)
[![javadoc](https://javadoc.io/badge2/com.helger/phorm-client/javadoc.svg)](https://javadoc.io/doc/com.helger/phorm-client)

> If this project saved you some time or made your day a little easier, a star would mean a lot — it helps others find it too.
<!-- ph-badge-end -->

Java client library for the REST API of [phorm](https://github.com/phax/phorm), the standalone XML
document validation service built on [phive](https://github.com/phax/phive).

It covers all five phorm APIs, hands back the regular phive and
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
| `GET /api/get/vesids` | `getAllVESIDs ()` / `getAllVESIDs (boolean)` |
| `POST /api/validate/{vesid}` | `validate (...)`, `validateAsXML (...)`, `validateAsHtml (...)` |
| `POST /api/determinedoctype` | `determineDocumentDetails (...)`, `determineDocumentDetailsAsXML (...)` |
| `POST /api/dd_and_validate` | `determineAndValidate (...)`, `determineAndValidateAsXML (...)`, `determineAndValidateAsHtml (...)` |
| `POST /api/hybrid_validate` | `hybridValidate (...)`, `hybridValidateAsXML (...)`, `hybridValidateAsHtml (...)` |

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

# Configuration

Everything but the base URL has a default:

```java
PhormClient.builder ()
           .baseURL ("https://phorm.example.org")   // mandatory
           .token ("...")                           // the X-Token header value
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
