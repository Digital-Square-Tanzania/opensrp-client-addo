package org.smartregister.addo.interactor;

import static org.smartregister.addo.util.AddoUtils.displayReferralFacilities;

import android.content.Context;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.addo.R;
import org.smartregister.addo.util.AddoUtils;
import org.smartregister.addo.util.Constants;
import org.smartregister.addo.util.Constants.FamilyMemberType;
import org.smartregister.addo.util.CoreConstants;
import org.smartregister.chw.anc.actionhelper.HomeVisitActionHelper;
import org.smartregister.chw.anc.contract.BaseAncHomeVisitContract;
import org.smartregister.chw.anc.domain.MemberObject;
import org.smartregister.chw.anc.domain.VisitDetail;
import org.smartregister.chw.anc.model.BaseAncHomeVisitAction;
import org.smartregister.chw.anc.util.AppExecutors;
import org.smartregister.chw.anc.util.JsonFormUtils;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.family.util.Utils;
import org.smartregister.util.FormUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

public class AddoVisitInteractorFlv implements AddoVisitInteractor.Flavor {

    protected LinkedHashMap<String, BaseAncHomeVisitAction> actionList;
    Context context;
    private BaseAncHomeVisitContract.InteractorCallBack callback;

    private static FamilyMemberType clientType;
    @Override
    public LinkedHashMap<String, BaseAncHomeVisitAction> calculateActions(BaseAncHomeVisitContract.View view, MemberObject memberObject, BaseAncHomeVisitContract.InteractorCallBack callBack, FamilyMemberType clientType) throws BaseAncHomeVisitAction.ValidationException {
        actionList = new LinkedHashMap<>();
        context = view.getContext();
        callback = callBack;
        this.clientType = clientType;

        try {
            //add action here
            evaluatePrescriptionsNote(context, actionList, clientType);
        }catch (Exception e){
            Timber.e(e);
        }

        return actionList;
    }

    private void evaluatePrescriptionsNote(final Context context,
                                           LinkedHashMap<String, BaseAncHomeVisitAction> actionList, FamilyMemberType clientType) throws Exception {



        BaseAncHomeVisitAction prescription_note = new BaseAncHomeVisitAction.Builder(context, context.getString(R.string.evalueate_prescription_note))
                .withOptional(false)
                .withFormName(Constants.JSON_FORM.ANC_HOME_VISIT.getAddoPrescriptionNote())
                .withHelper(new PrescriptionNoteHelper())
                .build();
        actionList.put(context.getString(R.string.evalueate_prescription_note), prescription_note);
    }

    private String getDangerSignsFormName(Constants.FamilyMemberType clientType) {
        switch (clientType) {
            case CHILD:
                return CoreConstants.JSON_FORM.getChildAddoDangerSigns();
            case ANC:
                return CoreConstants.JSON_FORM.getAncAddoDangerSigns();
            case PNC:
                return CoreConstants.JSON_FORM.getPncAddoDangerSigns();
            case ADOLESCENT:
                return CoreConstants.JSON_FORM.getAdolescentAddoScreening();
            default:
                return "";
        }
    }

    private void evaluateDangerSigns(final Context context,
                                     LinkedHashMap<String, BaseAncHomeVisitAction> actionList) throws Exception {
        BaseAncHomeVisitAction danger_signs = new BaseAncHomeVisitAction.Builder(context, context.getString(R.string.anc_home_visit_danger_signs))
                .withOptional(false)
                .withFormName(getDangerSignsFormName(clientType))
                .withHelper(new AddoDangerSignsHelper(context, actionList))
                .build();
        actionList.put(context.getString(R.string.anc_home_visit_danger_signs), danger_signs);
    }

    public static void evaluateMedicationDispensed(final Context context,
                                             LinkedHashMap<String, BaseAncHomeVisitAction> actionList, String dangerSignsForm) throws Exception {
        MedicationDispensedActionHelper medicationDispensedActionHelper = new MedicationDispensedActionHelper(dangerSignsForm, clientType);
        BaseAncHomeVisitAction medication_dispensed = new BaseAncHomeVisitAction.Builder(context, context.getString(R.string.evalueate_medication_dispensed))
                .withOptional(false)
                .withHelper(medicationDispensedActionHelper)
                .withFormName(Constants.JSON_FORM.ANC_HOME_VISIT.getAddoCommodities())
                .withOptional(true)
                .build();
        actionList.put(context.getString(R.string.evalueate_medication_dispensed), medication_dispensed);
    }

    private void evaluateCommodities(final Context context,
                                     LinkedHashMap<String, BaseAncHomeVisitAction> actionList) throws Exception {
        BaseAncHomeVisitAction commodities = new BaseAncHomeVisitAction.Builder(context, context.getString(R.string.evalueate_commodities))
                .withOptional(false)
                .withFormName(Constants.JSON_FORM.ANC_HOME_VISIT.getAddoCommodities())
                .withHelper(new CommoditiesActionHelper())
                .withOptional(true)
                .build();
        actionList.put(context.getString(R.string.evalueate_commodities), commodities);
    }

    private void evaluatePrescriptions(final Context context,
                                       LinkedHashMap<String, BaseAncHomeVisitAction> actionList) throws Exception {

        BaseAncHomeVisitAction prescription_from_hf = new BaseAncHomeVisitAction.Builder(context, context.getString(R.string.evalueate_prescription))
                .withOptional(false)
                .withFormName(Constants.JSON_FORM.ANC_HOME_VISIT.getAddoPrescriptionsFromLab())
                .withOptional(true)
                .build();
        actionList.put(context.getString(R.string.evalueate_prescription), prescription_from_hf);
    }

    @Override
    public void addExtraObs(Event baseEvent) {
        //if needed
    }

    private FormUtils getFormUtils() throws Exception {
        FormUtils formUtils = FormUtils.getInstance(Utils.context().applicationContext());;
        return formUtils;
    }

    private void refreshActionList() {
        new AppExecutors().mainThread().execute(() -> callback.preloadActions(actionList));
    }

    //Action Helper Classes

    class PrescriptionNoteHelper extends HomeVisitActionHelper {

        boolean hasPrescription;
        String prescriptionNoteAvailable = "";
        String jsonPayload;

        //LinkedHashMap<String, BaseAncHomeVisitAction> mActionList;
        Context mContext;

        @Override
        public void onJsonFormLoaded(String jsonString, Context context, Map<String, List<VisitDetail>> details) {
            mContext = context;
            jsonPayload = jsonString;
        }

        @Override
        public String getPreProcessed() {
            return "";
        }

        @Override
        public void onPayloadReceived(String jsonPayload) {
            try {
                JSONObject jsonObject = new JSONObject(jsonPayload);
                prescriptionNoteAvailable = JsonFormUtils.getValue(jsonObject, "client_prescription_note_available");
                hasPrescription = prescriptionNoteAvailable.contains("client_prescription_yes");
            }catch (JSONException e){
                Timber.e(e);
            }
        }

        @Override
        public void onPayloadReceived(BaseAncHomeVisitAction baseAncHomeVisitAction) {
            Timber.d("onPayloadReceived");
        }

        @Override
        public BaseAncHomeVisitAction.ScheduleStatus getPreProcessedStatus() {
            return null;
        }

        @Override
        public String getPreProcessedSubTitle() {
            return "";
        }

        @Override
        public String postProcess(String jsonPayload) {
            try {
                if (hasPrescription){
                    actionList.clear();
                    evaluatePrescriptions(mContext, actionList);
                }else{
                    actionList.remove(mContext.getString(R.string.evalueate_prescription_note));
                    evaluateDangerSigns(mContext, actionList);
                    //evaluateMedicationDispensed(mContext, actionList);
                    evaluateCommodities(mContext, actionList);
                }
                refreshActionList();
            }catch (Exception e){
                Timber.e(e);
            }
            return jsonPayload;
        }

        @Override
        public String evaluateSubTitle() {
            return "";
        }

        @Override
        public BaseAncHomeVisitAction.Status evaluateStatusOnPayload() {
            if (!prescriptionNoteAvailable.isEmpty()){
                return BaseAncHomeVisitAction.Status.COMPLETED;
            }else {
                return BaseAncHomeVisitAction.Status.PENDING;
            }

        }
    }

    class CommoditiesActionHelper extends HomeVisitActionHelper {

        String commoditiesDispensed;

        @Override
        public void onPayloadReceived(String jsonPayload) {
            try {
                JSONObject jsonObject = new JSONObject(jsonPayload);
                commoditiesDispensed = JsonFormUtils.getCheckBoxValue(jsonObject, "client_commodities_dispensed");
            }catch (Exception e){
                Timber.e(e);
            }
        }

        @Override
        public String evaluateSubTitle() {
            return "";
        }

        @Override
        public BaseAncHomeVisitAction.Status evaluateStatusOnPayload() {
            if (StringUtils.isNotEmpty(commoditiesDispensed)){
                return BaseAncHomeVisitAction.Status.COMPLETED;
            }else {
                return BaseAncHomeVisitAction.Status.PENDING;
            }
        }
    }

    class AddoDangerSignsHelper extends HomeVisitActionHelper {

        private String signs_present;
        private Context context;
        private LinkedHashMap<String, BaseAncHomeVisitAction> actionList;
        private String jsonPayload;

        public AddoDangerSignsHelper(Context context, LinkedHashMap<String, BaseAncHomeVisitAction> actionList) {
            this.context = context;
            this.actionList = actionList;
        }

        @Override
        public String getPreProcessed() {
            try{
                return displayReferralFacilities(new JSONObject(this.jsonPayload));
            }catch (JSONException e){
                Timber.e(e);
            }
            return null;
        }

        @Override
        public void onJsonFormLoaded(String jsonString, Context context, Map<String, List<VisitDetail>> details) {
            this.jsonPayload = jsonString;
        }

        @Override
        public void onPayloadReceived(String s) {
            try {
                JSONObject jsonObject = new JSONObject(s);
                this.signs_present = org.smartregister.addo.util.JsonFormUtils.getCheckBoxValue(jsonObject, "child_addo_danger_signs");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public String evaluateSubTitle() {
            return null;
        }

        @Override
        public BaseAncHomeVisitAction.Status evaluateStatusOnPayload() {
            if (StringUtils.isNotEmpty( signs_present)) {
                return BaseAncHomeVisitAction.Status.COMPLETED;
            } else {
                return BaseAncHomeVisitAction.Status.PENDING;
            }
        }

        @Override
        public String postProcess(String jsonPayload) {
            try {
                evaluateMedicationDispensed(context, actionList, jsonPayload);
                refreshActionList();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return super.postProcess(jsonPayload);
        }
    }

    static class MedicationDispensedActionHelper extends HomeVisitActionHelper {

        String dangerSignsForm;
        private String jsonPayload;

        FamilyMemberType clientType;
        public MedicationDispensedActionHelper(String dangerSignsForm, FamilyMemberType clientType) {
            this.dangerSignsForm = dangerSignsForm;
            this.clientType = clientType;
        }

        @Override
        public void onJsonFormLoaded(String jsonString, Context context, Map<String, List<VisitDetail>> details) {
            this.jsonPayload = jsonString;
        }

        @Override
        public void onPayloadReceived(String jsonPayload) {
            try {
                JSONObject jsonObject = new JSONObject(jsonPayload);
            }catch (JSONException je){
                Timber.e(je);
            }
        }

        @Override
        public String getPreProcessed() {
            try {
                JSONObject dangerSignsFormObject = new JSONObject(dangerSignsForm);
                return AddoUtils.checkDSPresentProposedMedsAndDispense(dangerSignsFormObject, clientType);
            } catch (JSONException e) {
                Timber.e(e);
            }

            return "";
        }

        @Override
        public String evaluateSubTitle() {
            return "";
        }

        @Override
        public BaseAncHomeVisitAction.Status evaluateStatusOnPayload() {
            return BaseAncHomeVisitAction.Status.COMPLETED;
        }
    }

}
