package org.smartregister.addo.actionhelper;

import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.anc.actionhelper.HomeVisitActionHelper;
import org.smartregister.chw.anc.model.BaseAncHomeVisitAction;

import timber.log.Timber;

public class PrescriptionActionHelper extends HomeVisitActionHelper {


    @Override
    public void onPayloadReceived(String jsonPayload) {
        try {
            JSONObject jsonObject = new JSONObject(jsonPayload);
        }catch (JSONException je){
            Timber.e(je);
        }
    }

    @Override
    public String evaluateSubTitle() {
        return "";
    }

    @Override
    public BaseAncHomeVisitAction.Status evaluateStatusOnPayload() {
        return null;
    }
}
