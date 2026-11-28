# phorm-client design notes

Status: implemented, 2026-09-28, v1.0.0-SNAPSHOT.

## What drove the design

### An HTTP 400 can mean two completely different things

This is the reason the library exists rather than being three lines of `HttpClient`.

With the server side default `phorm.api.response.onfailure.http400=true`, phorm answers a
*content wise invalid* document with HTTP 400 **and a complete, regular result body**. It also
answers a genuinely broken request with HTTP 400 and a `text/plain` message
(`Failed to read the message body as XML`, `The VESID '...' could not be resolved.`).

`ph-httpclient`'s stock response handlers throw `ExtendedHttpResponseException` on any status
&ge; 300, so the naive integration loses every validation finding of the first case.

The library therefore uses its own `HttpClientResponseHandler` that never fails on the status code,
and decides afterwards:

| Situation | Result |
|---|---|
| 2xx | regular result |
| 400 with the body in the requested representation | regular result, `isSuccess()` is `false` |
| 400 with any other content type | `PhormClientException` carrying the body |
| 403 | `PhormClientException` - the `X-Token` was missing or wrong |
| 204 on `determinedoctype` | `null` - the type could not be determined |
| 404 on `get/version` | `null` - phorm predates the version API |
| 404 on an admin API | `PhormClientException` - the admin APIs are disabled on the server |
| 500 with a JSON body on `admin/repositories/reload` | regular result, `isSuccess()` is `false` |
| anything else | `PhormClientException` carrying the body |

The check is representation aware, because the same duality applies to the XML and HTML variants.

### JSON internally

All three representations are exposed, but the typed path always requests JSON. It is the most
compact of the three, and it is the only one that `PhiveJsonHelper` and `DocumentDetailsJsonHelper`
can convert back into the phive and ddd object models. The `...AsXML` and `...AsHtml` variants pass
the server rendered representation straight through.

### Three failure modes, not one

`PhormClientException` carries an `EPhormErrorType`. The split exists because a consumer that has
to decide between *deferring* a document and *rejecting* it cannot treat "phorm is down" like
"phorm refused this payload" - a verification service that is merely unavailable must never turn
into a rejected document. phoss AP's `PhormDocumentVerifier` models exactly these three states
internally, and that is where the shape comes from.

| Type | Condition | Consumer reaction |
|---|---|---|
| `REQUEST_ERROR` | anything detected before sending, plus HTTP 4xx incl. 403 | fix the call or the configuration; retrying is pointless |
| `SERVICE_UNAVAILABLE` | transport failure, HTTP 5xx | retry later |
| `RESPONSE_ERROR` | usable status, unusable body | inspect `getResponse ()` |

A rejected `X-Token` is deliberately a `REQUEST_ERROR` and not a `RESPONSE_ERROR`: phorm answered
correctly, the caller is misconfigured.

### Streamed payloads, not materialized ones

The payload overloads take an `IHasInputStream` rather than an `IReadableResource`. Two reasons:

* it is the wider type - `IReadableResource` is one - so nothing is lost, and a caller whose
  document lives behind a store API (a filesystem or an S3 bucket reached through its own
  abstraction) can supply one with `HasInputStream.multiple (...)` without first reading the whole
  document into a `byte []`;
* `isReadMultiple ()` carries exactly the information Apache HttpClient needs: a payload that can be
  re-read becomes an `EntityTemplate` and is therefore *repeatable*, so a request may be replayed
  after a connection level failure. A read-once payload becomes an `InputStreamEntity`, which
  honestly reports itself as non-repeatable rather than replaying a consumed stream.

The `...AsXML` and `...AsHtml` variants stay `byte []` only - they exist for rendering a report, not
for pushing large documents.

### The hybrid country is not a closed set

`EPhormHybridCountry` matches what phorm accepts today, but the server logs a warning and falls back
to its default for anything else - it does not reject. Modelling the parameter as an enum only would
make *this library* the artifact that has to be released when phorm learns a new country. So every
hybrid method also takes a plain `String`, checked against `[0-9A-Za-z_-]{1,20}` so that nothing
unexpected reaches the URL, and otherwise passed through untouched. The same overload pair already
exists for the VESID (`DVRCoordinate` and `String`), so the shape is not new.

### Typed models rather than an own DTO layer

`PhormValidationResult` hands back phive's `ValidationResultList` and ddd's `DocumentDetails`
instead of re-modelling them. Two consequences worth knowing:

* `getAllErrors()` follows the phive semantics - level `ERROR` and above only. Warnings need
  `getAllFailures()`. Both are exposed, because the difference is easy to trip over.
* Every result also carries `getAsJson()` and `getRawResponse()`, so nothing the typed model drops
  is out of reach.

### The admin APIs have their own token

phorm checks the `X-Token` header of `/api/admin/*` against `phorm.api.admin.token`, a different
value than `phorm.api.requiredtoken`. The client therefore holds a second, optional token
(`Builder.adminToken`) and sends it to the admin APIs only - the validation token never reaches
them and the admin token never reaches the validation APIs. Without an admin token an admin call is
refused with a `REQUEST_ERROR` before anything is sent, because phorm would reject it anyway.

phorm registers the admin APIs only when the admin token is configured on its side, so a 404 from
an admin path is reported as "admin APIs disabled" rather than as an unexpected response.

### A failed reload is a result, not an error

A failed repository reload is answered with HTTP 500 and a regular body (`success:false` plus an
`errors` array), and phorm keeps the previously loaded VES active. That is the same situation as an
invalid document: the server did its job and reports a negative outcome. Turning it into a
`SERVICE_UNAVAILABLE` exception would suggest that retrying helps, when the cause is almost always a
broken repository configuration. So `reloadAdminRepositories` treats a 500 with a JSON object body
as a regular `PhormRepositoryReloadResult`; any other 500 is still a `SERVICE_UNAVAILABLE`.

### Only major and minor version

`getServerVersion ()` is the client for the version API added in phorm 2.2.18
([phorm #25](https://github.com/phax/phorm/issues/25)). It exists for feature detection, and features
only change with a minor version, so phorm reports `major` and `minor` only: the micro version of a
publicly reachable instance would tell an attacker which fixes are missing. `PhormServerVersion` is
therefore a plain value with `isAtLeast (major, minor)` and `Comparable`, not a full version model.
The API also offers `text/plain` (`2.3`) for scripts and monitoring; `getServerVersionAsText ()`
passes it through like the `...AsXML` and `...AsHtml` variants do, using the new
`EPhormResponseFormat.TEXT`.
A phorm that predates the API answers 404, which maps to `null` rather than an exception, because
"older than the version API" is a valid answer to the question.

## Dependency state

The library depends on `ph-httpclient`, `ph-json`, `ph-xml`, `phive-result` and `ddd-model`.

`ddd-model` (from the ddd 0.9.0 split) brings only `edelivery-id`, so **`peppol-id` and its
BDXR-SMP / XAdES / XMLDSig datatypes are not on the classpath**. `ph-schematron-api` 10.2.0 is
Saxon-free, so `phive-api` no longer drags Saxon either.

What remains today:

```
phive-result -> phive-xml -> ph-schematron-pure -> Saxon-HE (5.5 MB)
```

Measured: **55 JARs / 13.5 MB**. Once phive extracts `com.helger.phive.xml.source` into its own
module (see `../../phive/docs/reduce-dependency-surface.md`), `phive-result` stops pulling the
Schematron engines and this drops to roughly **34 JARs / 5.6 MB** - with no change to this library
beyond the phive version.

## API shape

One class per concern, thirteen in total:

| Class | Role |
|---|---|
| `PhormClient` | the client, built through `PhormClient.builder ()`, `AutoCloseable` |
| `PhormValidationResult` | typed result of the three validating APIs |
| `PhormVESID` | one entry of `GET /api/get/vesids`, including the external repository it came from |
| `PhormServerVersion` | major and minor version from `GET /api/get/version` |
| `PhormRepositoryStatus` | result of `GET /api/admin/repositories` |
| `PhormRepository` | one external repository with its VES count |
| `PhormRepositoryReloadResult` | result of `POST /api/admin/repositories/reload` |
| `PhormRawResponse` | status code, content type, unparsed body - the escape hatch |
| `PhormClientException` | everything that prevented a result, carrying the raw response |
| `EPhormResponseFormat` | JSON / XML / HTML / plain text, mapped to the `Accept` header |
| `EPhormHybridCountry` | `DE` / `FR` / `OTHER` for the hybrid PDF API, so kaltblut is not needed |
| `EPhormErrorType` | why a call did not yield a result - faulty request, unavailable service, unusable answer |
| `CPhormClient` | header name, API paths, query and JSON field names |

`PhormValidationResult.createFromResponse (PhormRawResponse, IIdentifierFactory)` is public so the
conversion can be tested, and used, without an HTTP round trip - `PhormValidationResultTest` covers
the full phive and ddd deserialization against realistic phorm responses that way.

## Deliberately left out

* No retry or circuit breaking - configure it on the `HttpClientSettings` instead.
* No async API. phorm calls are single request / single response.
* No own error model. The phive `IError` list is the error model.
* No `kaltblut` dependency for the hybrid country; a three constant enum covers the API, and the
  `String` overload covers everything phorm may add later.
