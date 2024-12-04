package org.smartregister.addo.interactor;

import static org.smartregister.addo.util.AddoUtils.convertToObjectList;
import static org.smartregister.addo.util.AddoUtils.createObsValuesFromFields;
import static org.smartregister.addo.util.AddoUtils.getDangerSignsFieldObject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.addo.R;
import org.smartregister.addo.application.AddoApplication;
import org.smartregister.addo.contract.ToastCallback;
import org.smartregister.addo.dao.FamilyDao;
import org.smartregister.addo.dao.VisitDao;
import org.smartregister.addo.model.ReferralObsValues;
import org.smartregister.addo.util.AddoUtils;
import org.smartregister.addo.util.AddoVisitUtils;
import org.smartregister.addo.util.Constants;
import org.smartregister.addo.util.Constants.FamilyMemberType;
import org.smartregister.addo.util.CoreConstants;
import org.smartregister.addo.util.JsonFormUtils;
import org.smartregister.addo.util.ReferralUtils;
import org.smartregister.chw.anc.contract.BaseAncHomeVisitContract;
import org.smartregister.chw.anc.domain.MemberObject;
import org.smartregister.chw.anc.interactor.BaseAncHomeVisitInteractor;
import org.smartregister.chw.anc.model.BaseAncHomeVisitAction;
import org.smartregister.chw.anc.util.VisitUtils;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;
import org.smartregister.domain.tag.FormTag;
import org.smartregister.family.FamilyLibrary;
import org.smartregister.location.helper.LocationHelper;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.sync.helper.ECSyncHelper;
import org.smartregister.util.DateTimeTypeConverter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import timber.log.Timber;

public class AddoVisitInteractor extends BaseAncHomeVisitInteractor {

    private Flavor flavor = new AddoVisitInteractorFlv();

    private final FamilyMemberType clientType;

    private final String villageTown;
    private ToastCallback toastCallback;

    public static Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .registerTypeAdapter(DateTime.class, new DateTimeTypeConverter()).create();

    public AddoVisitInteractor(FamilyMemberType clientType, String villageTown, ToastCallback toastCallback) {
        this.clientType = clientType;
        this.villageTown = villageTown;
        this.toastCallback = toastCallback;

    }

    public Flavor getFlavor() {
        return flavor;
    }

    public void setFlavor(Flavor flavor) {
        this.flavor = flavor;
    }

    @Override
    public void reloadMemberDetails(String memberID, BaseAncHomeVisitContract.InteractorCallBack callBack) {
        Runnable runnable = () -> {
            MemberObject memberObject = getMemberClient(memberID);
            this.appExecutors.mainThread().execute(() -> {
                callBack.onMemberDetailsReloaded(memberObject);
            });
        };
        this.appExecutors.diskIO().execute(runnable);
    }

    @Override
    public MemberObject getMemberClient(String memberID) {
        return VisitDao.getMember(memberID);
    }

    @Override
    public void calculateActions(BaseAncHomeVisitContract.View view, MemberObject memberObject, BaseAncHomeVisitContract.InteractorCallBack callBack) {
        try {
            AddoVisitUtils.processVisits(memberObject.getBaseEntityId());
        } catch (Exception e) {
            Timber.e(e);
        }

        final Runnable runnable = () -> {
            final LinkedHashMap<String, BaseAncHomeVisitAction> actionList = new LinkedHashMap<>();

            try {
                for (Map.Entry<String, BaseAncHomeVisitAction> entry : flavor.calculateActions(view, memberObject, callBack, clientType).entrySet()) {
                    actionList.put(entry.getKey(), entry.getValue());
                }
            } catch (BaseAncHomeVisitAction.ValidationException e) {
                Timber.e(e);
            }

            appExecutors.mainThread().execute(() -> callBack.preloadActions(actionList));
        };

        appExecutors.diskIO().execute(runnable);
    }

    @Override
    protected String getEncounterType() {
        String encounterType;
        if (clientType.equals(FamilyMemberType.CHILD)) {
            encounterType = CoreConstants.EventType.CHILD_ADDO_VISIT;
        } else if (clientType.equals(FamilyMemberType.ANC)) {
            encounterType = CoreConstants.EventType.ANC_ADDO_VISIT;
        } else if (clientType.equals(FamilyMemberType.PNC)) {
            encounterType = CoreConstants.EventType.PNC_ADDO_VISIT;
        } else if (clientType.equals(FamilyMemberType.ADOLESCENT)) {
            encounterType = CoreConstants.EventType.ADOLESCENT_ADDO_VISIT;
        } else {
            encounterType = "Home Visit";
        }

        return encounterType;
    }

    @Override
    protected String getTableName() {
        String tableName;
        if (clientType.equals(FamilyMemberType.CHILD)) {
            tableName = Constants.TABLE_NAME.CHILD;
        } else if (clientType.equals(FamilyMemberType.ANC)) {
            tableName = Constants.TABLE_NAME.ANC_MEMBER;
        } else if (clientType.equals(FamilyMemberType.PNC)) {
            tableName = Constants.TABLE_NAME.ANC_PREGNANCY_OUTCOME;
        } else if (clientType.equals(FamilyMemberType.ADOLESCENT)) {
            tableName = Constants.TABLE_NAME.ADOLESCENT;
        } else {
            tableName = "ec_family_member";
        }

        return tableName;
    }

    @Override
    protected void prepareEvent(Event baseEvent) {
        if (baseEvent != null) {
            // add anc date obs and last
            List<Object> list = new ArrayList<>();
            list.add(new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date()));
            baseEvent.addObs(new Obs("concept", "text", "addo_visit_encounter_date", "",
                    list, new ArrayList<>(), null, "addo_visit_encounter_date"));
        }
    }

    public interface Flavor {

        LinkedHashMap<String, BaseAncHomeVisitAction> calculateActions(final BaseAncHomeVisitContract.View view,
                                                                       MemberObject memberObject,
                                                                       final BaseAncHomeVisitContract.InteractorCallBack callBack,
                                                                       final FamilyMemberType clientType) throws BaseAncHomeVisitAction.ValidationException;

        void addExtraObs(Event baseEvent);

    }

    protected void submitVisit(boolean editMode, String memberID, Map<String, BaseAncHomeVisitAction> map, String parentEventType) throws Exception {
        super.submitVisit(editMode, memberID, map, parentEventType);

        FormTag formTag = formTag(org.smartregister.util.Utils.getAllSharedPreferences());

        String dangerSignsFormJsonString = "";
        String medicationsFormJsonString = "";

        for (Map.Entry<String, BaseAncHomeVisitAction> entry : map.entrySet()) {
            if (entry.getKey().equals("Danger signs") || entry.getKey().equals("Dalili za hatari")) {
                dangerSignsFormJsonString = entry.getValue().getJsonPayload();
            } else if (entry.getKey().equals("Medicine dispensation") || entry.getKey().equals("Utoaji wa dawa")) {
                medicationsFormJsonString = entry.getValue().getJsonPayload();
            }
        }

        ReferralUtils.closeLinkageAndOpenFollowUp(memberID, villageTown);

        if (!getButtonAction(dangerSignsFormJsonString).isEmpty()) {
            JSONObject dangerSignsFormJsonObject = new JSONObject(dangerSignsFormJsonString);


            String encounterType = dangerSignsFormJsonObject.optString(JsonFormUtils.ENCOUNTER_TYPE);
            JSONArray fields = JsonFormUtils.fields(dangerSignsFormJsonObject);
            JSONObject dangerSignsFieldJsonObject = getDangerSignsFieldObject(fields, encounterType);

            ReferralObsValues problems = createObsValuesFromFields(dangerSignsFieldJsonObject);

            String facilityValue = JsonFormUtils.getValue(dangerSignsFormJsonObject, "chw_referral_hf");
            String facility = facilityValue.substring(2, facilityValue.length() - 2);

            if (ReferralUtils.hasReferralTask(CoreConstants.REFERRAL_PLAN_ID_2, facility, memberID, CoreConstants.JsonAssets.REFERRAL_CODE)) {
                FamilyDao.archiveHFTasksForEntity(memberID);
            }

            ReferralUtils.createReferralTask(memberID,
                    dangerSignsFormJsonObject.optString(org.smartregister.chw.anc.util.Constants.ENCOUNTER_TYPE),
                    dangerSignsFormJsonString,
                    villageTown,
                    facility,
                    formTag.formSubmissionId);

            ReferralObsValues medicationsValues = new ReferralObsValues(new ArrayList<String>(), new ArrayList<String>());
            if (StringUtils.isNotEmpty(medicationsFormJsonString)) {

                JSONObject medicationsFormJsonObject = new JSONObject(medicationsFormJsonString);
                JSONArray medicationsFormFields = JsonFormUtils.fields(medicationsFormJsonObject);
                JSONObject medicatoinsFieldJsonObject = JsonFormUtils.getFieldJSONObject(medicationsFormFields, "medicine_dispensed");
                medicationsValues = createObsValuesFromFields(medicatoinsFieldJsonObject);

            } else {
                medicationsValues = new ReferralObsValues(List.of("None"), List.of("None"));
            }

            // Create referral event
            submitReferralEvent(memberID,
                    AddoUtils.createReferralForm(dangerSignsFormJsonObject, StringUtils.isNotEmpty(medicationsFormJsonString) ? new JSONObject(medicationsFormJsonString) : null),
                    formTag, problems, medicationsValues);
        }
    }

    public String getButtonAction(String dangerSignJsonObject) {
        String buttonAction = "";
        try {
            JSONObject jsonObject = JsonFormUtils.getFieldJSONObject(
                    JsonFormUtils.fields(new JSONObject(dangerSignJsonObject)),
                    "save_n_refer"
            );
            if (jsonObject.optString("value", "").compareToIgnoreCase("true") == 0) {
                buttonAction = jsonObject.getJSONObject("action").getString("behaviour");
            }
        } catch (JSONException e) {
            Timber.e(e);
        }
        return buttonAction;
    }

    public void submitReferralEvent(String baseEntityId, JSONArray jsonArray, FormTag formTag, ReferralObsValues problems, ReferralObsValues servicesBeforeRef) {
        try {
            final ECSyncHelper syncHelper = AddoApplication.getInstance().getEcSyncHelper();
            JSONObject metadata = new JSONObject();
            Event event = org.smartregister.util.JsonFormUtils.createEvent(jsonArray, metadata, formTag, baseEntityId, "Referral Registration", "ec_referral");
            event.setEventId(UUID.randomUUID().toString());
            event.addObs(createObsFromValues(problems.getValues(), problems.getHumanReadableValues(), "problem"));
            event.addObs(createObsFromValues(servicesBeforeRef.getValues(), servicesBeforeRef.getHumanReadableValues(), "service_before_referral"));
            JSONObject eventJson = new JSONObject(gson.toJson(event));
            Timber.e("%S", eventJson);
            syncHelper.addEvent(baseEntityId, eventJson);
            toastCallback.showToastInInteractor(R.string.referral_submitted);
        } catch (JSONException e) {
            Timber.e(e);
        }
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

    private Obs createObsFromValues(List<String> values, List<String> humanReadableValues, String formSubmissionField) {

        return new Obs(
                "concept",
                "text",
                formSubmissionField,
                "",
                convertToObjectList(values),
                convertToObjectList(humanReadableValues), null, formSubmissionField);

    }
}
