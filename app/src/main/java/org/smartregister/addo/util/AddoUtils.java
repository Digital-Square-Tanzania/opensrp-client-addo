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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

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

    public static String displayReferralFacilities(JSONObject jsonForm){
        try{
            JSONArray fields = org.smartregister.addo.util.JsonFormUtils.fields(jsonForm);
            JSONObject hf_facilities = org.smartregister.addo.util.JsonFormUtils.getFieldJSONObject(fields, "chw_referral_hf");
            JSONArray facilityArrayOption = hf_facilities.getJSONArray("options");
            JSONArray facilityArrayOptionExclusive = hf_facilities.getJSONArray("exclusive");

            List<JSONObject> facilities= org.smartregister.addo.util.Utils.getWardFacilities();
            assert facilities != null;
            for (JSONObject facility : facilities) {
                JSONObject node = facility.getJSONObject("node");
                String locationId = node.getString("locationId");
                String name = node.getString("name");

                // Add the locationId for exclusive selection
                facilityArrayOptionExclusive.put(locationId);

                JSONObject newOption = new JSONObject();
                newOption.put("key", locationId);
                newOption.put("text", name);
                newOption.put("value", false);
                newOption.put("openmrs_entity", "concept");
                newOption.put("openmrs_entity_id", locationId);

                facilityArrayOption.put(newOption);
            }
            return jsonForm.toString();
        }catch (JSONException e){
            Timber.e(e);
        }
        return null;
    }

    public static JSONArray createReferralForm(JSONObject dangerSignJsonObject, JSONObject medicationJsonObject){
        try{
            String encounterType = dangerSignJsonObject.optString(JsonFormUtils.ENCOUNTER_TYPE);
            JSONArray referralFormArray = dangerSignJsonObject.getJSONObject("step3").getJSONArray("fields");
            JSONArray fields = JsonFormUtils.fields(dangerSignJsonObject);
            JSONObject dangerSignsJsonObject = new JSONObject();
            String chwReferralService = "";
            switch(encounterType) {
                case CHILD_DANGER_SIGN_SCREENING_ENCOUNTER:
                    dangerSignsJsonObject = JsonFormUtils.getFieldJSONObject(fields,"danger_signs_present_child");
                    chwReferralService = CHILD_DANGER_SIGN_SCREENING_ENCOUNTER;
                    break;
                case ANC_DANGER_SIGN_SCREENING_ENCOUNTER:
                    dangerSignsJsonObject = JsonFormUtils.getFieldJSONObject(fields,"danger_signs_present");
                    chwReferralService = ANC_DANGER_SIGN_SCREENING_ENCOUNTER;
                    break;
                case PNC_DANGER_SIGN_SCREENING_ENCOUNTER:
                    dangerSignsJsonObject = JsonFormUtils.getFieldJSONObject(fields,"danger_signs_present_mama");
                    chwReferralService = PNC_DANGER_SIGN_SCREENING_ENCOUNTER;
                    break;
                case ADOLESCENT_SCREENING_ENCOUNTER:
                    dangerSignsJsonObject = JsonFormUtils.getFieldJSONObject(fields,"adolescent_condition_present");
                    chwReferralService = ADOLESCENT_SCREENING_ENCOUNTER;
                    break;
                default:
                    Timber.e("Encounter type not recognized: %S", encounterType);
                    break;
            }

            // Combine the checkbox values
            dangerSignsJsonObject.put(JsonFormUtils.COMBINE_CHECKBOX_OPTION_VALUES,true);

            // Rename the key for danger sign JSONObject to match UCS
            dangerSignsJsonObject.put("key","problem");

            //Convert referral appointment date to timestamp
            convertDateToLong(fields, "referral_appointment_date");

            //Add other referral form fields
            referralFormArray.put(dangerSignsJsonObject);
            referralFormArray.put(createReferralFormField("referral_status", Constants.REFERRAL_BUSINESS_STATUS));
            referralFormArray.put(createReferralFormField("chw_referral_service", chwReferralService));
            referralFormArray.put(createReferralFormField("referral_date", Long.toString(Calendar.getInstance().getTimeInMillis())));
            referralFormArray.put(createReferralFormField("referral_type", Constants.REFERRAL_TYPE));
            referralFormArray.put(createReferralFormField("referral_time", currentDateTime()));

            // Remove unwanted fields
            removeFieldsFromJSONArray(referralFormArray, "asterisk_symbol", "save_n_refer");

            // Add meds dispensed
            JSONObject medications_selected = JsonFormUtils.getFieldJSONObject(JsonFormUtils.fields(medicationJsonObject), "medications_selected");
            referralFormArray.put(medications_selected);

            return  referralFormArray;
        }catch (JSONException e){
            Timber.e(e);
        }
        return  null;
    }

    private static JSONObject createReferralFormField(String key, Object value) {
        try {
            JSONObject referralTypeJsonObject = new JSONObject();
            referralTypeJsonObject.put("key", key);
            referralTypeJsonObject.put("text", "name");
            referralTypeJsonObject.put("type", key);
            referralTypeJsonObject.put("value", value);
            referralTypeJsonObject.put("openmrs_entity", "concept");
            referralTypeJsonObject.put("openmrs_entity_id", key);

            // Add additional field for referral_date key
            if ("referral_date".equals(key)) {
                referralTypeJsonObject.put("openmrs_data_type", "date");
            }

            return referralTypeJsonObject;
        } catch (Exception e) {
            Timber.e(e);
        }
        return null;
    }

    public static void convertDateToLong(JSONArray fields, String fieldName) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
            JSONObject dateField = JsonFormUtils.getFieldJSONObject(fields, fieldName);
            String value = dateField.getString(JsonFormUtils.VALUE);
            Date formattedDate = dateFormat.parse(value);
            assert formattedDate != null;
            long unixTimestamp = formattedDate.getTime();
            dateField.put("value", String.valueOf(unixTimestamp));
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    public static String currentDateTime(){
        Date now = new Date();
        DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
        return dateFormat.format(now);
    }

    public static void removeFieldsFromJSONArray(JSONArray jsonArray, String... keysToRemove) {
        for (int i = jsonArray.length() -1 ; i >= 0; i--) {
            try {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                for (String value : keysToRemove) {
                    String jsonValue = jsonObject.getString("key");
                    if(jsonValue.equals(value)){
                        jsonArray.remove(i);
                        break;
                    }
                }
            } catch (JSONException e) {
                Timber.e(e);
            }
        }
    }
}
