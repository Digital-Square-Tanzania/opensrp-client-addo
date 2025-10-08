package org.smartregister.addo.activity;

import static org.smartregister.addo.activity.FamilyFocusedMemberProfileActivity.CHILD_DANGER_SIGN_SCREENING_ENCOUNTER;
import static org.smartregister.family.util.JsonFormUtils.fields;
import static org.smartregister.util.JsonFormUtils.getFieldJSONObject;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;
import com.nerdstone.neatformcore.domain.model.NFormViewData;
import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.domain.Form;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.addo.R;
import org.smartregister.addo.contract.FamilyOtherMemberProfileExtendedContract;
import org.smartregister.addo.custom_views.FamilyMemberFloatingMenu;
import org.smartregister.addo.dao.AdolescentDao;
import org.smartregister.addo.dao.AncDao;
import org.smartregister.addo.dao.FamilyDao;
import org.smartregister.addo.dao.PNCDao;
import org.smartregister.addo.fragment.FamilyOtherMemberProfileFragment;
import org.smartregister.addo.listeners.FloatingMenuListener;
import org.smartregister.addo.presenter.FamilyOtherMemberActivityPresenter;
import org.smartregister.addo.util.CoreConstants;
import org.smartregister.addo.util.CoreJsonFormUtils;
import org.smartregister.addo.util.JsonFormUtils;
import org.smartregister.addo.util.ReferralUtils;
import org.smartregister.chw.anc.domain.MemberObject;
import org.smartregister.commonregistry.CommonPersonObjectClient;
import org.smartregister.domain.tag.FormTag;
import org.smartregister.family.FamilyLibrary;
import org.smartregister.family.activity.BaseFamilyOtherMemberProfileActivity;
import org.smartregister.family.activity.FamilyWizardFormActivity;
import org.smartregister.family.adapter.ViewPagerAdapter;
import org.smartregister.family.fragment.BaseFamilyOtherMemberProfileFragment;
import org.smartregister.family.model.BaseFamilyOtherMemberProfileActivityModel;
import org.smartregister.family.util.Constants;
import org.smartregister.family.util.DBConstants;
import org.smartregister.family.util.Utils;
import org.smartregister.helper.ImageRenderHelper;
import org.smartregister.location.helper.LocationHelper;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.simprint.OnDialogButtonClick;
import org.smartregister.util.FormUtils;
import org.smartregister.view.fragment.BaseRegisterFragment;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import timber.log.Timber;

public class FamilyOtherMemberProfileActivity extends BaseFamilyOtherMemberProfileActivity implements FamilyOtherMemberProfileExtendedContract.View {

    private String familyBaseEntityId;
    private String baseEntityId;
    private String familyHead;
    private String primaryCaregiver;
    private String villageTown;
    private String familyName;
    private String PhoneNumber;
    private CommonPersonObjectClient commonPersonObject;
    private FamilyMemberFloatingMenu familyFloatingMenu;
    private TextView textViewDangersignScreening;
    private ImageView imageView;
    protected MemberObject memberObject;
    private FormUtils formUtils;
    public static final String REFERRAL_BUSINESS_STATUS = "PENDING";
    public static final String REFERRAL_TYPE = "addo_to_facility_referral";
    public static final String ADDO_VISIT_OTHER_CLIENTS = "Addo Visit - Other Clients";
    public static final String DIABETES_AND_HYPERTENSION_SCREENING = "Diabetes and Hypertension Screening";

    public static final String CHILD_DANGER_SIGN_SCREENING_ENCOUNTER = "Child Danger Signs";
    public static final String ANC_DANGER_SIGN_SCREENING_ENCOUNTER = "ANC Danger Signs";
    public static final String PNC_DANGER_SIGN_SCREENING_ENCOUNTER = "PNC Danger Signs";
    public static final String ADOLESCENT_SCREENING_ENCOUNTER = "Adolescent Addo Screening";


    @Override
    protected void onCreation() {
        setContentView(R.layout.activity_family_other_member_profile_addo);

        Toolbar toolbar = findViewById(R.id.addo_other_family_member_toolbar);
        this.setSupportActionBar(toolbar);

        ActionBar actionBar = this.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            final Drawable backArrow = getResources().getDrawable(R.drawable.ic_arrow_back_white_24dp);
            backArrow.setColorFilter(getResources().getColor(R.color.addo_primary), PorterDuff.Mode.SRC_ATOP);
            actionBar.setHomeAsUpIndicator(backArrow);
            actionBar.setTitle("");
        }

        this.appBarLayout = findViewById(R.id.toolbar_appbarlayout_addo_non_focused);

        this.imageRenderHelper = new ImageRenderHelper(this);

        initializePresenter();

        setupViews();
    }

    @Override
    protected void initializePresenter() {
        commonPersonObject = (CommonPersonObjectClient) getIntent().getSerializableExtra(org.smartregister.addo.util.Constants.INTENT_KEY.CHILD_COMMON_PERSON);
        familyBaseEntityId = getIntent().getStringExtra(Constants.INTENT_KEY.FAMILY_BASE_ENTITY_ID);
        baseEntityId = getIntent().getStringExtra(Constants.INTENT_KEY.BASE_ENTITY_ID);
        familyHead = getIntent().getStringExtra(Constants.INTENT_KEY.FAMILY_HEAD);
        primaryCaregiver = getIntent().getStringExtra(Constants.INTENT_KEY.PRIMARY_CAREGIVER);
        villageTown = getIntent().getStringExtra(org.smartregister.addo.util.Constants.INTENT_KEY.VILLAGE_SELECTED);
        familyName = getIntent().getStringExtra(Constants.INTENT_KEY.FAMILY_NAME);
        PhoneNumber = commonPersonObject.getColumnmaps().get(org.smartregister.addo.util.Constants.JsonAssets.FAMILY_MEMBER.PHONE_NUMBER);
        memberObject = new MemberObject(commonPersonObject);
        presenter = new FamilyOtherMemberActivityPresenter(this, new BaseFamilyOtherMemberProfileActivityModel(), null, familyBaseEntityId, baseEntityId, familyHead, primaryCaregiver, villageTown, familyName);

        //TODO: Include flavor to implement
        //onClickFloatingMenu = flavor.getOnClickFloatingMenu(this, familyBaseEntityId);

    }

    @Override
    protected void setupViews() {
        super.setupViews();

        textViewDangersignScreening = findViewById(R.id.textview_ds_screening);
        imageView = findViewById(R.id.imageview_profile);

        TextView toolbarTitle = findViewById(R.id.toolbar_title);
        if(presenter().getFamilyName() == null) {
            toolbarTitle.setText(getString(R.string.search_results_return));
        } else {
            toolbarTitle.setText(String.format(getString(R.string.return_to_family_name), presenter().getFamilyName()));
        }

        TabLayout tabLayout = findViewById(R.id.tabs);
        tabLayout.setSelectedTabIndicatorHeight(0);

        findViewById(R.id.viewpager).setVisibility(View.GONE);

        textViewDangersignScreening.setOnClickListener(this);

    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void setProfileDetailOne(String detailOne) {
        super.setProfileDetailOne(org.smartregister.addo.util.Utils.getTranslatedGender(detailOne));
    }

    @Override
    protected ViewPager setupViewPager(ViewPager viewPager) {
        adapter = new ViewPagerAdapter(getSupportFragmentManager());
        BaseFamilyOtherMemberProfileFragment profileOtherMemberFragment = FamilyOtherMemberProfileFragment.newInstance(this.getIntent().getExtras());
        adapter.addFragment(profileOtherMemberFragment, "");

        viewPager.setAdapter(adapter);

        return viewPager;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuItem addMember = menu.findItem(R.id.add_member);
        if (addMember != null) {
            addMember.setVisible(false);
        }
        int age = getPersonAge(commonPersonObject);
        // Remove the the Option Menu for the ADDO application, can be activated by uncommenting this line
//        getMenuInflater().inflate(R.menu.other_member_menu, menu);

        if (age >= 30) {
            getMenuInflater().inflate(R.menu.other_member_menu, menu);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_diabetes_hypertension_screening) {
            // ✅ Handle menu item click here
//            Toast.makeText(this, "Screening selected", Toast.LENGTH_SHORT).show();
            startDiabetesRiskAssessment();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public FamilyOtherMemberActivityPresenter presenter() {
        return (FamilyOtherMemberActivityPresenter) presenter;
    }

    @Override
    public void setProfileImage(String baseEntityId, String entityType) {
        this.imageRenderHelper.refreshProfileImage(baseEntityId, this.imageView, Utils.getMemberProfileImageResourceIDentifier(entityType));
    }

    public void startFormActivity(JSONObject jsonForm, String formTitle) {
        Form form = new Form();
        form.setName(formTitle);
        form.setActionBarBackground(R.color.family_actionbar);
        form.setHomeAsUpIndicator(R.mipmap.ic_cross_white);
        form.setHideSaveLabel(true);
        form.setWizard(false);

        Intent intent = new Intent(this, FamilyWizardFormActivity.class);
        intent.putExtra(org.smartregister.family.util.Constants.JSON_FORM_EXTRA.JSON, jsonForm.toString());
        intent.putExtra(Constants.WizardFormActivity.EnableOnCloseDialog, false);
        intent.putExtra(JsonFormConstants.JSON_FORM_KEY.FORM, form);
        intent.putExtra(JsonFormConstants.PERFORM_FORM_TRANSLATION, true);
        startActivityForResult(intent, org.smartregister.family.util.JsonFormUtils.REQUEST_CODE_GET_JSON);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == org.smartregister.addo.util.JsonFormUtils.REQUEST_CODE_GET_JSON) {
            try {
                String jsonString = data.getStringExtra(Constants.JSON_FORM_EXTRA.JSON);
                JSONObject form = new JSONObject(jsonString);


                AllSharedPreferences allSharedPreferences = org.smartregister.util.Utils.getAllSharedPreferences();
                // complete any linkage first
                ReferralUtils.closeLinkageAndOpenFollowUp(baseEntityId, villageTown);

                String encounterType = form.optString(JsonFormUtils.ENCOUNTER_TYPE);
                if (encounterType.equalsIgnoreCase(ADDO_VISIT_OTHER_CLIENTS)) {

                    Map<String, String> formSubmission = new HashMap<>();
                    formSubmission.put(form.optString(CoreJsonFormUtils.ENCOUNTER_TYPE), jsonString);
                    submitForm(formSubmission);

                } else if (encounterType.equalsIgnoreCase(DIABETES_AND_HYPERTENSION_SCREENING)) {
                    // Submit the Addo  Diabetes Screening  Event
                    Map<String, String> formSubmission = new HashMap<>();
                    formSubmission.put(form.optString(CoreJsonFormUtils.ENCOUNTER_TYPE), jsonString);
                    submitForm(formSubmission);

                    //check if client is being referred
                    JSONArray a = form.getJSONObject("step4").getJSONArray("fields");
                    String buttonAction = "";

                    for (int i = 0; i < a.length(); i++) {
                        org.json.JSONObject jo = a.getJSONObject(i);
                        if (jo.getString("key").compareToIgnoreCase("save_n_refer") == 0) {
                            if (jo.optString("value") != null && jo.optString("value").compareToIgnoreCase("true") == 0) {
                                buttonAction = jo.getJSONObject("action").getString("behaviour");
                            }
                        }
                    }

                    if (!buttonAction.isEmpty()) {
                        String facilityValue = JsonFormUtils.getValue(form, "chw_referral_hf");
                        FormTag formTag = formTag(allSharedPreferences);

                        presenter().submitDiabetesAndHypertensionScreeningEvent(baseEntityId, getDiabetesAndHypertensionScreeningObs(form),
                                formTag, villageTown, DIABETES_AND_HYPERTENSION_SCREENING);

                        // Check if the client has referral already or not
                        if (ReferralUtils.hasReferralTask(CoreConstants.REFERRAL_PLAN_ID_2, facilityValue, baseEntityId, CoreConstants.JsonAssets.REFERRAL_CODE)) {
                            closeOpenNewReferral(this, new OnDialogButtonClick() {
                                @Override
                                public void onOkButtonClick() {
                                    // Close referral
                                    FamilyDao.archiveHFTasksForEntity(baseEntityId);

                                    // Open a new referral
                                    ReferralUtils.createReferralTask(baseEntityId, "Diabetes And Hypertension Testing", jsonString, villageTown, facilityValue, formTag.formSubmissionId);

                                    // Create a referral event
                                    presenter().submitReferralEvent(baseEntityId, createReferralForm(jsonString, encounterType), formTag);
                                }

                                @Override
                                public void onCancelButtonClick() {
                                    checkDSPresentProposedMedsAndDispense(form);
                                }
                            });
                        } else {
                            //refer
                            ReferralUtils.createReferralTask(baseEntityId, "Diabetes And Hypertension Testing", jsonString, villageTown, facilityValue, formTag.formSubmissionId);
                            // Create a referral event
                            presenter().submitReferralEvent(baseEntityId, createReferralForm(jsonString, encounterType), formTag);
                        }

                    } else {
                        checkDSPresentProposedMedsAndDispense(form);
                    }
                }
            } catch (JSONException e) {
                Timber.e(e);
            }
        }
    }

    public static JSONArray getDiabetesAndHypertensionScreeningObs(JSONObject jsonForm) {
        try {
            JSONArray allFields = new JSONArray();
            if (jsonForm == null) return null;

            for (Iterator<String> it = jsonForm.keys(); it.hasNext(); ) {
                String key = it.next();
                if (key.matches("step\\d+")) {
                    JSONArray fields = fields(jsonForm, key);
                    if (fields != null) {
                        for (int i = 0; i < fields.length(); i++) {
                            allFields.put(fields.get(i));
                        }
                    }
                }
            }
            return allFields;
        } catch (JSONException e) {
            Log.e("FamilyOtherMemberProfileActivity", "", e);
        }
        return null;
    }

    public void submitForm(Map<String, String> formForSubmission) {
        presenter().submitVisit(formForSubmission);
    }

    @Override
    protected void onResumption() {
        super.onResumption();
        FloatingMenuListener.getInstance(this, presenter().getFamilyBaseEntityId());
    }

    public void refreshList() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            for (int i = 0; i < adapter.getCount(); i++) {
                refreshList(adapter.getItem(i));
            }
        } else {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                public void run() {
                    for (int i = 0; i < adapter.getCount(); i++) {
                        refreshList(adapter.getItem(i));
                    }
                }
            });
        }
    }

    private void refreshList(Fragment fragment) {
        if (fragment instanceof BaseRegisterFragment && fragment instanceof FamilyOtherMemberProfileFragment) {
            FamilyOtherMemberProfileFragment familyOtherMemberProfileFragment = ((FamilyOtherMemberProfileFragment) fragment);
            if (familyOtherMemberProfileFragment.presenter() != null) {
                familyOtherMemberProfileFragment.refreshListView();
            }
        }
    }

    @Override
    public void onClick(View view) {

        switch (view.getId()) {
            case R.id.family_has_row:

                break;

            case R.id.textview_ds_screening:

                startRecordServiceProvided();

            default:
                super.onClick(view);
                break;
        }
    }

    private void startRecordServiceProvided() {
        startFormActivity(getFormUtils().getFormJson(CoreConstants.JSON_FORM.getAddoRecordServiceOther()), getResources().getString(R.string.non_focused_service_provided));
    }


    private FormUtils getFormUtils() {
        if (formUtils == null) {
            try {
                formUtils = FormUtils.getInstance(Utils.context().applicationContext());
            } catch (Exception e) {
                Timber.e(e);
            }
        }
        return formUtils;
    }

    private static int getPersonAge(CommonPersonObjectClient commonPersonObject) {
        String dob = org.smartregister.addo.util.Utils.getValue(commonPersonObject.getColumnmaps(), DBConstants.KEY.DOB, false);
        return org.smartregister.addo.util.Utils.getAgeFromDate(dob);
    }
    private void startDiabetesRiskAssessment() {
        try {
            JSONObject formJsonObject = getFormUtils().getFormJson(CoreConstants.JSON_FORM.getDiabetesScreeningForm());
            prepopulateDiabetesScreeningForm(formJsonObject);
            assert formJsonObject != null;
            startNcdFormActivity(formJsonObject, getResources().getString(R.string.diabetes_and_hypertension_screening_form_title), true);
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    private void prepopulateDiabetesScreeningForm(JSONObject formJsonObject) throws JSONException {

        int age = Utils.getAgeFromDate(Utils.getValue(commonPersonObject.getColumnmaps(), DBConstants.KEY.DOB, false));
        // Populate Client age
        JSONArray step3Fields = fields(formJsonObject, "step3");
        JSONObject ageField = getFieldJSONObject(step3Fields, "age");

        if (ageField != null) {
            ageField.put("value", age);
        }
        formJsonObject.getJSONObject(JsonFormConstants.GLOBAL).put("age", age);
    }

    public void startNcdFormActivity(JSONObject jsonForm, String formTitle, boolean displayHF) {
        Form form = new Form();
        form.setName(formTitle);
        form.setActionBarBackground(R.color.family_actionbar);
        form.setNavigationBackground(R.color.family_navigation);
        form.setHomeAsUpIndicator(R.mipmap.ic_cross_white);
        form.setSaveLabel("FINISH");
        form.setHideSaveLabel(true);
        form.setWizard(true);

        if(displayHF) displayReferralFacilities(jsonForm);

        Intent intent = new Intent(this, NcdFormWizardActivity.class);
        intent.putExtra(org.smartregister.family.util.Constants.JSON_FORM_EXTRA.JSON, jsonForm.toString());
        intent.putExtra(Constants.WizardFormActivity.EnableOnCloseDialog, false);
        intent.putExtra(JsonFormConstants.JSON_FORM_KEY.FORM, form);
        intent.putExtra(JsonFormConstants.PERFORM_FORM_TRANSLATION, true);
        startActivityForResult(intent, org.smartregister.family.util.JsonFormUtils.REQUEST_CODE_GET_JSON);
    }

    public void displayReferralFacilities(JSONObject jsonForm) {
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
        }catch (JSONException e){
            Timber.e(e);
        }
    }

    public JSONArray createReferralForm(String jsonString, String encounterType) {
        try{
            JSONObject form = new JSONObject(jsonString);
            JSONArray referralFormArray = form.getJSONObject("step4").getJSONArray("fields");
            JSONArray fields = JsonFormUtils.fields(form);


            // Service before referral
            String serviceBReferralValue = JsonFormUtils.getValue(form, "service_before_referral");
            JSONArray serviceBReferralArray = new JSONArray(serviceBReferralValue);
            HashMap<String, NFormViewData> serviceBReferralNFormValue = new HashMap<>();
            for(int i=0; i < serviceBReferralArray.length(); i++){
                String serviceValue = serviceBReferralArray.getString(i);
                NFormViewData valueItem = createFormViewData( serviceValue, null, metaData("", serviceValue, ""));
                serviceBReferralNFormValue.put(serviceValue, valueItem);
            }
            referralFormArray.put(createReferralFormField("service_before_referral",
                    createFormViewData(serviceBReferralNFormValue, "MultiChoiceCheckBox",
                            metaData("concept", "service_before_referral", ""))));

            // Diabetes risk score
            String dbRiskScore = JsonFormUtils.getValue(new JSONObject(jsonString), "diabetes_risk_score_output");
            referralFormArray.put(createReferralFormField("diabetes_risk_score", createFormViewData(dbRiskScore,"Calculation",null)));


            //Convert referral appointment date to timestamp
            convertAppointmentDate(fields);

            //Add other referral form fields
            referralFormArray.put(createReferralFormField("referral_status", REFERRAL_BUSINESS_STATUS));
            referralFormArray.put(createReferralFormField("chw_referral_service", "Diabetes and Hypertension Screening"));
            referralFormArray.put(createReferralFormField("referral_date", Long.toString(Calendar.getInstance().getTimeInMillis())));
            referralFormArray.put(createReferralFormField("referral_type", REFERRAL_TYPE));
            referralFormArray.put(createReferralFormField("referral_time", referralTime()));

            // Remove unwanted fields
            removeFieldsFromJSONArray(referralFormArray, "asterisk_symbol", "save_n_refer");

            return  referralFormArray;
        }catch (JSONException e){
            Timber.e(e);
        }
        return  null;
    }

    private JSONObject createReferralFormField(String key, Object value) {
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

    private void convertAppointmentDate(JSONArray fields) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
            JSONObject appointment_date = JsonFormUtils.getFieldJSONObject(fields, "referral_appointment_date");
            String value = appointment_date.getString(JsonFormUtils.VALUE);
            Date formattedDate = dateFormat.parse(value);
            assert formattedDate != null;
            long unixTimestamp = formattedDate.getTime();
            appointment_date.put("value", String.valueOf(unixTimestamp));
        }catch (Exception e){
            Timber.e(e);
        }
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

    private void closeOpenNewReferral(Context context, final OnDialogButtonClick onDialogButtonClick) {
        final AlertDialog alert = new AlertDialog.Builder(context).create();
        alert.setMessage(getString(R.string.do_you_want_to_give_another_referral));
        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.yes), (dialog, which) -> {
            //Toast.makeText(context, context.getResources().getString(R.string.referral_submitted), Toast.LENGTH_LONG).show();
            onDialogButtonClick.onOkButtonClick();
            alert.dismiss();
        });
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.no), (dialog, which) -> {
            onDialogButtonClick.onCancelButtonClick();
            alert.dismiss();
        });
        alert.show();
    }

    public String referralTime(){
        Date now = new Date();
        DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
        return dateFormat.format(now);
    }


    private FormTag formTag(AllSharedPreferences allSharedPreferences) {
        FormTag formTag = new FormTag();
        formTag.providerId = allSharedPreferences.fetchRegisteredANM();
        formTag.appVersion = FamilyLibrary.getInstance().getApplicationVersion();
        formTag.databaseVersion = FamilyLibrary.getInstance().getDatabaseVersion();
        formTag.team = allSharedPreferences.fetchDefaultTeam(allSharedPreferences.fetchRegisteredANM());
        formTag.teamId = allSharedPreferences.fetchDefaultTeamId(allSharedPreferences.fetchRegisteredANM());
        formTag.locationId = LocationHelper.getInstance().getOpenMrsLocationId(villageTown);
        formTag.formSubmissionId = UUID.randomUUID().toString();
        return formTag;
    }

    private void checkDSPresentProposedMedsAndDispense(JSONObject form) {

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
                        suggestedMeds = getResources().getString(R.string.default_dispense_message);
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

                    dispenseMedication(dangerSigns, suggestedMeds, referralStatus);
                } else if (dangerSignsSelected.getString(0).equalsIgnoreCase("chk_none")) {
                    dispenseMedication(null, getResources().getString(R.string.default_dispense_message), null);
                }
            } else {
                dispenseMedication(null, null, null);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private Boolean isClientPresent(@NotNull JSONObject form) {
//        try {
//            JSONObject step1 = form.getJSONObject(JsonFormUtils.STEP1);
//            JSONArray step1Fields = step1.getJSONArray(JsonFormUtils.FIELDS);
//            if (form.optString(JsonFormUtils.ENCOUNTER_TYPE).equalsIgnoreCase(CHILD_DANGER_SIGN_SCREENING_ENCOUNTER)) {
//                return JsonFormUtils.getFieldJSONObject(step1Fields, "child_present").getJSONArray(JsonFormUtils.VALUE).get(0).toString().equals("chk_child_present_yes");
//            } else if (form.optString(JsonFormUtils.ENCOUNTER_TYPE).equalsIgnoreCase(ANC_DANGER_SIGN_SCREENING_ENCOUNTER)) {
//                return JsonFormUtils.getFieldJSONObject(step1Fields, "pregnant_woman_present").getJSONArray(JsonFormUtils.VALUE).get(0).toString().equals("chk_pregnant_woman_present_yes");
//            } else if (form.optString(JsonFormUtils.ENCOUNTER_TYPE).equalsIgnoreCase(PNC_DANGER_SIGN_SCREENING_ENCOUNTER)) {
//                return JsonFormUtils.getFieldJSONObject(step1Fields, "mother_present").getJSONArray(JsonFormUtils.VALUE).get(0).toString().equals("chk_mother_present_yes");
//            } else {
//                return JsonFormUtils.getFieldJSONObject(step1Fields, "adolescent_present").getJSONArray(JsonFormUtils.VALUE).get(0).equals("adolescent_present_yes");
//            }
//
//        } catch (JSONException e) {
//            Timber.e(e);
//        }
        return false;
    }
    private void dispenseMedication(String dangerSigns, String suggestedMeds, String referralStatus) {
        try {
            JSONObject form = new JSONObject();
            // ANC and PNC have the same Dispense Medication Form
            if(isAncClient() || isPncClient()) {
                form = getFormUtils().getFormJson(CoreConstants.JSON_FORM.getDangerSignsMedicationAnc());
            } else if(isAdolescentClient()) {
                form = getFormUtils().getFormJson(CoreConstants.JSON_FORM.getDangerSignsMedicationAdolescent());
            } else {
                form = getFormUtils().getFormJson(CoreConstants.JSON_FORM.getDangerSignMedicationChild());
            }
            JSONObject stepOne = form.getJSONObject(JsonFormUtils.STEP1);
            JSONArray fields = stepOne.getJSONArray(JsonFormUtils.FIELDS);
            updateFormField(fields, "danger_signs_captured", dangerSigns);
            updateFormField(fields, "addo_medication_to_give", suggestedMeds);
            updateFormField(fields, "referral_status", referralStatus);
            startFormActivity(form, getResources().getString(R.string.dispense_medication_title), false);
        } catch (JSONException e) {
            Timber.e(e);
        }
    }

    private JSONArray getDangerSignsSelected(JSONObject form, JSONArray stepFields) {
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

    public boolean isAncClient() {
        return AncDao.isANCMember(baseEntityId);
    }

    public boolean isPncClient() {
        return PNCDao.isPNCMember(baseEntityId);
    }

    public boolean isAdolescentClient() {
        return AdolescentDao.isAdolescentMember(baseEntityId);
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


    // TODO: 10/3/25 MARIO TO INVESTIGATE HOW TO HANDLE ERROR ON THIS  
    public void startFormActivity(JSONObject jsonForm, String formTitle, boolean displayHF) {
        Form form = new Form();
        form.setName(formTitle);
        form.setActionBarBackground(R.color.family_actionbar);
        form.setNavigationBackground(R.color.family_navigation);
        form.setHomeAsUpIndicator(R.mipmap.ic_cross_white);
        form.setSaveLabel("FINISH");
        form.setHideSaveLabel(true);
        form.setWizard(true);

        if(displayHF) displayReferralFacilities(jsonForm);

        Intent intent = new Intent(this, ReferralWizardFormActivity.class);
        intent.putExtra(org.smartregister.family.util.Constants.JSON_FORM_EXTRA.JSON, jsonForm.toString());
        intent.putExtra(Constants.WizardFormActivity.EnableOnCloseDialog, false);
        intent.putExtra(JsonFormConstants.JSON_FORM_KEY.FORM, form);
        intent.putExtra(JsonFormConstants.PERFORM_FORM_TRANSLATION, true);
        startActivityForResult(intent, org.smartregister.family.util.JsonFormUtils.REQUEST_CODE_GET_JSON);
    }

    private NFormViewData createFormViewData(Object value, String type, HashMap<String, Object> metaData) {
        NFormViewData data = new NFormViewData();
        data.setValue(value);
        data.setType(type);
        data.setVisible(true);
        data.setMetadata(metaData);
        return data;
    }

    private HashMap<String, Object> metaData(String openmrs_entity,String openmrs_entity_id, String openmrs_entity_parent) {
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("openmrs_entity", openmrs_entity);
        metadata.put("openmrs_entity_id", openmrs_entity_id);
        metadata.put("openmrs_entity_parent", openmrs_entity_parent);
        return metadata;
    }
}
