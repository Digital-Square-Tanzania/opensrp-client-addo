package org.smartregister.addo.util;

import static org.smartregister.addo.activity.FamilyFocusedMemberProfileActivity.ADOLESCENT_SCREENING_ENCOUNTER;
import static org.smartregister.addo.activity.FamilyFocusedMemberProfileActivity.ANC_DANGER_SIGN_SCREENING_ENCOUNTER;
import static org.smartregister.addo.activity.FamilyFocusedMemberProfileActivity.CHILD_DANGER_SIGN_SCREENING_ENCOUNTER;
import static org.smartregister.addo.activity.FamilyFocusedMemberProfileActivity.PNC_DANGER_SIGN_SCREENING_ENCOUNTER;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.google.gson.Gson;
import com.vijay.jsonwizard.constants.JsonFormConstants;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.addo.R;
import org.smartregister.addo.application.AddoApplication;
import org.smartregister.addo.domain.DukaLaDawaPayload;
import org.smartregister.addo.model.ReferralObsValues;
import org.smartregister.chw.anc.domain.MemberObject;
import org.smartregister.util.FormUtils;
import org.smartregister.util.Utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import timber.log.Timber;

public class AddoUtils extends Utils {


    private static FormUtils formUtils;

    private static StringBuilder medicationsSelectedString = new StringBuilder();

    public static String checkDSPresentProposedMedsAndDispense(JSONObject form, Constants.FamilyMemberType familyMemberType) throws JSONException{
        String updatedMedicationForm = null;
        try {
            // Check if the focused group client is present or not; if not skip to dispensing
            if (isClientPresent(form)) {
                String dangerSigns;
                String suggestedMeds;

                JSONObject step2 = form.getJSONObject(JsonFormUtils.STEP2);
                JSONArray step2Fields = step2.getJSONArray(JsonFormUtils.FIELDS);
                JSONObject dangerSignsObject = getDangerSignsSelected(form, step2Fields);
                JSONArray dangerSignsSelected = dangerSignsObject.getJSONArray(JsonFormUtils.VALUE);
                // When there is danger signs and the none field is not selected open the dispense medication
                if (dangerSignsSelected.length() > 0 && !dangerSignsSelected.getString(0).equalsIgnoreCase("chk_none")) {
                    dangerSigns = getDangerSignsString(dangerSignsObject);
                    // Check if client has referral or to determine if they should be linked to another ADDO or not
                    JSONArray step3Fields = form.getJSONObject(JsonFormUtils.STEP3).getJSONArray(JsonFormUtils.FIELDS);
                    JSONObject referralButtonObject = JsonFormUtils.getFieldJSONObject(step3Fields, "save_n_refer");
                    String referralStatus;
                    if (referralButtonObject.optString(JsonFormUtils.VALUE) != null && referralButtonObject.optString(JsonFormUtils.VALUE).compareToIgnoreCase("true") == 0) {
                        referralStatus = "referred";
                    } else {
                        referralStatus = null;
                    }

                    updatedMedicationForm = dispenseMedication(dangerSigns, AddoApplication.getInstance().getContext().getStringResource(R.string.default_dispense_message), referralStatus, familyMemberType);
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

    private static String getDangerSignsString(JSONObject dangerSignsObject) throws JSONException {

        List<String> dangerSignsText = JsonQ.fromJson(dangerSignsObject.toString()).getStrings("options[(@.value==true)].text");

        // We are putting html tags here so as to display it properly on the toaster message
        // The message will be something like:
        // This client has the following danger signs:
        // • Albendazole Suspension/Tablets
        // • Paracetamol tablets

        return "<br /> \u25FB " + String.join("<br /> \u25FB ", dangerSignsText) + "<br />";

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

    private static JSONObject getDangerSignsSelected(JSONObject form, JSONArray stepFields) {
        JSONObject dangerSignsObject = new JSONObject();

        switch (form.optString(JsonFormUtils.ENCOUNTER_TYPE)) {
            case CHILD_DANGER_SIGN_SCREENING_ENCOUNTER:
                dangerSignsObject = JsonFormUtils.getFieldJSONObject(stepFields, "danger_signs_present_child");
                break;
            case ANC_DANGER_SIGN_SCREENING_ENCOUNTER:
                dangerSignsObject = JsonFormUtils.getFieldJSONObject(stepFields, "danger_signs_present");
                break;
            case PNC_DANGER_SIGN_SCREENING_ENCOUNTER:
                dangerSignsObject = JsonFormUtils.getFieldJSONObject(stepFields, "danger_signs_present_mama");
                break;
            case ADOLESCENT_SCREENING_ENCOUNTER:
                dangerSignsObject = JsonFormUtils.getFieldJSONObject(stepFields, "adolescent_condition_present");
                break;
            default:
                return null;
        }

        return dangerSignsObject;
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
            JSONArray fields = JsonFormUtils.fields(jsonForm);
            JSONObject hf_facilities = JsonFormUtils.getFieldJSONObject(fields, "chw_referral_hf");
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

    public static JSONArray createReferralForm(JSONObject dangerSignsFormJsonObject, JSONObject medicationsFormJsonObject){
        try{
            String encounterType = dangerSignsFormJsonObject.optString(JsonFormUtils.ENCOUNTER_TYPE);
            JSONArray referralFormArray = dangerSignsFormJsonObject.getJSONObject("step3").getJSONArray("fields");
            JSONArray fields = JsonFormUtils.fields(dangerSignsFormJsonObject);
            JSONObject dangerSignsFieldJsonObject = getDangerSignsFieldObject(fields, encounterType);
            String chwReferralService = "";
            switch(encounterType) {
                case CHILD_DANGER_SIGN_SCREENING_ENCOUNTER:
                    chwReferralService = CHILD_DANGER_SIGN_SCREENING_ENCOUNTER;
                    break;
                case ANC_DANGER_SIGN_SCREENING_ENCOUNTER:
                    chwReferralService = ANC_DANGER_SIGN_SCREENING_ENCOUNTER;
                    break;
                case PNC_DANGER_SIGN_SCREENING_ENCOUNTER:
                    chwReferralService = PNC_DANGER_SIGN_SCREENING_ENCOUNTER;
                    break;
                case ADOLESCENT_SCREENING_ENCOUNTER:
                    chwReferralService = ADOLESCENT_SCREENING_ENCOUNTER;
                    break;
                default:
                    Timber.e("Encounter type not recognized: %S", encounterType);
                    break;
            }

            // Combine the checkbox values
            dangerSignsFieldJsonObject.put(JsonFormUtils.COMBINE_CHECKBOX_OPTION_VALUES,true);

            // Rename the key for danger sign JSONObject to match UCS
            //dangerSignsFieldJsonObject.put("key","problem");

            //Convert referral appointment date to timestamp
            convertDateToLong(fields, "referral_appointment_date");

            //Add other referral form fields
            referralFormArray.put(dangerSignsFieldJsonObject);
            referralFormArray.put(createReferralFormField("referral_status", Constants.REFERRAL_BUSINESS_STATUS));
            referralFormArray.put(createReferralFormField("chw_referral_service", chwReferralService));
            referralFormArray.put(createReferralFormField("referral_date", Long.toString(Calendar.getInstance().getTimeInMillis())));
            referralFormArray.put(createReferralFormField("referral_type", Constants.REFERRAL_TYPE));
            referralFormArray.put(createReferralFormField("referral_time", currentDateTime()));

            // Remove unwanted fields
            removeFieldsFromJSONArray(referralFormArray, "asterisk_symbol", "save_n_refer");

            // Add meds dispensed
            JSONObject medicationsSelectedFieldJsonObject = new JSONObject();

            if (medicationsFormJsonObject != null) {
                medicationsSelectedFieldJsonObject = JsonFormUtils.getFieldJSONObject(JsonFormUtils.fields(medicationsFormJsonObject), "medications_selected");
                referralFormArray.put(medicationsSelectedFieldJsonObject);
            }

            // referralFormArray.put(createReferralFormField("service_before_referral",
             //       medicationDispensedValue != null ? getDispensedMedicineName(medicationDispensedValue) : "None"));

            return  referralFormArray;
        }catch (JSONException e){
            Timber.e(e);
        }
        return  null;
    }

    public static JSONObject getDangerSignsFieldObject(JSONArray fields, String encounterType) {
        JSONObject dangerSignsFieldJsonObject = new JSONObject();
        switch(encounterType) {
            case CHILD_DANGER_SIGN_SCREENING_ENCOUNTER:
                dangerSignsFieldJsonObject = JsonFormUtils.getFieldJSONObject(fields,"danger_signs_present_child");
                break;
            case ANC_DANGER_SIGN_SCREENING_ENCOUNTER:
                dangerSignsFieldJsonObject = JsonFormUtils.getFieldJSONObject(fields,"danger_signs_present");
                break;
            case PNC_DANGER_SIGN_SCREENING_ENCOUNTER:
                dangerSignsFieldJsonObject = JsonFormUtils.getFieldJSONObject(fields,"danger_signs_present_mama");
                break;
            case ADOLESCENT_SCREENING_ENCOUNTER:
                dangerSignsFieldJsonObject = JsonFormUtils.getFieldJSONObject(fields,"adolescent_condition_present");
                break;
            default:
                Timber.e("Encounter type not recognized: %S", encounterType);
                break;
        }
        return dangerSignsFieldJsonObject;
    }

    public static JSONObject createReferralFormField(String key, Object value) {
        try {
            JSONObject referralTypeJsonObject = new JSONObject();
            referralTypeJsonObject.put("key", key);
            referralTypeJsonObject.put("text", "name");
            referralTypeJsonObject.put("type", key.equals("service_before_referral") ? "text" : key);
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

    public static ReferralObsValues createObsValuesFromFields(JSONObject fieldJsonObject) throws JSONException {
        // Get selected values and options
        JSONArray selectedValuesJsonArray = new JSONArray();

        if ("multi_select_list".equals(fieldJsonObject.getString(JsonFormConstants.TYPE)) && !fieldJsonObject.optString(JsonFormConstants.VALUE).isEmpty()) {
            JSONArray selectedJsonArrayObject = new JSONArray(fieldJsonObject.optString(JsonFormConstants.VALUE));
            selectedValuesJsonArray = getMultiSelectJsonArrayKeys(selectedJsonArrayObject);
        } else if ("check_box".equals(fieldJsonObject.getString(JsonFormConstants.TYPE))) {
            selectedValuesJsonArray = fieldJsonObject.getJSONArray("value");
        }

        List<String> selectedValues = jsonArrayToList(selectedValuesJsonArray);
        JSONArray options = fieldJsonObject.getJSONArray("options");

        // Prepare lists for values and human-readable texts
        List<String> values = new ArrayList<>();
        List<String> humanReadableValues = new ArrayList<>();

        // Iterate through options to find selected ones
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.getJSONObject(i);
            String key = option.getString("key");
            String text = option.getString("text");

            if (selectedValues.contains(key)) {
                values.add(key);
                humanReadableValues.add(text);
            }
        }
        return new ReferralObsValues(values, humanReadableValues);
    }

    private static JSONArray getMultiSelectJsonArrayKeys(JSONArray selectedJsonArrayObject) {

        JSONArray arrayOfSelectedKeys = new JSONArray();

        for (int i = 0; i < selectedJsonArrayObject.length(); i ++) {
            try {
                arrayOfSelectedKeys.put(selectedJsonArrayObject.getJSONObject(i).getString(JsonFormConstants.KEY));
            } catch (JSONException e) {
                Timber.e(e);
            }
        }
        return arrayOfSelectedKeys;
    }

    public static List<Object> convertToObjectList(List<String> strings) {
        return new ArrayList<>(strings);
    }

    private static List<String> jsonArrayToList(JSONArray jsonArray) throws JSONException {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            list.add(jsonArray.getString(i));
        }
        return list;
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

    private static String getDispensedMedicineName(String medicationDispensedValue) throws JSONException {
        JSONArray jsonArrayMedicineDispensed = new JSONArray(medicationDispensedValue);

        StringBuilder medListString = new StringBuilder();

        for (int i = 0; i < jsonArrayMedicineDispensed.length(); i++) {
            JSONObject selectedMedObject = jsonArrayMedicineDispensed.getJSONObject(i);
            String medicineName = selectedMedObject.getString("text");
            if (medListString.length() > 0) {
                medListString.append(", ");
            }
            medListString.append(medicineName);
        }

        return medListString.toString();

    }

    public static void launchDukaLaDawaApp(Activity activity, MemberObject memberObject, String gender, String prescriptionNote) {
        Gson gson = new Gson();
        Locale currentLocale = AddoApplication.getCurrentLocale();
        String language = currentLocale != null ? currentLocale.getLanguage() : Locale.getDefault().getLanguage();

        DukaLaDawaPayload dukaLaDawaPayload = new DukaLaDawaPayload(memberObject.getBaseEntityId(), memberObject.getDob(), gender, prescriptionNote);
        String payLoad = gson.toJson(dukaLaDawaPayload);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("addopharmacy://salesregister?data=" + Uri.encode(payLoad)));

        activity.startActivityForResult(intent, JsonFormUtils.REQUEST_CODE_GET_JSON);
    }

    public static String createMedicationDispenseForm(JSONObject medicationJsonObject) {
        try {
            JSONObject medicationForm = FormUtils.getInstance(org.smartregister.family.util.Utils.context().applicationContext()).getFormJson("duka_medicine_dispensed");
            JSONArray formFields = JsonFormUtils.fields(medicationForm);

            JSONObject medicineDispensedFormJsonObject = org.smartregister.family.util.JsonFormUtils.getFieldJSONObject(formFields,"medicine_dispensed");

            addOptionFields(medicationJsonObject, medicineDispensedFormJsonObject);

            JSONObject medicationsSelectedFormJsonObject = org.smartregister.family.util.JsonFormUtils.getFieldJSONObject(formFields,"medications_selected");
            medicationsSelectedFormJsonObject.put("value", medicationsSelectedString.toString());

            return medicationForm.toString();
        } catch (Exception e) {
            Timber.e(e);
        }
        return null;
    }

    private static void addOptionFields(JSONObject medicineDispensedJsonObjectValue, JSONObject medicineDispensedObject){
        try{
            JSONArray options = medicineDispensedObject.getJSONArray("options");
            String jsonString = "{\n" +
                    "    \"key\": \"\",\n" +
                    "    \"text\": \"\",\n" +
                    "    \"openmrs_entity\": \"\",\n" +
                    "    \"openmrs_entity_id\": \"\",\n" +
                    "    \"openmrs_entity_parent\": \"\",\n" +
                    "    \"property\": {\n" +
                    "      \"presumed-id\": \"err\",\n" +
                    "      \"confirmed-id\": \"err\"\n" +
                    "    }\n" +
                    "}";
            JSONArray jsonArray = medicineDispensedJsonObjectValue.getJSONArray("administered_medicines");

            medicationsSelectedString = new StringBuilder();

            for(int i = 0; i < jsonArray.length(); i++){
                JSONObject optionJsonObject = new JSONObject(jsonString);
                JSONObject jsonObject1 = jsonArray.getJSONObject(i);

                String nameOptionValue = jsonObject1.getString("name");
                String idOptionValue = jsonObject1.getString("id");

                // Create the string with <br /> between each medicine name
                medicationsSelectedString.append("• ").append(nameOptionValue).append("<br />");

                optionJsonObject.put("key", idOptionValue);
                optionJsonObject.put("text", nameOptionValue);
                optionJsonObject.put("openmrs_entity_id", idOptionValue);

                options.put(optionJsonObject);
            }

            medicineDispensedObject.put("value", options.toString());
        }catch (JSONException jsonException){
            Timber.e(jsonException);
        }
    }

    public static String getPrescriptionNote(String jsonString){
        try {
            assert jsonString != null;
            String prescriptionNote = "";
            JSONArray prescriptionFormFields = org.smartregister.family.util.JsonFormUtils.fields(new JSONObject(jsonString));
            String prescriptionNoteValue = org.smartregister.family.util.JsonFormUtils.getFieldValue(prescriptionFormFields, "client_prescription_note_available");
            if(prescriptionNoteValue != null){
                prescriptionNote= prescriptionNoteValue.contains("client_prescription_yes") ? "Yes" : "No";
            }
            return prescriptionNote;
        } catch (JSONException e){
            Timber.e(e);
        }
        return null;
    }
}
