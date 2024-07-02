package org.smartregister.addo.interactor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.addo.R;
import org.smartregister.addo.application.AddoApplication;
import org.smartregister.addo.dao.FamilyDao;
import org.smartregister.addo.dao.VisitDao;
import org.smartregister.addo.util.AddoUtils;
import org.smartregister.addo.util.Constants;
import org.smartregister.addo.util.Constants.FamilyMemberType;
import org.smartregister.addo.util.CoreConstants;
import org.smartregister.addo.util.JsonFormUtils;
import org.smartregister.addo.util.ReferralUtils;
import org.smartregister.chw.anc.AncLibrary;
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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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

    public static Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .registerTypeAdapter(DateTime.class, new DateTimeTypeConverter()).create();

    public AddoVisitInteractor(FamilyMemberType clientType, String villageTown) {
        this.clientType = clientType;
        this.villageTown = villageTown;

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
            VisitUtils.processVisits(memberObject.getBaseEntityId());
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

        String dangerSignJsonString = "";
        String medicationJsonString = "";

        for (Map.Entry<String, BaseAncHomeVisitAction> entry : map.entrySet()) {
            if (entry.getKey().equals(AddoApplication.getInstance().getContext().getStringResource(R.string.anc_home_visit_danger_signs))) {
                dangerSignJsonString = entry.getValue().getJsonPayload();
            } else if (entry.getKey().equals(AddoApplication.getInstance().getContext().getStringResource(R.string.evalueate_medication_dispensed))) {
                medicationJsonString = entry.getValue().getJsonPayload();
            }
        }

        ReferralUtils.closeLinkageAndOpenFollowUp(memberID, villageTown);

        if (!getButtonAction(dangerSignJsonString).isEmpty()){
            JSONObject dangerSignJsonObject = new JSONObject(dangerSignJsonString);

            String facilityValue = JsonFormUtils.getValue(dangerSignJsonObject, "chw_referral_hf");
            String facility =  facilityValue.substring(2, facilityValue.length() - 2);

            if (ReferralUtils.hasReferralTask(CoreConstants.REFERRAL_PLAN_ID_2, facility, memberID, CoreConstants.JsonAssets.REFERRAL_CODE)) {
                FamilyDao.archiveHFTasksForEntity(memberID);
            }

            ReferralUtils.createReferralTask(memberID,
                    dangerSignJsonObject.optString(org.smartregister.chw.anc.util.Constants.ENCOUNTER_TYPE),
                    dangerSignJsonString,
                    villageTown,
                    facility,
                    formTag.formSubmissionId);

            // Create referral event
            submitReferralEvent(memberID,
                    AddoUtils.createReferralForm(dangerSignJsonObject, new JSONObject(medicationJsonString)),
                    formTag);
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

    public void submitReferralEvent(String baseEntityId, JSONArray jsonArray, FormTag formTag) {
        try{
            final ECSyncHelper syncHelper = AddoApplication.getInstance().getEcSyncHelper();
            JSONObject metadata= new JSONObject();
            Event event = org.smartregister.util.JsonFormUtils.createEvent(jsonArray, metadata, formTag, baseEntityId,"Referral Registration","ec_referral");
            event.setEventId(UUID.randomUUID().toString());
            JSONObject eventJson = new JSONObject(gson.toJson(event));
            Timber.e("%S", eventJson);
            syncHelper.addEvent(baseEntityId, eventJson);
        }catch (JSONException e){
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
}
