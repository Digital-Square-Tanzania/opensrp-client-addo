package org.smartregister.addo.interactor;

import android.content.Context;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.addo.R;
import org.smartregister.addo.util.Constants;
import org.smartregister.chw.anc.actionhelper.DangerSignsHelper;
import org.smartregister.chw.anc.actionhelper.HomeVisitActionHelper;
import org.smartregister.chw.anc.contract.BaseAncHomeVisitContract;
import org.smartregister.chw.anc.domain.MemberObject;
import org.smartregister.chw.anc.domain.VisitDetail;
import org.smartregister.chw.anc.model.BaseAncHomeVisitAction;
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

    @Override
    public LinkedHashMap<String, BaseAncHomeVisitAction> calculateActions(BaseAncHomeVisitContract.View view, MemberObject memberObject, BaseAncHomeVisitContract.InteractorCallBack callBack) throws BaseAncHomeVisitAction.ValidationException {
        actionList = new LinkedHashMap<>();
        context = view.getContext();

        try {
            //add action here
            evaluatePrescriptionsNote(context);
            evaluateDangerSigns(context);
            evaluateMedicationDispensed(context);
            evaluateCommodities(context);

        }catch (Exception e){
            Timber.e(e);
        }

        return actionList;
    }

    private void evaluatePrescriptionsNote(final Context context) throws Exception {

        BaseAncHomeVisitAction prescription_note = new BaseAncHomeVisitAction.Builder(context, context.getString(R.string.evalueate_prescription_note))
                .withOptional(false)
                .withFormName(Constants.JSON_FORM.ANC_HOME_VISIT.getAddoPrescriptionNote())
                .withHelper(new PrescriptionNoteHelper(actionList))
                .build();
        actionList.put(context.getString(R.string.evalueate_prescription_note), prescription_note);
    }

    private void evaluateDangerSigns(final Context context) throws Exception {
        BaseAncHomeVisitAction danger_signs = new BaseAncHomeVisitAction.Builder(context, context.getString(R.string.anc_home_visit_danger_signs))
                .withOptional(false)
                .withFormName(Constants.JSON_FORM.ANC_HOME_VISIT.getAncAddoDangerSign())
                .withHelper(new DangerSignsHelper())
                .build();
        actionList.put(context.getString(R.string.anc_home_visit_danger_signs), danger_signs);
    }

    private void evaluateMedicationDispensed(final Context context) throws Exception {
        BaseAncHomeVisitAction medication_dispensed = new BaseAncHomeVisitAction.Builder(context, context.getString(R.string.evalueate_medication_dispensed))
                .withOptional(false)
                .withFormName(Constants.JSON_FORM.ANC_HOME_VISIT.getAddoCommodities())
                .withOptional(true)
                .build();
        actionList.put(context.getString(R.string.evalueate_medication_dispensed), medication_dispensed);
    }

    private void evaluateCommodities(final Context context) throws Exception {
        BaseAncHomeVisitAction commodities = new BaseAncHomeVisitAction.Builder(context, context.getString(R.string.evalueate_commodities))
                .withOptional(false)
                .withFormName(Constants.JSON_FORM.ANC_HOME_VISIT.getAddoCommodities())
                .withHelper(new CommoditiesActionHelper())
                .withOptional(true)
                .build();
        actionList.put(context.getString(R.string.evalueate_commodities), commodities);
    }

    private void evaluatePrescriptions(final Context context) throws Exception {

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

    //Action Helper Classes

    class PrescriptionNoteHelper extends HomeVisitActionHelper {

        boolean hasPrescription;
        String prescriptionNoteAvailable = "";
        String jsonPayload;

        LinkedHashMap<String, BaseAncHomeVisitAction> mActionList;
        Context mContext;

        PrescriptionNoteHelper(LinkedHashMap<String, BaseAncHomeVisitAction> actionList){
            mActionList = actionList;
        }

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
        public void onPayloadReceived(BaseAncHomeVisitAction baseAncHomeVisitAction) {
            Timber.d("onPayloadReceived");
        }

        @Override
        public void onPayloadReceived(String jsonPayload) {
            try {
                JSONObject jsonObject = new JSONObject(jsonPayload);
                prescriptionNoteAvailable = JsonFormUtils.getCheckBoxValue(jsonObject, "client_prescription_note_available");
                hasPrescription = prescriptionNoteAvailable.contains("client_prescription_yes");
            }catch (JSONException e){
                Timber.e(e);
            }
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
                    evaluatePrescriptions(mContext);
                }else{
                    evaluateDangerSigns(mContext);
                    evaluateMedicationDispensed(mContext);
                    evaluateCommodities(mContext);
                }
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

}
