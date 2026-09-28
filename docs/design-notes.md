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
| anything else | `PhormClientException` carrying the body |

The check is representation aware, because the same duality applies to the XML and HTML variants.

### JSON internally

All three representations are exposed, but the typed path always requests JSON. It is the most
compact of the three, and it is the only one that `PhiveJsonHelper` and `DocumentDetailsJsonHelper`
can convert back into the phive and ddd object models. The `...AsXML` and `...AsHtml` variants pass
the server rendered representation straight through.

### Typed models rather than an own DTO layer

`PhormValidationResult` hands back phive's `ValidationResultList` and ddd's `DocumentDetails`
instead of re-modelling them. Two consequences worth knowing:

* `getAllErrors()` follows the phive semantics - level `ERROR` and above only. Warnings need
  `getAllFailures()`. Both are exposed, because the difference is easy to trip over.
* Every result also carries `getAsJson()` and `getRawResponse()`, so nothing the typed model drops
  is out of reach.

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

One class per concern, eight in total:

| Class | Role |
|---|---|
| `PhormClient` | the client, built through `PhormClient.builder ()`, `AutoCloseable` |
| `PhormValidationResult` | typed result of the three validating APIs |
| `PhormVESID` | one entry of `GET /api/get/vesids` |
| `PhormRawResponse` | status code, content type, unparsed body - the escape hatch |
| `PhormClientException` | everything that prevented a result, carrying the raw response |
| `EPhormResponseFormat` | JSON / XML / HTML, mapped to the `Accept` header |
| `EPhormHybridCountry` | `DE` / `FR` / `OTHER` for the hybrid PDF API, so kaltblut is not needed |
| `CPhormClient` | header name, API paths, query and JSON field names |

`PhormValidationResult.createFromResponse (PhormRawResponse, IIdentifierFactory)` is public so the
conversion can be tested, and used, without an HTTP round trip - `PhormValidationResultTest` covers
the full phive and ddd deserialization against realistic phorm responses that way.

## Deliberately left out

* No retry or circuit breaking - configure it on the `HttpClientSettings` instead.
* No async API. phorm calls are single request / single response.
* No own error model. The phive `IError` list is the error model.
* No `kaltblut` dependency for the hybrid country; a three constant enum covers the API.
