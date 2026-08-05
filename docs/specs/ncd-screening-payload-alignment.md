# Diabetes & Hypertension Screening — App vs Server Payload Alignment

Aligning the three documents the ADDO app produces at the end of a screening-with-referral
submission with the ones the downstream pipeline is already known to accept.

**Scope:** `Diabetes and Hypertension Screening` event, `Referral Registration` event, referral `Task`.
**Reference:** server documents captured 2026-08-03, plus
`opensrp-client-chw/opensrp-chw/src/nacp/` as the authority for form content and referral wiring.
**App baseline:** output captured 2026-07-31.
**Library:** `opensrp-client-core:1.8.31-DTREE-9-ALPHA-SNAPSHOT` (facts below verified against the
Gradle-cache AAR with `javap`, not from memory).

---

## Status

| | |
|---|---|
| Implemented | C1, C2, C5, C6, C10, C11, C12, C13, C14, C15, C16, C18 |
| Retracted | C3, C4 (form authority is CHW), C7, C8, C9 (reference says otherwise) |
| Closed, won't fix | C17 |
| Open | nothing in the change list — only device verification remains |

Commits: `a25a9368` (payload alignment), `2b4fc09f` (form alignment), `0e3c9295` (C16).
`:app:compileMohDebugJavaWithJavac` is green.

**C16 is the one that mattered operationally.** Both events shared a `formSubmissionId`, and
`EventClientRepository.addEvent` treats that column as an update key — so the referral event
updated the screening event's row instead of inserting alongside it, and only one event survived.
Symptom: a Referral Registration event with no Diabetes and Hypertension Screening event.

### Remaining verification

Everything below needs one Save-and-Refer submission on a device set to `Africa/Dar_es_Salaam`.
The timezone matters: a UTC device hides item 4.

1. **Two event rows, not one** — `SELECT eventType, formSubmissionId FROM event WHERE baseEntityId = '<id>'`
2. **A screening event on the plain Save path too** (C18) — repeat the submission without referring,
   and on a declined screening; both must produce a `Diabetes and Hypertension Screening` row
3. `start` / `end` obs present on the screening event (C2)
4. `service_before_referral` obs shape — **expected to still differ**, see §2.4
5. Timestamps carrying `+03:00` rather than a mislabelled `Z` (C11)

Then diff obs-by-obs on `fieldCode | fieldType | fieldDataType | parentCode | values |
humanReadableValues` against the server reference.

---

## 1. Screening event

### 1.1 `entityType` was `ec_ncd_register` — fixed (C1)
Server carries `"entityType": "Diabetes and Hypertension Screening"` — identical to `eventType`.
The app hardcoded `"ec_ncd_register"`, which appeared nowhere else in the repo and was not
registered in `assets/ec_client_classification.json`, so it had no local consumer.

### 1.2 `start` / `end` obs were never emitted — fixed (C2)
Server carries two obs the app had none of:
- `163137AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA` / `formSubmissionField: start` / `fieldDataType: start`
- `163138AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA` / `formSubmissionField: end` / `fieldDataType: end`

Root cause: both submit methods built `JSONObject metadata = new JSONObject()` — empty — and
passed it to `createEvent`, which feeds it to `createFormMetadataObs`. The form defines these
under its own `metadata` block; they were simply never handed over. Passing the real block also
sets `eventDate` from the form's encounter date, which is why the reference `eventDate` is local
midnight rather than the submit timestamp.

Applies to the screening event **only** — the reference referral event has no `start`/`end` obs.

### 1.3 `medicines_diabetes` / `medicines_hypertension` — NOT a gap
The server sample carries these two obs and the app does not. This looked like a missing pair of
form fields; it is not. The CHW reference form has no such fields, so those obs were produced by
older code. C3 added them and was reverted in full. **Do not re-add them.**

### 1.4 `service_before_referral` shape diverges — unresolved, not a form problem

| | Server | App |
|---|---|---|
| `fieldType` | `concept` | `formsubmissionField` |
| `fieldDataType` | `check_box` | `text` |
| `values` | `["no"]` (option key) | `["No"]` (option label) |
| `humanReadableValues` | `["no"]` | `[]` |

C4 attempted to fix this by adding `openmrs_data_type: check_box` and setting each option's
`openmrs_entity` to `concept`. It was reverted: the CHW form — which produces the accepted
payload — has neither. The addo form is now identical to CHW's for this field, so the difference
originates downstream, most likely the MLS native-form fork
(`opensrp-client-native-form:1.7.27-DTREE-MLS-6-SNAPSHOT`) writing the translated option *text*
where the CHW build writes the option *key*.

**Do not try to fix this in the form.** Capture it on a device first.

### 1.5 Extra obs — resolved
`is_emergency_case`, `has_treatment_supporter`, `treatment_supporter_name`,
`treatment_supporter_phone`, `treatment_supporter_relationship` are absent from the server sample.
CHW's `AllClientsMemberProfileActivity.createReferralForm` includes all of them, so they are
expected, not stray. No change needed.

Still unconfirmed for `reasons_for_declining`, `other_reason_for_declining` and
`service_provided_details`, which are in the form but were unset in the captured sample.

### 1.6 `obs[].set` and `obs[].saveObsAsArray` absent — not app-fixable
Every server obs carries `"set": []` and `"saveObsAsArray": false`. The `Obs` class in client-core
1.8.31 has neither field — `javap` shows only `fieldType, fieldDataType, fieldCode, parentCode,
values, humanReadableValues, comments, formSubmissionField`. No app-level change can emit them.
Either the server fills defaults on ingestion, or client-core has to be bumped. See Q3.

### 1.7 Timestamp offset — fixed (C11)
Server: `"dateCreated": "2026-06-08T16:36:36.007+03:00"`. App was emitting `…347Z`.

The Gson builder used `setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")`, where `Z` is a *literal
character* and the underlying `SimpleDateFormat` renders in the device's default timezone. On a
UTC device the output is accidentally correct; on an EAT device it stamps local 16:43 and labels
it `Z` — a silent three-hour skew for any date-partitioned pipeline.

Replaced with a joda `Date` serializer emitting `ISODateTimeFormat.dateTime()`. Note
`SimpleDateFormat`'s `XXX` pattern is **not** usable here: it needs API 24 and this app's
`minSdkVersion` is 21.

### 1.8 Informational, no action
- `identifiers: {}` present on server, key absent in app output.
- `clientDatabaseVersion` 27 vs 37, `clientApplicationVersion` 3 vs 40 — expected drift.
- Obs ordering differs; irrelevant if the pipeline keys on `fieldCode`.

---

## 2. Referral Registration event

`entityType: "ec_referral"` and `eventType: "Referral Registration"` already matched.

### 2.1 `problem` obs was missing entirely — fixed (C12)
The reference carries an obs the app never emitted:

```json
{ "fieldCode": "concept", "fieldType": "", "parentCode": "problem",
  "formSubmissionField": "problem",
  "values": ["risk_for_diabetes_and_hypertension"],
  "humanReadableValues": ["Risk for diabetes and hypertension"] }
```

The shape is unusual and deliberate: `fieldCode` is the literal string `"concept"`, `fieldType` is
empty, `parentCode` is `"problem"`, and there is no `fieldDataType`. Two independent CHW sources
confirm it — `AllClientsMemberProfileActivity.createReferralForm` builds it as
`metaData("", "concept", "problem")`, and `NcdReferralTaskHelper` constructs the same `Obs`
explicitly. `fieldDataType` must be left null so Gson omits the key.

The app already had the pattern on the danger-signs path (`AddoVisitInteractor.java:262`); the
diabetes/hypertension path never got it. Note `createObsFromValues` produces a *different* shape
(`fieldCode="problem"`, `parentCode=""`), so the `Obs` is constructed explicitly.

### 2.2 Java object `toString()` leaking into obs values — fixed (C5)
Two obs shipped a stringified Java object instead of data:

```
"diabetes_risk_score":     "NFormViewData(type=Calculation, value=0.124…, metadata=null, visible=true)"
"service_before_referral": "NFormViewData(type=MultiChoiceCheckBox, value={no=NFormViewData(…)}, …)"
```

`createReferralForm` passed an `NFormViewData` instance as a `JSONObject` value, and `org.json`
stringifies unknown types with `toString()`.

**This was a half-finished port, not stray code.** CHW builds a `HashMap<String, NFormViewData>`
and hands it to `IssueReferralInteractor.saveRegistration`, which converts it to obs inside the
referral library — the metadata on each `NFormViewData` is what becomes `fieldType` / `fieldCode` /
`parentCode`. The addo code copied `createReferralForm` but fed the result into
`JsonFormUtils.createEvent`, which expects json-wizard fields. Writing plain values is correct for
the path addo actually uses; **the fuller port would be to call the referral library the way CHW
does**, and is worth considering if this area is revisited.

### 2.3 `service_before_referral` emitted twice — fixed (C6)
`step4` already carries the field; `createReferralForm` appended a second synthetic one. The
reference has exactly one.

### 2.4 `chw_referral_service` casing — fixed (C13)
Reference: `"Diabetes And Hypertension Screening"`, capital **A**. The app sent lowercase "and"
while the task's `focus` already used the capital-A form, so the app contradicted itself.

### 2.5 `chw_referral_hf` had no human-readable value — fixed (C14)
Reference carries the facility name (`["Hai - 101295-4"]`); the app sent `[]`. CHW achieves this
by putting the name in the `NFormViewData` value and the location id in its metadata. The addo fix
stamps the name onto the obs after event construction, resolving it from
`Utils.getWardFacilities()`.

Applied to the referral event only — the reference screening event has an empty HRV here.

### 2.6 `diabetes_risk_score_output` should not be on this event — fixed (C15)
The reference carries only `diabetes_risk_score`; the app emitted both. CHW's referral form data
likewise contains only `diabetes_risk_score`.

### 2.7 Shared `formSubmissionId` — fixed (C16), and this was the blocker

The reference gives the screening event `9544b1fe-…` and the referral event `5ce1ae02-…`, with the
task's `reasonReference` pointing at the referral one.

The app built **one** `FormTag` and reused it for the screening event, the referral event and the
task's `reasonReference`. `createEvent` reads `FormTag.formSubmissionId` and only generates a UUID
when it is blank, so both events shipped the same id.

The consequence is local, not server-side. `EventClientRepository.addEvent`:

1. reads `formSubmissionId` from the event JSON
2. calls `checkIfExistsByFormSubmissionId(Table.event, id)`
3. on a hit → `db.update(event, values, "formSubmissionId=?", [id])`
4. on a miss → `db.insert(...)`

The screening event inserted; the referral event, submitted second, found the row and overwrote
it — `eventType` and all. **One row survived, holding the last write.**

The fix mirrors CHW, which documents this exact trap in `NcdReferralTaskHelper`'s javadoc — the
triggering form's id is "optional context, *not used for reasonReference*". Two distinct
`FormTag`s now; the task's `reasonReference` takes the referral event's id, matching
`ReferralUtil.createReferralTask -> task.setReasonReference(event.getFormSubmissionId())`.

The referral event is also now submitted **before** the task, the order CHW uses
(`BaseIssueReferralInteractor` → `Util.processEvent` → `ReferralUtil.createReferralTask`).
CHW goes further and aborts task creation when the event fails to persist; addo's
`submitReferralEvent` returns `void` and swallows its exception, so that gate is **not** in place.
Worth adding — it would prevent an orphan task.

### 2.8 `referral_type` — closed, won't fix
Reference: `"community_to_facility_referral"`. App: `"addo_to_facility_referral"`.

**This divergence is intentional and correct.** The referral genuinely originates at an ADDO
rather than in the community, so the distinct value carries meaning. No code change (2026-08-03).

### 2.10 Screening measurements were absent from the referral event — fixed (C17)

The reference Referral Registration event carries the clinical evidence behind the referral:

| Reference obs | Value in reference | ADDO form key (step3) |
|---|---|---|
| `family_history_of_dm` | `"No"` | `family_history_diabetes` |
| `waist_circumference` | `"40"` | `waist_circumference` |
| `systolic` | `"160"` | `systolic_bp` |
| `diastolic` | `"98"` | `diastolic_bp` |

The app emitted **none** of them, so a receiving facility saw the `problem` code and
`diabetes_risk_score` with nothing underneath.

The cause is structural rather than an omitted line. `createReferralForm` seeds its obs array from
`form.getJSONObject("step4").getJSONArray("fields")`, but all four measurements are captured in
**step3** — they are unreachable from that array. `diabetes_risk_score` only survives because
§2.6's fix pulls it in by hand.

CHW never hits this because `HpsMemberProfileActivity.createReferralForm` builds a
`HashMap<String, NFormViewData>` and pulls each key individually via
`JsonFormUtils.getValue(wholeForm, key)`, which searches every step.

The addo fix keeps the existing array-based approach and adds four explicit pulls, mirroring CHW's
per-field non-blank guards. **The rename matters**: the form's `systolic_bp` / `diastolic_bp` /
`family_history_diabetes` keys are not the concept ids the server expects — the obs must go out as
`systolic`, `diastolic`, `family_history_of_dm`. `waist_circumference` is the one key that is
already correct.

Blank values are skipped rather than emitted empty (`putReferralFieldIfNotBlank`); an obs carrying
`""` is worse than an absent one for the receiving facility. Consistent with C7, these synthetic
obs carry no `openmrs_entity_parent`.

Note the four fields are added to the referral event only. They remain on the screening event
through their own step3 field definitions — this is duplication by design, matching the reference,
because the referral has to stand alone.

### 2.9 Retracted proposals
Three first-pass proposals were made before the real Referral Registration reference arrived, and
are wrong. Recorded so they are not re-attempted:

- **C7 — do not add `openmrs_entity_parent`.** The reference's synthetic obs (`diabetes_risk_score`,
  `referral_status`, `chw_referral_service`, `referral_date`, `referral_type`, `referral_time`)
  carry *only* `set`, `values`, `saveObsAsArray`, `formSubmissionField` — no `parentCode`. This is
  a consequence of their `NFormViewData` metadata being null on the CHW side.
- **C8 — keep `convertAppointmentDate()`.** `referral_appointment_date` on the reference is
  `"1784062800000"`, epoch millis as a string. CHW does the same conversion.
- **C9 — do not stringify `referral_date`.** The reference carries a raw JSON number
  (`1784122337748`). CHW passes `System.currentTimeMillis()` directly.

---

## 3. Referral Task

Already aligned: `planIdentifier` (`5270285b-…`, matches `CoreConstants.REFERRAL_PLAN_ID_2`),
`code` (`Referral`), `focus`, `description`, `businessStatus` (`Referred`), `groupIdentifier` =
selected facility id, `location` = ward/village OpenMRS id, `owner`/`requester`,
`authoredOn`/`lastModified`, `executionPeriod.start` set with no `end`, `syncStatus` = `Created`.
`reasonReference` now points at the referral event's `formSubmissionId` (C16).

### 3.1 `priority` — fixed (C10)
Server: `"priority": "routine"`. App sent `3`. `Task.priority` is a plain `int` in client-core
1.8.31 (no `TaskPriority` enum) and `taskGson` registers only a `DateTime` adapter, so the int
went over the wire literally. In the FHIR ordering used by `createFollowUpTask` in the same file,
`routine` is `1` and `3` is `asap`. Changed to `1`.

### 3.2 `status` — verify, do not "fix"
Server holds `"Ready"`; the app's `TaskStatus.READY` enum serialises to `"READY"`. Because
`taskGson` has no enum adapter the app **cannot** emit `"Ready"` without a library change, so the
server almost certainly normalises case on ingestion. See Q4.

---

## 4. Form parity with CHW

The addo form matches
`opensrp-client-chw/opensrp-chw/src/nacp/assets/json.form/diabetes_hypertension_screening_form.json`
field-for-field. Three deliberate divergences remain, all payload-neutral:

| Divergence | Why it stays |
|------------|--------------|
| `{{value_yes}}` / `{{value_no}}` instead of literal `Yes`/`No` | addo uses MLS properties files; `keys` and `openmrs_choice_ids` are unchanged, so the submitted value is identical |
| rules-engine relevance instead of CHW's inline `equalTo(., "No")` on `reasons_for_declining` and the four step2 cascade fields | under MLS the compared value is the *translated* string; the addo yml tests both `'No'` and `'Hapana'`, so the inline form would silently fail in Swahili |
| `chw_referral_hf.exclusive: []` | `displayReferralFacilities()` → `AddoUtils.requireFieldArray(field, "exclusive")` populates it; removing it breaks facility population |

### Translations
20 text differences against the CHW en/sw forms, all display-only, none payload-affecting. Four
Swahili strings were adopted from CHW verbatim: `step1_other_reason_for_declining_err`,
`step2_diagnosed_diabetes_hint_err`, `step2_diagnosed_hypertension_hint_err`,
`step3_diastolic_lt_systolic_err`.

The rest were deliberately **not** imported, because CHW is the weaker source there:

- Eight `v_required.err` strings in CHW read "Please select whether you agree with the screening"
  on fields unrelated to consent, and `waist_circumference.v_numeric.err` reads "Please enter the
  temperature" — copy-paste errors. addo already has correct text.
- CHW's *Swahili* form leaves `diabetes_risk_score_output.hint` as the English "Patient risk".
- Long info texts differ only by em-dash vs hyphen and curly vs straight apostrophe. addo keeps
  ASCII, which is correct for a `.properties` file read as ISO-8859-1.

Two suspected typos were adopted verbatim rather than silently corrected: `"unakisukari"` (likely
`una kisukari`) and `"linatikiwa"` (likely `linatakiwa`). Worth fixing in both repos.

---

## 5. Secondary observations

**A second event per submission.** `onActivityResult` calls `submitForm(formSubmission)` in
addition to `submitDiabetesAndHypertensionScreeningEvent`. `submitForm` routes through
`FamilyOtherMemberProfileInteractor.saveVisit`, whose `getEncounterType()` returns
`OTHER_MEMBER_ADDO_VISIT` — so the entire screening JSON is also persisted and synced as a second
event under a different encounter type, with its own `formSubmissionId`. Not a duplicate screening
event, but it doubles the data reaching the server. See Q5.

**No screening event on the plain Save path — fixed (C18).**
`submitDiabetesAndHypertensionScreeningEvent` sat inside `if (!buttonAction.isEmpty())`, and
`buttonAction` is only ever set by `db_save_n_refer`. A client screened with no referral therefore
produced **no screening event at all**, so low-risk screenings and declined screenings were both
invisible to the pipeline — only referred clients generated one.

The submission is now hoisted above that branch and runs for every completion of the form,
whichever button finished it. Only the referral event and its task remain gated on
`db_save_n_refer`. The screening event keeps its own `FormTag`, so C16 still holds on the referral
path; on the plain Save path only the screening tag is created.

Note the `else` branch still calls `checkDSPresentProposedMedsAndDispense(form)`, which is the
**danger-signs** flow and has no fields in common with this form. It is harmless today only
because `isClientPresent()` is entirely commented out and returns `false` unconditionally, so the
method always falls through to `dispenseMedication(null, null, null)`. Whether a dispense prompt
belongs at the end of a low-risk screening is a product question, untouched here.

---

## 6. Open questions for the pipeline owner

Not code changes — these need an answer from whoever owns the downstream pipeline.

| | Question | Status |
|---|---|---|
| Q2 | Does the pipeline tolerate unknown `fieldCode`s? (`reasons_for_declining`, `other_reason_for_declining`, `service_provided_details`) | Partly answered — CHW emits `is_emergency_case` and the treatment-supporter fields, so those are fine |
| Q3 | Are `obs.set` / `obs.saveObsAsArray` required on ingestion? If yes, client-core must be bumped | Open |
| Q4 | Does the server normalise `"READY"` → `"Ready"`? | Open |
| Q5 | Is the extra `OTHER_MEMBER_ADDO_VISIT` event wanted? | Open |
| Q6 | Which `referral_type` for ADDO referrals? | **Answered 2026-08-03** — `addo_to_facility_referral`, intentionally distinct |
| Q7 | Must `formSubmissionId` be unique per event? | **Answered** — yes, and the constraint is local: `EventClientRepository.addEvent` uses it as an update key |
