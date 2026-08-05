# SPEC — Port CHW NCD screening changes into ADDO

**Source of truth:** `opensrp-client-chw` @ `feat/ncd-auto-referral-emergency-supporter`
**Target:** `opensrp-client-addo` @ `ncd-addo`
**Status:** Executed 2026-07-30 (code complete; device verification V1–V8 and Phase 6 test outstanding — see §11)
**Author:** generated 2026-07-30

---

## 1. Background

ADDO's `diabetes_hypertension_screening_form.json` was forked from CHW's NACP form and both
sides have since moved. CHW removed the medication questions, added a cascading step-2 gate,
inline BP guidance, a diastolic-below-systolic constraint, `is_emergency_case`,
`service_provided_details`, and a four-field treatment-supporter block. ADDO independently added
`medicines_diabetes` / `medicines_hypertension`, an extra toaster, and a `.properties`-based i18n
layer. Neither form is a superset of the other.

**Three things make this a port, not a copy:**

| # | Divergence | Consequence |
|---|---|---|
| **A** | **i18n mechanism differs.** CHW ships two parallel JSON files (`json.form/` EN + `json.form-sw/` SW) with literal strings. ADDO ships one JSON of `{{placeholders}}` plus `addo_diabetes_hypertension_screening_{en,sw}.properties`. | Every CHW string becomes a *properties key* in ADDO. CHW's SW JSON is the source for ADDO's `_sw.properties`. A literal JSON copy would hardcode English into a localized app. |
| **B** | **Referral pipeline differs.** CHW builds `HashMap<String, NFormViewData>` and posts via the referral library (`sendNCDReferralToFacility` → `referrals/referral_form`). ADDO builds a `JSONArray` of `createReferralFormField(...)` and calls `presenter().submitReferralEvent(...)` + `ReferralUtils.createReferralTask(...)`. | CHW's `TreatmentSupporterFormUtil.addScreeningReferralObs` cannot be dropped in. The obs must be re-expressed in ADDO's field shape. |
| **C** | **Rules YAML is shared across locales in both apps, but CHW's is locale-broken.** CHW's step-3 conditions read `(x == 'No' \|\| x == '')`. In CHW's own Swahili build the value is `'Hapana'`, matching neither branch — step 3 collapses. ADDO's current YAML already carries the dual literals. | **Do not copy CHW's YAML verbatim.** Port the *semantics* and keep dual EN/SW literals. See §4. |

### 1.1 Pre-existing defect in scope

`app/src/main/java/org/smartregister/addo/activity/FamilyOtherMemberProfileActivity.java:289`

```java
if (jo.getString("key").compareToIgnoreCase("save_n_refer") == 0) {
```

The screening form's button key is **`db_save_n_refer`**. `compareToIgnoreCase` never returns 0,
so `buttonAction` stays empty and **the screening form creates no referral task today**. CHW's
equivalent (`FamilyOtherMemberProfileActivity.java:513`) correctly reads `db_save_n_refer`.

This is fixed as part of this port (decision D2, §7). Every referral field ported below is inert
until it lands.

---

## 2. Scope

### In scope
- Field-level parity of `diabetes_hypertension_screening_form.json` with CHW's NACP form.
- `diabetes_hypertension_screening_relevance.yml` — semantic port.
- `_en` and `_sw` properties files — new and revised keys.
- Java: `is_emergency_case`, `service_provided_details`, and the four treatment-supporter fields
  emitted as referral obs; the `db_save_n_refer` fix.
- A config-parity regression test modelled on CHW's `NcdInitialScreeningConfigTest`.

### Out of scope (decided)
- **Caregiver prefill infrastructure** — `TreatmentSupporterDao`, a caregiver field on ADDO's
  registration form, and the backing DB column. ADDO has zero occurrences of
  `treatment_supporter` / `TreatmentSupporter` / caregiver-at-registration today. The four
  supporter fields ship as **manual entry**. (Decision D1, §7.)
- CHW's auto-referral prompt dialog (`NcdReferralPromptDialog`, `NcdReferralTaskHelper`), NCD
  follow-up forms, NCD register/profile activities, and the `opensrp-chw-ncd` dependency — none
  exist in ADDO.
- `FamilyFocusedMemberProfileActivity` — its `save_n_refer` lookup at line 731 serves the
  ANC/PNC/child danger-sign forms (step 3), not the screening form. Leave it alone.
- `diabetes_hypertension_screening_calculation.yml` — already byte-identical apart from a missing
  trailing newline.

---

## 3. Form delta — `app/src/main/assets/json.form/diabetes_hypertension_screening_form.json`

Retain `"properties_file_name": "addo_diabetes_hypertension_screening"`. All strings below are
expressed as `{{key}}`; the literal text goes in the properties files (§5).

### 3.1 step1 — Client consent

Structure already matches. **Text-only changes** (properties values, §5.1):

| Key | Change |
|---|---|
| `step1_consent_message` | Adopt CHW wording ("…free, and does not involve injections or drawing blood…") |
| `step1_reasons_for_declining_key_in_hurry` | → "They do not have time to participate at the moment." |
| `step1_reasons_for_declining_key_privacy_trust` | → "Fear that health information may be misused, or concern that the application records too much personal information" |
| `step1_reasons_for_declining_key_feel_healthy` | → "The client feels they are healthy and do not see the need for screening" |
| `step1_reasons_for_declining_key_fear_anxiety` | → "Worry about being diagnosed with a disease" |
| `step1_reasons_for_declining_key_other_reason` | → "Other, please specify." |

### 3.2 step2 — Self-reported Medical History

**Remove two fields entirely:**
- `medicines_diabetes`
- `medicines_hypertension`

**Replace flat gating with CHW's cascade.** Today every step-2 field is gated on
`step1:agree_with_screening_procedure == "Yes"`. Target — each question appears only when the
previous was answered **No**:

| Field | New `relevance` |
|---|---|
| `diagnosed_diabetes` | `rules-engine` → `diabetes_hypertension_screening_relevance.yml` (unchanged) |
| `screened_diabetes_12m` | `step2:diagnosed_diabetes` → `equalTo(., "No")` |
| `diagnosed_hypertension` | `step2:screened_diabetes_12m` → `equalTo(., "No")` |
| `screened_hypertension_last12m` | `step2:diagnosed_hypertension` → `equalTo(., "No")` |
| `routine_clinic_visits` | `step2:screened_hypertension_last12m` → `equalTo(., "No")` |

> ⚠️ **Localization risk.** `equalTo(., "No")` is a literal comparison inside the form JSON,
> and ADDO's spinner `values` are `{{value_no}}` → `"Hapana"` in the SW build. Verify on a
> Swahili device that the cascade advances; if it does not, move these four fields onto
> rules-engine relevance with dual literals (same pattern as §4). **This is the single highest
> regression risk in the port** — see V4 in §6.

Hint wording aligns to CHW ("Are you diagnosed with diabetes?", "Are you attending or enrolled
in a routine clinic for diabetes and high blood pressure follow-ups?") — §5.1.

### 3.3 step3 — Screening

| Field | Change |
|---|---|
| `family_history_diabetes` | Add `label_info_title` + `label_info_text` (first-degree-relative guidance). Hint → "Family history of diabetes?" |
| `systolic_bp` | Add `label_info_title` + `label_info_text` (how to measure: 5-min rest, cuff on bare skin, two readings averaged, thresholds 120/140). Fix `v_numeric.err` → "Please enter the systolic value" |
| `diastolic_bp` | Add `label_info_title` + `label_info_text`. Fix `v_numeric.err` → "Please enter the diastolic value". **Add `constraints`:** `lessThan(., step3:systolic_bp)` with err "The diastolic value must be less than the systolic value" |
| `waist_circumference` | Fix `v_numeric.err` "Please Number" → "Please enter the waist circumference" (CHW has "Please enter the temperature" — a copy-paste bug there; **do not port the typo**) |

Also fix two mislabelled existing keys: `step3_diastolic_bp_50_err` / `step3_diastolic_bp_320_err`
belong to **systolic** — rename to `step3_systolic_bp_50_err` / `step3_systolic_bp_320_err`, and
correct the 320 message text (currently says "less than 200").

### 3.4 step4 — Facility Referral

**Remove:** `prescreening_client_has_condition_toaster_not_using_meds` (exists only to serve the
removed medicines questions).

**Add, in CHW's order:**

| Field | Type | Notes |
|---|---|---|
| `is_emergency_case` | `spinner` Yes/No | `openmrs_data_type: select one`, required, rules-engine relevance |
| `service_provided_details` | `edit_text` | required; relevance `step4:service_before_referral` → `ex-checkbox` `or: ["yes"]` |
| `has_treatment_supporter` | `spinner` Yes/No | required, rules-engine relevance |
| `treatment_supporter_name` | `edit_text` | required, rules-engine relevance |
| `treatment_supporter_phone` | `edit_text`, `edit_type: number` | optional, rules-engine relevance |
| `treatment_supporter_relationship` | `spinner` | 18 options: Mother, Father, Brother, Sister, Grandfather, Grandmother, Friend, Uncle, Aunt, Police, Guardian, Son, Daughter, Work Colleague, Brother in Law, Sister in Law, Wife, Husband. Required, rules-engine relevance |

**Modify:**

| Field | Change |
|---|---|
| `diabetes_risk_score_output` | **Add the missing `relevance` rules-engine block.** Hint → "Patient risk" |
| `service_before_referral` | Replace the 4 options (`ors` / `panadol` / `other_treatment` / `chk_none`) with CHW's 2 (`yes` / `no`); `exclusive: ["no"]`; label → "Pre-referral management given." |
| `risk_present_toaster` | Text → "Initial screening suggests a potential risk for {condition}. …" |
| `prescreening_client_has_condition_toaster` | Text → "According to the information you have provided, you have already been diagnosed, recently screened, or enrolled in routine care… not eligible for another screening today…" |
| `chw_referral_hf` | ~~Drop the stray `"exclusive": []` (CHW has none)~~ **WITHDRAWN — see §11.5.** The array is not stray: `FamilyOtherMemberProfileActivity.displayReferralFacilities()` reads it with `getJSONArray("exclusive")` before populating `options`. Leave it in place. |
| `db_save_n_refer`, `save` | `openmrs_entity_id` → `165310AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA` (CHW's OpenMRS concept, both buttons) |

**Final step-4 field order (CHW):** `diabetes_risk_score_output`, `risk_present_toaster`,
`client_refused_assessment`, `prescreening_client_has_condition_toaster`,
`no_risk_present_toaster`, `is_emergency_case`, `service_before_referral`,
`service_provided_details`, `has_treatment_supporter`, `treatment_supporter_name`,
`treatment_supporter_phone`, `treatment_supporter_relationship`, `chw_referral_hf`,
`referral_appointment_date`, `spacer`, `db_save_n_refer`, `save`.

---

## 4. Rules delta — `app/src/main/assets/rule/diabetes_hypertension_screening_relevance.yml`

**Porting rule: semantics from CHW, dual EN/SW literals from ADDO.** Every value comparison must
accept both the English and Swahili form of the answer, because ADDO's spinner values are
localized placeholders.

Canonical negative test becomes:

```
(step2_diagnosed_diabetes == 'No' || step2_diagnosed_diabetes == 'Hapana' || step2_diagnosed_diabetes == '')
```

…and the positive test `(x == 'Yes' || x == 'Ndio')`.

### Remove
| Rule | Why |
|---|---|
| `step2_diagnosed_hypertension` | ADDO-only; the cascade in §3.2 supersedes it |
| `step4_prescreening_client_has_condition_toaster_not_using_meds` | Field removed |

### Rename / repair
| Rule | Change |
|---|---|
| `step3_diabetes_risk_score_output` | **Rename to `step4_diabetes_risk_score_output`.** The field lives in step4, so the current rule never matches — it is dead code, and `AddoRulesEngineFactory.getCalculation` already keys on the `step4_` name. Adopt CHW's OR-form condition: `step3_family_history_diabetes != '' \|\| step3_waist_circumference != '' \|\| step3_systolic_bp != '' \|\| step3_diastolic_bp != ''` |

### Modify (all four step-3 rules)
`step3_family_history_diabetes`, `step3_waist_circumference`, `step3_systolic_bp`,
`step3_diastolic_bp`:
- **Drop** the `step2_medicines_diabetes` and `step2_medicines_hypertension` clauses.
- **Fix the existing ADDO bug:** `step2_diagnosed_hypertension == 'Ndio'` inside the AND-chain is
  inverted — it currently shows step 3 when hypertension *is* diagnosed. Correct to
  `'No' || 'Hapana' || ''`.
- Keep dual literals throughout (do **not** adopt CHW's `== ''`-only form).

### Modify (step 4)
| Rule | Change |
|---|---|
| `step4_prescreening_client_has_condition_toaster` | Replace condition with CHW's OR-chain over `diagnosed_diabetes`, `screened_diabetes_12m`, `diagnosed_hypertension`, `screened_hypertension_last12m`, `routine_clinic_visits` (Yes/Ndio) |
| `step4_save` | Replace the medicines-referencing clause with CHW's Yes/Ndio OR-chain over the five step-2 fields |

### Add (5 new rules, verbatim semantics from CHW)
`step4_is_emergency_case`, `step4_has_treatment_supporter`, `step4_treatment_supporter_name`,
`step4_treatment_supporter_phone`, `step4_treatment_supporter_relationship`.

Shared referral gate:
```
(step3_systolic_bp != '' && step3_systolic_bp >= 140) ||
(step3_diastolic_bp != '' && step3_diastolic_bp >= 90) ||
(step4_diabetes_risk_score_output != '' && step4_diabetes_risk_score_output > 0.09175944)
```
The three `treatment_supporter_*` rules AND that gate with
`(step4_has_treatment_supporter == 'Yes' || step4_has_treatment_supporter == 'Ndio')`.

`service_provided_details` needs **no** rule — it uses inline `ex-checkbox` relevance.

Finally: add the missing trailing newline to both YAML files.

---

## 5. Properties delta

### 5.1 Revised keys (both locales)
`step1_consent_message`, `step1_reasons_for_declining_key_{in_hurry,privacy_trust,feel_healthy,fear_anxiety,other_reason}`,
`step2_{diagnosed_diabetes,diagnosed_hypertension,screened_*,routine_clinic_visits}_hint`,
`step3_family_history_diabetes_hint`, `step3_waist_circumference_hint_err`,
`step4_diabetes_risk_score_output_hint`, `step4_risk_present_toaster_text`,
`step4_prescreening_client_has_condition_toaster_text`, `step4_service_before_referral`.

### 5.2 Deleted keys
`step2_medicines_diabetes_hint(_err)`, `step2_medicines_hypertension_hint(_err)`,
`step4_prescreening_client_has_condition_toaster_not_using_meds`,
`step4_service_before_referral_given_{ORS,Panadol,Other_treatment,chk_none}`.

### 5.3 New keys

| Key | EN | SW (from CHW `json.form-sw`) |
|---|---|---|
| `step3_family_history_diabetes_info_title` | About family history of diabetes | *(translate)* |
| `step3_family_history_diabetes_info_text` | *(CHW long text)* | *(translate)* |
| `step3_systolic_bp_info_title` | About the top number (systolic) | *(translate)* |
| `step3_systolic_bp_info_text` | *(CHW long text)* | *(translate)* |
| `step3_diastolic_bp_info_title` | About the bottom number (diastolic) | *(translate)* |
| `step3_diastolic_bp_info_text` | *(CHW long text)* | *(translate)* |
| `step3_diastolic_lt_systolic_err` | The diastolic value must be less than the systolic value | *(translate)* |
| `step4_is_emergency_case_hint` | Is this an emergency case? | Je, hii ni dharura? |
| `step4_is_emergency_case_err` | Please indicate whether this is an emergency case | *(translate)* |
| `step4_service_before_referral_given_yes` | Yes | Ndio |
| `step4_service_before_referral_given_no` | No | Hapana |
| `step4_service_provided_details_hint` | Details of the service provided | Maelezo ya huduma zilizotolewa |
| `step4_service_provided_details_err` | Please specify the details of the service provided | *(translate)* |
| `step4_has_treatment_supporter_hint` | Do you have a caregiver/Treatment supporter? | Je, una mlezi/msaidizi wa matibabu? |
| `step4_treatment_supporter_name_hint` | Name of Treatment Supporter/Caregiver | Jina la Mlezi/Msaidizi wa Matibabu |
| `step4_treatment_supporter_phone_hint` | Treatment Supporter Phone Number | Namba ya Simu ya Msaidizi wa Matibabu |
| `step4_treatment_supporter_relationship_hint` | Relationship to Client | Uhusiano na Mpokea Huduma |
| `step3_systolic_bp_50_err` / `_320_err` | *(renamed from `step3_diastolic_bp_*`)* | *(renamed)* |

Swahili for step-1/2/3 hints and the step-4 toasters is transcribed directly from
`opensrp-chw/src/nacp/assets/json.form-sw/diabetes_hypertension_screening_form.json`.

**Relationship spinner values are not localized in CHW** (the SW form uses the same 18 English
option keys). Keep them literal in ADDO to match — do not add properties keys for them.

---

## 6. Java delta

All in `app/src/main/java/org/smartregister/addo/activity/FamilyOtherMemberProfileActivity.java`.

| # | Location | Change |
|---|---|---|
| **J1** | line ~289 | `"save_n_refer"` → `"db_save_n_refer"`. **Without this nothing else in this section is reachable.** |
| **J2** | `createReferralForm(...)` ~line 512 | Add `is_emergency_case` obs when non-empty, mirroring CHW `FamilyOtherMemberProfileActivity.java:625`: `referralFormArray.put(createReferralFormField("is_emergency_case", createFormViewData(value, null, metaData("concept", "is_emergency_case", ""))))` |
| **J3** | `createReferralForm(...)` | Add `service_provided_details` obs when non-empty |
| **J4** | `createReferralForm(...)` | Add the four supporter obs. Gate on `has_treatment_supporter == "Yes"`; when the gate is No emit only the gate. Port the *rule*, not `TreatmentSupporterFormUtil` — that class is built for NeatForm's `HashMap<String, NFormViewData>` shape. Recommended: a small `AddoTreatmentSupporterUtil.addScreeningReferralFields(JSONObject form, JSONArray referralFormArray)` in `org.smartregister.addo.util` |
| **J5** | `createReferralForm(...)` ~line 525 | `service_before_referral` now yields `["yes"]` / `["no"]` instead of drug keys. The existing JSONArray loop still works, but confirm the obs value shape is what the referral register expects |
| **J6** | `removeFieldsFromJSONArray(referralFormArray, "asterisk_symbol", "save_n_refer")` ~line 548 | Add `"db_save_n_refer"` and `"save"` to the strip list so buttons are not emitted as obs |

`getDiabetesAndHypertensionScreeningObs` walks every `step\d+` field generically — the new fields
reach the **encounter** with no change. J2–J4 are only about the separate **Referral Registration**
event.

`AddoRulesEngineFactory` needs no change: it already special-cases
`diabetes_hypertension_screening_calculation.yml` and keys on `step4_diabetes_risk_score_output`.

---

## 7. Decisions

| ID | Decision | Rationale |
|---|---|---|
| **D1** | Treatment-supporter fields ship as **manual entry**; no `TreatmentSupporterDao`, no registration capture, no DB column | ADDO has zero caregiver infrastructure. Prefill triples the blast radius (registration form + repository + migration) for a convenience feature. Deferrable without breaking parity of *captured data*. |
| **D2** | Fix the `save_n_refer` → `db_save_n_refer` defect inside this port | Every referral field ported here is dead until it lands. Shipping parity fields over a broken button would produce a green-looking release that still refers nobody. |
| **D3** | Adopt both the medicines-question removal and the step-2 cascade | Full CHW parity. Accepts the loss of `medicines_*` data ADDO records today — no downstream ADDO consumer of those fields exists. |
| **D4** | Port rules YAML **semantically**, keeping dual EN/SW literals | CHW's `== ''`-only conditions are locale-broken in its own SW build. Verbatim copy would regress ADDO's Swahili build. |
| **D5** | Keep ADDO's properties-file i18n; do not adopt CHW's dual-JSON mechanism | ADDO-wide convention; changing it is a far larger refactor than this port. |
| **D6** | Do not port CHW's `v_numeric.err` typo on `waist_circumference` ("Please enter the temperature") | Obvious copy-paste bug upstream. |

---

## 8. Execution plan

Each step names the probe that proves it landed. Run phases in order; phases 1–3 are independent
of phase 4 and can be reviewed separately.

### Phase 0 — Baseline (before touching anything)

| Step | Action | Probe |
|---|---|---|
| 0.1 | Branch from `ncd-addo` → `feat/ncd-screening-chw-parity` | `git branch --show-current` |
| 0.2 | Confirm the referral defect on-device: run a screening to a referral-triggering result, tap **Save and Refer** | `SELECT * FROM task WHERE for = '<baseEntityId>'` returns **0 rows** — the "before" evidence for J1 |
| 0.3 | Record the current build is green | `./gradlew :app:assembleMohDebug` succeeds |

### Phase 1 — Form JSON (§3)

| Step | Action | Probe |
|---|---|---|
| 1.1 | step1 — no structural change; note text keys for phase 3 | — |
| 1.2 | step2 — delete `medicines_diabetes`, `medicines_hypertension`; rewire the four cascade relevances | `python3 -c` field-key dump shows 5 step-2 fields in CHW order |
| 1.3 | step3 — add three `label_info_*` pairs, the diastolic `constraints` block, fix `v_numeric.err` | `jq '.step3.fields[] \| select(.key=="diastolic_bp") \| .constraints'` non-null |
| 1.4 | step4 — delete `..._not_using_meds`; add the 6 new fields; modify the 6 listed fields; reorder | Field-key dump matches the §3.4 order exactly |
| 1.5 | Validate JSON | `python3 -m json.tool <form> > /dev/null` exits 0 |
| 1.6 | Diff structure against CHW | Key-by-key dump of both forms differs only in `{{placeholder}}` vs literal text |

### Phase 2 — Rules YAML (§4)

| Step | Action | Probe |
|---|---|---|
| 2.1 | Remove the 2 dead rules | `grep -c "^name:"` drops by 2 |
| 2.2 | Rename `step3_diabetes_risk_score_output` → `step4_...` and adopt CHW's condition | `grep "step4_diabetes_risk_score_output" relevance.yml` hits |
| 2.3 | Rewrite the 4 step-3 conditions: drop medicines clauses, fix the inverted `'Ndio'`, keep dual literals | `grep -c "medicines" relevance.yml` returns 0 |
| 2.4 | Rewrite `step4_prescreening_client_has_condition_toaster` and `step4_save` | Visual diff against CHW + dual-literal check |
| 2.5 | Add the 5 new step-4 rules | `grep -c "^name: step4_treatment_supporter"` returns 3 |
| 2.6 | Add trailing newlines to both YAML files | `tail -c1 \| xxd` shows `0a` |
| 2.7 | Parse check | App boots and opens the form without an MVEL rule-parse crash in logcat |

### Phase 3 — Properties (§5)

| Step | Action | Probe |
|---|---|---|
| 3.1 | Apply revised + deleted keys to `_en` and `_sw` | Key-set diff between the two files is empty |
| 3.2 | Add the new keys to `_en` | — |
| 3.3 | Transcribe SW from CHW's `json.form-sw`; translate the 7 long `label_info_text` / err strings not present there | Both files have identical key sets |
| 3.4 | **Placeholder-coverage check** — every `{{key}}` in the form resolves | `comm -23` of form placeholders vs `_en` keys is empty; repeat for `_sw` |

### Phase 4 — Java (§6)

| Step | Action | Probe |
|---|---|---|
| 4.1 | **J1** — `save_n_refer` → `db_save_n_refer` | `grep -n "db_save_n_refer" FamilyOtherMemberProfileActivity.java` hits inside the screening branch |
| 4.2 | **J6** — extend the strip list | Referral event obs contain no button keys |
| 4.3 | **J2/J3** — `is_emergency_case` + `service_provided_details` obs | `SELECT` the Referral Registration event JSON; both `formSubmissionField`s present |
| 4.4 | **J4** — new `AddoTreatmentSupporterUtil`, gated on `has_treatment_supporter == "Yes"` | Gate=Yes → 4 obs present; gate=No → only the gate obs |
| 4.5 | **J5** — confirm `service_before_referral` obs shape | Referral register row renders the pre-referral value |
| 4.6 | Build | `./gradlew :app:assembleMohDebug` succeeds |

### Phase 5 — Verification

| ID | Scenario | Expected |
|---|---|---|
| **V1** | Consent = No | Reasons checkbox shows; `client_refused_assessment` toaster on step 4; only **Save** button |
| **V2** | Consent = Yes, `diagnosed_diabetes` = Yes | Cascade stops — remaining step-2 questions hidden; step 3 hidden; `prescreening_client_has_condition_toaster` on step 4; only **Save** |
| **V3** | All step-2 = No, systolic 150 / diastolic 95 | `risk_present_toaster`; `is_emergency_case`, `service_before_referral`, supporter block, facility and date all visible; **Save and Refer** shown |
| **V4** | 🔴 **Repeat V2 and V3 on a Swahili device** | Cascade advances and step 3 renders. If not, move the four step-2 relevances onto rules-engine with dual literals (§3.2 warning) |
| **V5** | Diastolic 100 with systolic 90 | Inline constraint error blocks submission |
| **V6** | Complete V3 and tap **Save and Refer** | A referral task **exists** for the client (the Phase-0.2 probe now returns 1 row) and the Referral Registration event carries `is_emergency_case`, `service_provided_details`, and the supporter obs |
| **V7** | All step-2 = No, systolic 118 / diastolic 76, low risk score | `no_risk_present_toaster`; only **Save** |
| **V8** | Tap the ⓘ icons on family history, systolic, diastolic | Guidance dialogs render in the active locale |

### Phase 6 — Regression harness

Port an ADDO-flavoured `NcdInitialScreeningConfigTest` into `app/src/test/java/` modelled on
`opensrp-chw/src/test/java/org/smartregister/chw/resources/NcdInitialScreeningConfigTest.java`.
It should assert, without an emulator:

1. The form parses and step 4 contains all 17 expected keys in order.
2. Every `{{placeholder}}` in the form has a key in **both** properties files.
3. `_en` and `_sw` have identical key sets.
4. Every `rules-engine` relevance reference resolves to a `name:` in the YAML — this is the test
   that would have caught the dead `step3_diabetes_risk_score_output` rule.
5. No YAML condition references a removed field (`medicines_*`).

Probe: `./gradlew :app:testMohDebugUnitTest --tests '*NcdInitialScreeningConfigTest*'` passes.

---

## 9. Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Step-2 cascade breaks in Swahili (literal `equalTo(., "No")` vs `"Hapana"`) | **High** | Screening unusable in SW | V4 is a blocking gate; fallback is rules-engine relevance with dual literals |
| Removing `medicines_*` orphans historical data | Low | Reporting gap | Fields are additive obs; existing events retain them. Confirm no ADDO report queries them before merge |
| `service_before_referral` obs shape change breaks the referral register row | Medium | Cosmetic/reporting | V6 + J5 |
| Supporter fields ship without prefill, adding CHW workload | Medium | UX friction | Accepted (D1); revisit once ADDO captures a caregiver at registration |
| CHW moves again during the port | Medium | Re-divergence | Pin the port to CHW `feat/ncd-auto-referral-emergency-supporter`; record the SHA in the merge commit |

---

## 10. Estimate

| Phase | Effort |
|---|---|
| 1 — Form JSON | 3–4 h |
| 2 — Rules YAML | 2 h |
| 3 — Properties (incl. 7 translations) | 3 h |
| 4 — Java | 3–4 h |
| 5 — Device verification (EN + SW) | 3 h |
| 6 — Regression test | 2–3 h |
| **Total** | **~2.5 days** |

---

## 11. Implementation notes — what actually changed (2026-07-30)

The port has been executed. Three corrections to the spec above, found during implementation:

### 11.1 The Java delta was smaller than §6 predicted

`createReferralForm(String, String)` seeds its array **from the step-4 fields themselves**:

```java
JSONArray referralFormArray = form.getJSONObject("step4").getJSONArray("fields");
```

So `is_emergency_case`, `service_provided_details` and the four `treatment_supporter_*` fields
reach the Referral Registration event **automatically**, with their OpenMRS metadata intact. J2,
J3 and J4 were written from CHW's build-the-map-from-scratch architecture and are unnecessary
here. No `AddoTreatmentSupporterUtil` was created.

**Actually changed in `FamilyOtherMemberProfileActivity.java`:**
- **J1** (line 289) `save_n_refer` → `db_save_n_refer` — the defect fix.
- **J6** (line 549) strip list extended to `asterisk_symbol`, `db_save_n_refer`, `save`, `spacer`.
  Both buttons now carry concept `165310AAAA…`, so without this they would be emitted as obs.

`AddoUtils.createReferralForm` (line 254) still references `save_n_refer` — **correctly left
alone**; it serves the ANC/PNC/child danger-sign path via `AddoVisitInteractor`, whose forms do
use that key.

### 11.2 Deviation — the step-2 cascade uses rules-engine, not inline `equalTo`

§3.2 specified CHW's inline `equalTo(., "No")` with V4 as a follow-up check. That check was
resolved at implementation time instead: ADDO's spinner `values` are `{{value_no}}`, so a literal
`"No"` comparison cannot match on a Swahili device. The cascade is implemented as four
rules-engine rules with dual `'No' || 'Hapana'` literals — identical behaviour in English,
correct in Swahili. This is decision **D4** applied consistently.

Each cascade rule tests the **full** prior chain, not just the immediately-preceding field, so
revising an earlier answer correctly re-hides everything downstream.

### 11.3 Deviation beyond spec scope — `reasons_for_declining`

`step1:reasons_for_declining` was also moved from inline `equalTo(., "No")` to rules-engine with
dual literals. It is the same defect class as 11.2 and meant a Swahili user who declined could
never record *why*. Pre-existing, and present in CHW too. Flagged here because it is a change the
spec did not authorise.

### 11.4 Verification performed (no device)

| Check | Result |
|---|---|
| Form JSON parses | ✅ `python3 -m json.tool` clean |
| Placeholder coverage | ✅ 79 placeholders, 79 `_en` keys, 79 `_sw` keys, zero unresolved, zero orphans |
| `_en` / `_sw` key sets identical | ✅ `diff` empty |
| Rules-engine references resolve | ✅ 25 rules defined, 25 fields reference rules-engine, 1:1, no dead rules |
| No reference to removed fields | ✅ zero `medicines_*` matches in the YAML |
| Properties `\n` escapes | ✅ `java.util.Properties` renders 13 real newlines in both locales, no literal `\n` |
| Java compiles | ✅ `:app:compileMohDebugJavaWithJavac` exit 0 |
| step-4 field order matches CHW | ✅ 17 keys in spec order |

**Still outstanding — Phase 5 device verification (V1–V8) and Phase 6 regression test have not
been done.** V6 in particular (a referral task actually landing) is the probe that closes out the
J1 defect fix, and it needs a device.

### 11.5 Regression found on device and fixed — empty facility spinner

**Symptom (2026-07-31, reported from a device run):** the form works end to end, but the health
facility spinner in step 4 is empty — no facilities to select, so no referral can be issued.

**Cause.** §3.4 told the port to drop `"exclusive": []` from `chw_referral_hf` on the grounds that
CHW's copy has no such key. That reasoning was wrong: CHW has no `exclusive` because CHW's Java
never reads it. ADDO's does. `FamilyOtherMemberProfileActivity.displayReferralFacilities()`
(app/src/main/java/…/FamilyOtherMemberProfileActivity.java:481) runs before the form activity is
started and populates the spinner:

```java
JSONArray facilityArrayOption          = hf_facilities.getJSONArray("options");
JSONArray facilityArrayOptionExclusive = hf_facilities.getJSONArray("exclusive");   // line 486
...
} catch (JSONException e) { Timber.e(e); }
```

`getJSONArray` throws on a missing key. With `exclusive` gone, line 486 threw, the whole method
aborted into the catch, and **zero** options were ever added — including the loop over
`Utils.getWardFacilities()` on the line below, which was never reached.

**Reproduction (JVM, no device).** A faithful replica of `displayReferralFacilities` run against
the on-disk form with `org.json` 20180813:

```
--- CURRENT (broken) form:
CAUGHT JSONException -> Timber.e(e), method aborts: JSONObject["exclusive"] not found.
facility options populated: 0
--- CONTROL: child_addo_danger_signs step3 (still has exclusive: [])
facility options populated: 2
```

**Fix.** Restore `"exclusive": []` on `chw_referral_hf`. Same probe against the fixed form now
reports `facility options populated: 2`. All four ADDO forms carrying `chw_referral_hf`
(`anc_addo_danger_signs`, `child_addo_danger_signs`, `pnc_addo_danger_signs`, and this one) again
declare both `options` and `exclusive` as empty arrays — that pair is the ADDO contract for this
field, not CHW's.

**Lesson for the rest of this port.** CHW-vs-ADDO field-shape differences are not automatically
drift to be normalised. Where ADDO's Java reads a key CHW's does not, the key is load-bearing.
Diff the *consumers*, not just the two JSON files.

### 11.6 Hardening the populate path (authorised follow-up, done)

The JSON restore in §11.5 unblocks this form. It does not stop the next form from doing the same
thing, and it does not address the second latent defect in the same routine. Both were fixed on
request.

`displayReferralFacilities` is copy-pasted, near-identical, into three places:

| File | Line | Serves |
|---|---|---|
| `activity/FamilyOtherMemberProfileActivity.java` | 481 | NCD screening + other-member forms |
| `activity/FamilyFocusedMemberProfileActivity.java` | 649 | ANC/PNC/child danger signs |
| `util/AddoUtils.java` | 178 | danger signs via `AddoVisitInteractorFlv.getPreProcessed()` |

**Change 1 — strict accessor → self-healing accessor.** A single shared helper,
`AddoUtils.requireFieldArray(JSONObject field, String arrayName)`, replaces
`getJSONArray("options")` / `getJSONArray("exclusive")` in all three copies. It uses
`optJSONArray`, and if the array is absent it logs a `Timber.w` naming the missing key and
attaches an empty one. A form asset that drops either array now produces a warning line and a
working spinner instead of a silent empty one.

**Change 2 — null field guard.** `JsonFormUtils.getFieldJSONObject` returns null when the field is
absent; all three copies dereferenced it immediately. Each now checks and logs
`"Form has no chw_referral_hf field; referral facilities not populated"`. The `AddoUtils` variant
returns the form unchanged rather than `null`, so `getPreProcessed()` no longer hands a null
payload downstream for a merely-absent field.

**Change 3 — the null guard that was never a guard.** `assert facilities != null` — Android
disables assertions at runtime, so a null from `Utils.getWardFacilities()` would have gone straight
into the for-each as an NPE, which `catch (JSONException e)` does not catch. Fixed at the ingestion
point: `getWardFacilities()` now returns `Collections.emptyList()` instead of `null`. No caller
null-checked it, so nothing else changes. The three populate sites now log
`"No facilities in this ward's location hierarchy; the referral spinner will be empty"` on an empty
list, which also covers the previously-indistinguishable case of a ward with no `Facility` children.

**Change 4 — the catch says what failed.** `Timber.e(e)` → `Timber.e(e, "Failed to populate
referral facilities on chw_referral_hf")` in all three copies.

**Verification.** `:app:compileMohDebugJavaWithJavac` exit 0. A JVM replica of the hardened routine
across four cases:

```
CASE 1 — shipped form, 2 facilities:                       options populated: 2
CASE 2 — form with 'exclusive' deleted (the regression):   WARN: missing 'exclusive' array; adding it
                                                           options populated: 2
CASE 3 — empty ward:                                       ERROR: no facilities in ward hierarchy
                                                           options populated: 0
CASE 4 — form has no chw_referral_hf field:                ERROR: no chw_referral_hf field
                                                           options populated: 0
```

Case 2 is the point: the exact edit that caused this bug no longer breaks the spinner.

**Not done.** The three copies are still three copies — collapsing them into the single
`AddoUtils` implementation is a wider refactor across the danger-signs path and was not part of
this request. They now share `requireFieldArray`, so the accessor bug at least cannot drift
between them again.
