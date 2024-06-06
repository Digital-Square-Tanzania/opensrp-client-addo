package org.smartregister.addo.util;

import static org.smartregister.addo.activity.FamilyFocusedMemberProfileActivity.ADOLESCENT_SCREENING_ENCOUNTER;
import static org.smartregister.addo.activity.FamilyFocusedMemberProfileActivity.ANC_DANGER_SIGN_SCREENING_ENCOUNTER;
import static org.smartregister.addo.activity.FamilyFocusedMemberProfileActivity.CHILD_DANGER_SIGN_SCREENING_ENCOUNTER;
import static org.smartregister.addo.activity.FamilyFocusedMemberProfileActivity.PNC_DANGER_SIGN_SCREENING_ENCOUNTER;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.addo.R;
import org.smartregister.addo.application.AddoApplication;
import org.smartregister.util.FormUtils;
import org.smartregister.util.Utils;

import timber.log.Timber;

public class AddoUtils extends Utils {


    private static FormUtils formUtils;
    public static String checkDSPresentProposedMedsAndDispense(JSONObject form, Constants.FamilyMemberType familyMemberType) {
        String updatedMedicationForm = null;
        try {
            // Check if the focused group client is present or not; if not skip to dispensing
            if (isClientPresent(form)) {
                String dangerSigns;
                String suggestedMeds;

                JSONObject step2 = form.getJSONObject(JsonFormUtils.STEP2);
                JSONArray step2Fields = step2.getJSONArray(JsonFormUtils.FIELDS);
                JSONArray dangerSignsSelected = getDangerSignsSelected(form, step2Fields);

                // When there is danger signs and the none field is not selected open the dispense medication
                if (dangerSignsSelected.length() > 0 && !dangerSignsSelected.getString(0).equalsIgnoreCase("chk_none")) {
                    dangerSigns = JsonFormUtils.getFieldJSONObject(step2Fields, "danger_signs_captured").getString(JsonFormUtils.VALUE);
                    if (form.optString(org.smartregister.chw.anc.util.Constants.ENCOUNTER_TYPE).equalsIgnoreCase(CHILD_DANGER_SIGN_SCREENING_ENCOUNTER)) {
                        suggestedMeds = JsonFormUtils.getFieldJSONObject(step2Fields, "addo_medication_to_give").getString(JsonFormUtils.VALUE);
                    } else {
                        suggestedMeds = AddoApplication.getInstance().getContext().getStringResource(R.string.default_dispense_message);
                    }
                    // Check if client has referral or to determine if they should be linked to another ADDO or not
                    JSONArray step3Fields = form.getJSONObject(JsonFormUtils.STEP3).getJSONArray(JsonFormUtils.FIELDS);
                    JSONObject referralButtonObject = JsonFormUtils.getFieldJSONObject(step3Fields, "save_n_refer");
                    String referralStatus;
                    if (referralButtonObject.optString(JsonFormUtils.VALUE) != null && referralButtonObject.optString(JsonFormUtils.VALUE).compareToIgnoreCase("true") == 0) {
                        referralStatus = "referred";
                    } else {
                        referralStatus = null;
                    }

                    updatedMedicationForm = dispenseMedication(dangerSigns, suggestedMeds, referralStatus, familyMemberType);
                } else if (dangerSignsSelected.getString(0).equalsIgnoreCase("chk_none")) {
                    updatedMedicationForm = dispenseMedication(null, AddoApplication.getInstance().getContext().getStringResource(R.string.default_dispense_message), null, familyMemberType);
                }
            } else {
                updatedMedicationForm = dispenseMedication(null, null, null, familyMemberType);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return updatedMedicationForm;

    }

    private static Boolean isClientPresent(@NotNull JSONObject form) {
        try {
            JSONObject step1 = form.getJSONObject(JsonFormUtils.STEP1);
            JSONArray step1Fields = step1.getJSONArray(JsonFormUtils.FIELDS);
            if (form.optString(JsonFormUtils.ENCOUNTER_TYPE).equalsIgnoreCase(CHILD_DANGER_SIGN_SCREENING_ENCOUNTER)) {
                return JsonFormUtils.getFieldJSONObject(step1Fields, "child_present").getJSONArray(JsonFormUtils.VALUE).get(0).toString().equals("chk_child_present_yes");
            } else if (form.optString(JsonFormUtils.ENCOUNTER_TYPE).equalsIgnoreCase(ANC_DANGER_SIGN_SCREENING_ENCOUNTER)) {
                return JsonFormUtils.getFieldJSONObject(step1Fields, "pregnant_woman_present").getJSONArray(JsonFormUtils.VALUE).get(0).toString().equals("chk_pregnant_woman_present_yes");
            } else if (form.optString(JsonFormUtils.ENCOUNTER_TYPE).equalsIgnoreCase(PNC_DANGER_SIGN_SCREENING_ENCOUNTER)) {
                return JsonFormUtils.getFieldJSONObject(step1Fields, "mother_present").getJSONArray(JsonFormUtils.VALUE).get(0).toString().equals("chk_mother_present_yes");
            } else {
                return JsonFormUtils.getFieldJSONObject(step1Fields, "adolescent_present").getJSONArray(JsonFormUtils.VALUE).get(0).equals("adolescent_present_yes");
            }

        } catch (JSONException e) {
            Timber.e(e);
        }
        return false;
    }

    private static JSONArray getDangerSignsSelected(JSONObject form, JSONArray stepFields) {
        JSONArray dangerSignsSelected = new JSONArray();
        try {

            switch (form.optString(JsonFormUtils.ENCOUNTER_TYPE)) {
                case CHILD_DANGER_SIGN_SCREENING_ENCOUNTER:
                    dangerSignsSelected = JsonFormUtils.getFieldJSONObject(stepFields, "danger_signs_present_child").getJSONArray(JsonFormUtils.VALUE);
                    break;
                case ANC_DANGER_SIGN_SCREENING_ENCOUNTER:
                    dangerSignsSelected = JsonFormUtils.getFieldJSONObject(stepFields, "danger_signs_present").getJSONArray(JsonFormUtils.VALUE);
                    break;
                case PNC_DANGER_SIGN_SCREENING_ENCOUNTER:
                    dangerSignsSelected = JsonFormUtils.getFieldJSONObject(stepFields, "danger_signs_present_mama").getJSONArray(JsonFormUtils.VALUE);
                    break;
                case ADOLESCENT_SCREENING_ENCOUNTER:
                    dangerSignsSelected = JsonFormUtils.getFieldJSONObject(stepFields, "adolescent_condition_present").getJSONArray(JsonFormUtils.VALUE);
                    break;
                default:
                    return null;
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return dangerSignsSelected;
    }

    private static String dispenseMedication(String dangerSigns, String suggestedMeds, String referralStatus, Constants.FamilyMemberType familyMemberType) {
        try {
            JSONObject form = new JSONObject();
            // ANC and PNC have the same Dispense Medication Form
            if(familyMemberType.equals(Constants.FamilyMemberType.ANC) || familyMemberType.equals(Constants.FamilyMemberType.PNC)) {
                form = getFormUtils().getFormJson(CoreConstants.JSON_FORM.getDangerSignsMedicationAnc());
            } else if(familyMemberType.equals(Constants.FamilyMemberType.ADOLESCENT)) {
                form = getFormUtils().getFormJson(CoreConstants.JSON_FORM.getDangerSignsMedicationAdolescent());
            } else {
                form = getFormUtils().getFormJson(CoreConstants.JSON_FORM.getDangerSignMedicationChild());
            }
            JSONObject stepOne = form.getJSONObject(JsonFormUtils.STEP1);
            JSONArray fields = stepOne.getJSONArray(JsonFormUtils.FIELDS);
            updateFormField(fields, "danger_signs_captured", dangerSigns);
            updateFormField(fields, "addo_medication_to_give", suggestedMeds);
            updateFormField(fields, "referral_status", referralStatus);
            return form.toString();
        } catch (JSONException e) {
            Timber.e(e);
            return null;
        }
    }

    private static void updateFormField(JSONArray formFieldArrays, String formFieldKey, String updateValue) {
        if (updateValue != null) {
            JSONObject formObject = org.smartregister.util.JsonFormUtils.getFieldJSONObject(formFieldArrays, formFieldKey);
            if (formObject != null) {
                try {
                    formObject.put(org.smartregister.util.JsonFormUtils.VALUE, updateValue);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static FormUtils getFormUtils() {
        if (formUtils == null) {
            try {
                formUtils = FormUtils.getInstance(org.smartregister.family.util.Utils.context().applicationContext());
            } catch (Exception e) {
                Timber.e(e);
            }
        }
        return formUtils;
    }

}
