# Lightweight data model for phorm-client

Status: **superseded in part**, 2026-09-28. S1 (ph-schematron) and S3 (ddd) are implemented and
released as ph-schematron 10.2.0 and ddd 0.9.0. S2 was re-assessed and replaced by a package
extraction - see `../../phive/docs/reduce-dependency-surface.md`. The analysis below is kept for
the measurements and the reasoning; `design-notes.md` records what was actually built.

The question this answers: `phorm-client` wants to hand back *typed* results —
phive's `ValidationResultList` and ddd's `DocumentDetails` — but both libraries
drag a validation *engine* behind them, and a REST client never validates
anything locally. Can the data model be separated from the engine?

Short answer: yes, and almost all of the weight comes from three named places,
each of which is a one-class or one-method coupling.

## 1. Measured footprint

Resolved with `mvn dependency:build-classpath` against the local repository,
summing the resulting JARs. Basis: `ph-httpclient` 11.4.6, `ph-json` /
`ph-xml` 12.5.0, `phive-result` 12.1.0, `ddd` 0.8.10.

| Configuration | JARs | Size |
|---|---:|---:|
| Thin core — `ph-httpclient` + `ph-json` + `ph-xml` | 23 | 4.9 MB |
| Typed, **today** — thin core + `ddd` + `phive-result` | 55 | 13.6 MB |
| Typed, **after the splits below** | 35 | 5.7 MB |

So the typed model costs **+32 JARs / +8.7 MB** today, and would cost
**+12 JARs / +0.8 MB** afterwards. That is ~90 % of the extra weight removed,
and it makes the "thin core vs. typed model" trade-off largely disappear.

The single biggest item is `net.sf.saxon:Saxon-HE:12.10` at 5.5 MB, plus its
`org.xmlresolver:xmlresolver` pair.

## 2. Where the weight actually comes from

### CP-1 — `phive-api` pulls Saxon for one enum

`phive-api` has 38 source files. Exactly one of them references Schematron:

```
phive-api/src/main/java/com/helger/phive/api/EValidationType.java:25
    import com.helger.schematron.ESchematronEngine;
```

That import makes `phive-api` depend on `ph-schematron-api`, which declares
`Saxon-HE` as a hard compile dependency ("Saxon is required!", see
`ph-schematron-api/pom.xml`). `ESchematronEngine` itself is a plain
`IHasID<String>` enum — it contains no Saxon code whatsoever.

**Cost: 5.5 MB + xmlresolver + `ph-xsds-xml` + `ph-telemetry`, for one enum.**

### CP-2 — `phive-result` pulls the five Schematron engines for one method

Exactly one file in `phive-result` references `phive-xml`:

```
phive-result/src/main/java/com/helger/phive/result/PhiveResultHelper.java:44-46
    import com.helger.phive.xml.source.IValidationSourceXML;
    import com.helger.phive.xml.source.ValidationSourceXML;
    import com.helger.xml.serialize.read.DOMReader;
```

All three are used in a single `switch` branch of `createValidationSource`
(line 225 ff.) — the branch that restores an XML validation source from the
Base64 payload. `phive-xml` in turn pulls `ph-schematron-xslt`, `-isosch`,
`-schxslt`, `-schxslt2`, `-pure`, plus `schxslt` 1.10.1 and `schxslt2` 1.11.1.

**Cost: 9 JARs, for one method branch that a REST client never reaches** —
phorm calls `sourceToJsonDefault(false)`, so no payload is ever echoed back.

### CP-3 — the error model itself lives in the Saxon module

This is the one that makes "just exclude it" fail. `PhiveJsonHelper.getAsIError`
returns an `SVRLResourceError` whenever a JSON error item carries a `test`
field — i.e. for every ordinary Schematron finding:

```
phive-result/src/main/java/com/helger/phive/result/json/PhiveJsonHelper.java:308-316
    if (sTest != null)
      return new SVRLResourceError (...);
```

`com.helger.schematron.svrl.SVRLResourceError` lives in `ph-schematron-api`.
It is itself Saxon-free — it extends `SingleError` and needs only `ph-base`
and `ph-diagnostics` — but it sits in the module that carries Saxon.

Verified empirically: a probe compiled against `phive-result` with both
`phive-xml` and `ph-schematron-api` excluded links fine and parses the
envelope, then fails the moment a real finding is read:

```
java.lang.NoClassDefFoundError: com/helger/schematron/svrl/SVRLResourceError
    at PhiveJsonHelper.getAsIError(PhiveJsonHelper.java:309)
    at PhiveJsonHelper.getAsValidationResultList(PhiveJsonHelper.java:706)
```

**This rules out the "declare it optional and document the exclusions" route.**

### CP-4 — `ddd`'s model needs `edelivery-id`, not `peppol-id`

`ddd` is comparatively cheap (+14 JARs / +1.1 MB), and its weight is
`peppol-id` with the BDXR-SMP / XAdES / XMLDSig XSD artifacts behind it. But
the cut inside `ddd` is clean — only the determinator needs `peppol-id`:

| ddd class | needs |
|---|---|
| `DocumentDetails` | `IParticipantIdentifier`, `IDocumentTypeIdentifier`, `IProcessIdentifier` → **`edelivery-id`** |
| `DocumentDetailsJsonHelper` | `IIdentifierFactory` → **`edelivery-id`** |
| `DocumentDetailsXMLHelper` | `IIdentifierFactory` → **`edelivery-id`** |
| `DocumentDetailsDeterminator` | `PeppolIdentifierFactory`, `PeppolDocumentTypeIdentifierParts`, `PeppolIdentifierHelper` → **`peppol-id`** |

`DocumentDetailsJsonHelper.getAsDocumentDetails` takes the `IIdentifierFactory`
as a parameter, so a client can pass `SimpleIdentifierFactory` — which is in
`edelivery-id`.

**Cost of the coupling: 8 JARs / 0.7 MB.**

## 3. Proposed solutions

### S1 — split `ph-schematron-api` into a Saxon-free core and a Saxon module

Saxon usage inside `ph-schematron-api` is confined to three packages:

| Package | Files | Use Saxon |
|---|---:|---:|
| (root — incl. `ESchematronEngine`) | 14 | 0 |
| `svrl` (incl. `SVRLResourceError`) | 13 | 0 |
| `api/cache` | 3 | 0 |
| `resolve` | 2 | 0 |
| `config` | 1 | 0 |
| `api/xslt` | 5 | 1 |
| `api/telemetry` | 7 | 2 |
| `saxon` | 5 | 4 |

Move `saxon/`, the two Saxon-using `api/telemetry/` classes and
`api/xslt/SchematronXSLTValidator` into a new `ph-schematron-saxon`; leave the
rest — including `ESchematronEngine` and the whole `svrl` error model — in a
Saxon-free `ph-schematron-api`. The engine modules (`-xslt`, `-isosch`,
`-pure`, …) depend on the new module and stay unchanged for their users.

**Fixes CP-1 and CP-3 together. Removes ~5.5 MB. Highest payoff of the three.**

Cost: a module split in a widely used public library. Consumers who referenced
the moved classes must add one dependency. Softened by keeping the artifact
name `ph-schematron-api` for the Saxon-free part (so the common case — people
who only touch `svrl` or the interfaces — needs no POM change at all), and by
having the engine modules re-export the Saxon part transitively.

### S2 — move the XML source restorer from `phive-result` into `phive-xml`

`phive-result` already defines the extension point:
`com.helger.phive.result.IValidationSourceRestorer`, and
`getAsValidationResultList` already takes one as a parameter.

So: keep the binary branch of `PhiveResultHelper.createValidationSource` in
`phive-result`, move the `IValidationSourceXML` branch into `phive-xml` as an
`IValidationSourceRestorer` implementation, and drop the `phive-xml`
dependency from `phive-result`. Callers who need XML source restoration pass
the new restorer explicitly; everyone else gets a `phive-result` that no longer
drags five Schematron engines.

**Fixes CP-2. Removes 9 JARs (~0.5 MB on its own — the number is small only
because Saxon, removed by S1, dominates).** Cleanest of the three changes, and
the only one that is not a module split.

### S3 — split `ddd` into `ddd-model` and `ddd`

`ddd-model`: `DocumentDetails`, `DocumentDetailsJsonHelper`,
`DocumentDetailsXMLHelper`, `DDDVersion` → depends on `ph-xml`, `ph-json`,
`edelivery-id`.
`ddd`: the determinator, `model/`, `unwrap/`, the JAXB artifacts → depends on
`ddd-model` + `peppol-id` + `ph-jaxb`.

**Fixes CP-4. Removes 8 JARs / 0.7 MB.** Lowest payoff, lowest risk, and `ddd`
is pre-1.0 so the split is cheap to make now.

### S4 — phorm-client keeps its own minimal model (no upstream change)

Define ~6 small value types in `phorm-client` (a response wrapper, a per-layer
result, an error item with level / ID / location / text / test) and map the
JSON directly. Zero upstream coordination, ships today, 23 JARs / 4.9 MB.

Downside: a second error model in the ecosystem that has to track
`CPhiveJson` by hand.

### S5 — optional dependencies plus documented exclusions

**Rejected.** CP-3 shows this fails at runtime on the normal Schematron path,
and it fails late — after a successful build and a successful HTTP call.

## 4. Recommendation

Do not let a new client library block on upstream releases, and do not give up
the typed model either:

1. **Now** — ship `phorm-client` with the S4 core as the always-available
   surface, and put the phive/ddd converters behind `<optional>true</optional>`
   dependencies. Consumers who already have phive on the classpath (anyone
   validating locally with `phive-rules`) get `ValidationResultList` and
   `DocumentDetails` at no extra cost; everyone else pays 4.9 MB.
   This is the same `<optional>` pattern `ph-httpclient` itself uses for
   `ph-json`, `ph-xml` and `ph-config`.
2. **Next, upstream, in this order** — S1 (−5.5 MB, and it is the one that
   unblocks the error model), then S2, then S3.
3. **After those release** — make the typed converters the default surface of
   `phorm-client`. The typed model then costs +12 JARs / +0.8 MB over the thin
   core, which is not a trade-off worth offering a switch for.

## 5. Open points

- S1 is a breaking module split in `ph-schematron`, which has the widest reach
  of the three. Whether that is acceptable is a release-policy call, not a
  technical one.
- The measurement used `ddd` 0.8.10's own transitive `peppol-id` 12.5.3.
  `phorm` pins `peppol-commons` 13.0.0 via BOM import; the JAR count is
  unaffected, the sizes shift slightly.
- Whether `SVRLMarshaller` / `CSVRL` (the only JAXB users in `svrl/`) should
  also move out of the Saxon-free core is worth deciding during S1. They cost
  `ph-jaxb` + `ph-xsds-xml`, which is minor next to Saxon.
