package org.smartregister.addo.actionhelper;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.addo.util.JsonFormUtils;
import org.smartregister.chw.anc.actionhelper.HomeVisitActionHelper;
import org.smartregister.chw.anc.model.BaseAncHomeVisitAction;


import java.util.jar.JarException;

public class AddoDangerSignsHelper extends HomeVisitActionHelper {

    private String signs_present;
    @Override
    public void onPayloadReceived(String s) {
        try {
            JSONObject jsonObject = new JSONObject(s);
            this.signs_present = JsonFormUtils.getCheckBoxValue(jsonObject, "child_addo_danger_signs");
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
}
