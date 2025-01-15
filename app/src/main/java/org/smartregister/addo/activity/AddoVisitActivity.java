package org.smartregister.addo.activity;

import static org.smartregister.chw.anc.util.Constants.ANC_MEMBER_OBJECTS.BASE_ENTITY_ID;
import static org.smartregister.chw.anc.util.Constants.ANC_MEMBER_OBJECTS.EDIT_MODE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.domain.Form;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.AllConstants;
import org.smartregister.addo.R;
import org.smartregister.addo.application.AddoApplication;
import org.smartregister.addo.contract.ToastCallback;
import org.smartregister.addo.domain.DukaLaDawaPayload;
import org.smartregister.addo.interactor.AddoVisitInteractor;
import org.smartregister.chw.anc.AncLibrary;
import org.smartregister.chw.anc.activity.BaseAncHomeVisitActivity;
import org.smartregister.chw.anc.domain.MemberObject;
import org.smartregister.chw.anc.model.BaseAncHomeVisitAction;
import org.smartregister.chw.anc.presenter.BaseAncHomeVisitPresenter;
import org.smartregister.family.util.Constants;
import org.smartregister.family.util.JsonFormUtils;
import org.smartregister.family.util.Utils;
import org.smartregister.util.FormUtils;
import org.smartregister.util.LangUtils;
import org.smartregister.addo.util.Constants.FamilyMemberType;

import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Locale;

import timber.log.Timber;

public class AddoVisitActivity extends BaseAncHomeVisitActivity implements ToastCallback {

    protected FamilyMemberType clientType;

    private String villageTown;

    private String prescriptionNote;

    private String clientGender;

    StringBuilder medicationsSelectedString = new StringBuilder();

    public static void startMe(Activity activity, MemberObject memberObject, boolean isEditMode, FamilyMemberType familyMemberType, String villageTown, String gender){
        Intent intent = new Intent(activity, AddoVisitActivity.class);
        intent.putExtra("MemberObject", memberObject);
        intent.putExtra(BASE_ENTITY_ID, memberObject.getBaseEntityId());
        intent.putExtra(EDIT_MODE, isEditMode);
        intent.putExtra("family_member_type", familyMemberType.name());
        intent.putExtra("villageTown", villageTown);
        intent.putExtra("gender", gender);
        activity.startActivityForResult(intent, org.smartregister.chw.anc.util.Constants.REQUEST_CODE_HOME_VISIT);
    }

    @Override
    public void showToastInInteractor(int resId) {
        runOnUiThread(() -> { Toast.makeText(this, resId, Toast.LENGTH_LONG).show(); });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        baseEntityID = this.getIntent().getStringExtra(BASE_ENTITY_ID);
        clientType = FamilyMemberType.valueOf(this.getIntent().getStringExtra("family_member_type"));
        villageTown = this.getIntent().getStringExtra("villageTown");
        clientGender = this.getIntent().getStringExtra("gender");
        super.onCreate(savedInstanceState);
    }

    @Override
    public void redrawHeader(MemberObject memberObject) {
        String visitType = "";

        if (clientType.equals(FamilyMemberType.CHILD)) {
            visitType = this.getString(R.string.child_visit);
        } else if (clientType.equals(FamilyMemberType.ANC)) {
            visitType = this.getString(R.string.anc_visit);
        } else if (clientType.equals(FamilyMemberType.PNC)) {
            visitType = this.getString(R.string.pnc_visit);
        } else if (clientType.equals(FamilyMemberType.ADOLESCENT)) {
            visitType = this.getString(R.string.adolescent_visit);
        }

        this.tvTitle.setText(MessageFormat.format("{0}, {1} · {2}", memberObject.getFullName(), memberObject.getAge(), visitType));
    }

    @Override
    protected void registerPresenter() {
        presenter = new BaseAncHomeVisitPresenter(
                memberObject,
                this,
                new AddoVisitInteractor(clientType, villageTown, this)//Interactor instance here
        );
    }

    @Override
    public void submittedAndClose() {
        super.submittedAndClose();
        /** HANDLE SUBMITTED AND CLOSED
         *  Runnable runnable = () ->  ChwScheduleTaskExecutor.getInstance().execute(memberObject.getBaseEntityId(), CoreConstants.EventType.ANC_HOME_VISIT, new Date());
         *         org.smartregister.chw.util.Utils.startAsyncTask(new RunnableTask(runnable), null);
         *         if (originatesFromAncRegister) {
         *             startAncRegisterActivity();
         *         } else {
         *             super.submittedAndClose();
         *         }
         */
    }

    @Override
    public void startFormActivity(JSONObject jsonForm) {
        Form form = new Form();
        String formTitle = getString(R.string.addo_visit);
        form.setName(formTitle);
        form.setActionBarBackground(R.color.family_actionbar);
        form.setNavigationBackground(R.color.family_navigation);
        form.setHomeAsUpIndicator(R.mipmap.ic_cross_white);
        form.setWizard(true);

        Intent intent = new Intent(this, ReferralWizardFormActivity.class);
        intent.putExtra(Constants.JSON_FORM_EXTRA.JSON, jsonForm.toString());
        intent.putExtra(Constants.WizardFormActivity.EnableOnCloseDialog, false);
        intent.putExtra(JsonFormConstants.JSON_FORM_KEY.FORM, form);
        intent.putExtra(Constants.INTENT_KEY.BASE_ENTITY_ID, baseEntityID);
        intent.putExtra(JsonFormConstants.PERFORM_FORM_TRANSLATION, true);
        startActivityForResult(intent, JsonFormUtils.REQUEST_CODE_GET_JSON);
    }

    @Override
    protected void attachBaseContext(Context base) {
        String lang = LangUtils.getLanguage(base.getApplicationContext());
        super.attachBaseContext(LangUtils.setAppLocale(base, lang));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == org.smartregister.chw.anc.util.Constants.REQUEST_CODE_GET_JSON) {
            BaseAncHomeVisitAction ancHomeVisitAction = actionList.get(current_action);

            if (resultCode == Activity.RESULT_OK) {
                try {
                    String jsonString = data.getStringExtra(org.smartregister.chw.anc.util.Constants.JSON_FORM_EXTRA.JSON);

                    if (jsonString != null && ancHomeVisitAction != null) {
                        if (current_action.equals(AddoApplication.getInstance().getContext().getStringResource(R.string.evalueate_medication_dispensed))) {
                            jsonString = createMedicationDispenseForm(new JSONObject(jsonString));
                            if (jsonString == null) jsonString = ancHomeVisitAction.getJsonPayload();
                        } else if (current_action.equals(AddoApplication.getInstance().getContext().getStringResource(R.string.evalueate_prescription_note))) {
                            getPrescriptionNote(jsonString);
                        }

                        ancHomeVisitAction.setJsonPayload(jsonString);
                    }
                } catch (Exception e) {
                    Timber.e(e);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {

                if (ancHomeVisitAction != null) {
                    ancHomeVisitAction.evaluateStatus();
                }
            }
        }

        // update the adapter after every payload
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
            redrawVisitUI();
        }
    }

    public void getPrescriptionNote(String jsonString){
        try {
            assert jsonString != null;
            JSONArray prescriptionFormFields = JsonFormUtils.fields(new JSONObject(jsonString));
            String prescriptionNoteValue = JsonFormUtils.getFieldValue(prescriptionFormFields, "client_prescription_note_available");
            prescriptionNote = prescriptionNoteValue.contains("client_prescription_yes") ? "Yes" : "No";
        } catch (JSONException e){
            Timber.e(e);
        }
    }

    @Override
    public void initializeActions(LinkedHashMap<String, BaseAncHomeVisitAction> map) {
        actionList.clear();
        actionList.putAll(map);

        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        displayProgressBar(false);
        redrawVisitUI();
    }

    public void startForm(BaseAncHomeVisitAction ancHomeVisitAction) {
        if (StringUtils.isNotBlank(ancHomeVisitAction.getJsonPayload())) {
            try {
                current_action = ancHomeVisitAction.getTitle();
                if(current_action.equals(AddoApplication.getInstance().getContext().getStringResource(R.string.evalueate_medication_dispensed))){
                    launchDukaLaDawaApp();
                }else{
                    JSONObject jsonObject = new JSONObject(ancHomeVisitAction.getJsonPayload());
                    startFormActivity(jsonObject);
                }
            } catch (Exception e) {
                Timber.e(e);
                String locationId = AncLibrary.getInstance().context().allSharedPreferences().getPreference(AllConstants.CURRENT_LOCATION_ID);
                presenter().startForm(ancHomeVisitAction.getFormName(), memberObject.getBaseEntityId(), locationId);
            }
        } else {
            String locationId = AncLibrary.getInstance().context().allSharedPreferences().getPreference(AllConstants.CURRENT_LOCATION_ID);
            presenter().startForm(ancHomeVisitAction.getFormName(), memberObject.getBaseEntityId(), locationId);
        }

    }

    private void launchDukaLaDawaApp(){
        Gson gson = new Gson();
        MemberObject object = memberObject;

        Locale currentLocale = AddoApplication.getCurrentLocale();
        String language = currentLocale != null ? currentLocale.getLanguage() : Locale.getDefault().getLanguage();

        DukaLaDawaPayload dukaLaDawaPayload = new DukaLaDawaPayload(object.getBaseEntityId(), object.getDob(), clientGender, prescriptionNote);
        String payLoad = gson.toJson(dukaLaDawaPayload);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("addopharmacy://salesregister?data=" + Uri.encode(payLoad)));
        startActivityForResult(intent, JsonFormUtils.REQUEST_CODE_GET_JSON);
    }

    private String createMedicationDispenseForm(JSONObject medicationJsonObject) {
        try {
            JSONObject medicationForm = FormUtils.getInstance(Utils.context().applicationContext()).getFormJson("duka_medicine_dispensed");
            JSONArray formFields = org.smartregister.addo.util.JsonFormUtils.fields(medicationForm);

            JSONObject medicineDispensedFormJsonObject = JsonFormUtils.getFieldJSONObject(formFields,"medicine_dispensed");

            addOptionFields(medicationJsonObject, medicineDispensedFormJsonObject);

            JSONObject medicationsSelectedFormJsonObject = JsonFormUtils.getFieldJSONObject(formFields,"medications_selected");
            medicationsSelectedFormJsonObject.put("value", medicationsSelectedString.toString());

            return medicationForm.toString();
        } catch (Exception e) {
            Timber.e(e);
        }
        return null;
    }

    private void addOptionFields(JSONObject medicineDispensedJsonObjectValue, JSONObject medicineDispensedObject){
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
}
