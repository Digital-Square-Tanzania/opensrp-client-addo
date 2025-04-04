package org.smartregister.addo.util;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.addo.R;
import org.smartregister.addo.application.AddoApplication;
import org.smartregister.addo.model.ReferralObsValues;
import org.smartregister.util.Utils;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

import static org.smartregister.addo.activity.FamilyFocusedMemberProfileActivity.ADOLESCENT_SCREENING_ENCOUNTER;
import static org.smartregister.addo.activity.FamilyFocusedMemberProfileActivity.ANC_DANGER_SIGN_SCREENING_ENCOUNTER;
import static org.smartregister.addo.activity.FamilyFocusedMemberProfileActivity.CHILD_DANGER_SIGN_SCREENING_ENCOUNTER;
import static org.smartregister.addo.activity.FamilyFocusedMemberProfileActivity.PNC_DANGER_SIGN_SCREENING_ENCOUNTER;
import static org.smartregister.addo.util.CoreConstants.JSON_FORM;


public class AddoUtils extends Utils {

    private static final Map<String,String> lookupMap = new HashMap<>();
    static {
        lookupMap.put(CHILD_DANGER_SIGN_SCREENING_ENCOUNTER + "-key", "danger_signs_present_child");
        lookupMap.put(CHILD_DANGER_SIGN_SCREENING_ENCOUNTER + "-client_present", "child_present");
        lookupMap.put(ANC_DANGER_SIGN_SCREENING_ENCOUNTER + "-key", "danger_signs_present");
        lookupMap.put(ANC_DANGER_SIGN_SCREENING_ENCOUNTER + "-client_present", "pregnant_woman_present");
        lookupMap.put(PNC_DANGER_SIGN_SCREENING_ENCOUNTER + "-key", "danger_signs_present_mama");
        lookupMap.put(PNC_DANGER_SIGN_SCREENING_ENCOUNTER + "-client_present", "mother_present");
        lookupMap.put(ADOLESCENT_SCREENING_ENCOUNTER + "-key", "adolescent_condition_present");
        lookupMap.put(ADOLESCENT_SCREENING_ENCOUNTER + "-client_present", "adolescent_present");
    }


    public static String checkDSPresentProposedMedsAndDispense(String jsonForm, Constants.FamilyMemberType familyMemberType) {
        JsonQ form=JsonQ.fromJson(jsonForm);
        if (!isClientPresent(form)) return new FormSyncManager().getFormJson(getFormName(familyMemberType));

        String dangerSignFieldKey=lookupMap.get(form.str(JsonFormUtils.ENCOUNTER_TYPE)+"-key");
        JsonQ dangerSign=form.get("step2.fields[?(@.key='%s')]", dangerSignFieldKey);
        boolean hasDangerSigns = !dangerSign.get(JsonFormUtils.VALUE).isEmpty()
                && !dangerSign.str("value").toLowerCase().contains("chk_none");

        boolean hasBeenReferred = hasDangerSigns
                && form.str("step3.fields[?(@.key='save_n_refer')].value").equalsIgnoreCase("true");

        return dispenseMedication(dangerSign,hasBeenReferred, familyMemberType);
    }

    private static String getDangerSignsString(JsonQ dangerSignsObject) {

        List<String> dangerSignsText = dangerSignsObject.getStrings("options[(@.value==true)].text");
        // We are putting html tags here so as to display it properly on the toaster message
        // The message will be something like:
        // This client has the following danger signs:
        // • Albendazole Suspension/Tablets
        // • Paracetamol tablets
        return dangerSignsText.isEmpty()
                ? null
                :"<br /> \u25FB " + String.join("<br /> \u25FB ", dangerSignsText) + "<br />";

    }

    private static boolean isClientPresent(JsonQ form) {
        String encounter=form.str(JsonFormUtils.ENCOUNTER_TYPE);
        final String PATH = "step1.fields[?(@.key='%s')].value";

        String key=lookupMap.get(encounter+"-client_present");
        String expectedValue="chk_" + lookupMap.get(encounter+"-client_present") + "_yes";

        return form.str(PATH,key).equals(expectedValue);
    }

    private static String getFormName(Constants.FamilyMemberType type){
        switch (type){
            case ANC: case PNC: return JSON_FORM.getDangerSignsMedicationAnc();
            case ADOLESCENT: return  JSON_FORM.getDangerSignsMedicationAdolescent();
            case CHILD: return JSON_FORM.getDangerSignMedicationChild();
            default:
                throw new IllegalArgumentException("Unknown family member type: " + type);
        }
    }
    private static String dispenseMedication(JsonQ dangerSigns, boolean hasBeenReferred, Constants.FamilyMemberType familyMemberType) {
        FormSyncManager uForm = new FormSyncManager();
        JsonQ form = uForm.getForm(getFormName(familyMemberType));
        String path="step1.fields[?(@.key='%s')]";

        String suggestedMeds=AddoApplication.getInstance().getContext().getStringResource(R.string.default_dispense_message);
        String dangerSignStrings=getDangerSignsString(dangerSigns);


        form.get(path,"danger_signs_captured")
                .putNoNull("value", dangerSignStrings);

        form.get(path,"addo_medication_to_give")
                .putNoNull("value", suggestedMeds);

        form.get(path,"referral_status")
                .putNoNull("value", hasBeenReferred? "referred" : null);

        return form.toString();
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
            JSONObject medicationsSelectedFieldJsonObject;

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
        JSONObject field= JsonFormUtils.getFieldJSONObject(fields,lookupMap.get(encounterType + "-key"));
        if(field==null){
            field=new JSONObject();
            Timber.e("Encounter type not recognized: %S", encounterType);
        }
        return field;
    }

    private static JSONObject createReferralFormField(String key, Object value) {
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
}
