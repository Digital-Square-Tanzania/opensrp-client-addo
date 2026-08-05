package org.smartregister.addo.activity;

import android.text.TextUtils;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.utils.NativeFormLangUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.addo.fragment.NcdJsonWizardFormFragment;
import org.smartregister.addo.rules.AddoRulesEngineFactory;
import org.smartregister.family.activity.FamilyWizardFormActivity;

public class NcdFormWizardActivity extends FamilyWizardFormActivity {

    @Override
    public void initializeFormFragment() {
        NcdJsonWizardFormFragment jsonWizardFormFragment = NcdJsonWizardFormFragment.getFormFragment("step1");
        getSupportFragmentManager().beginTransaction()
                .add(com.vijay.jsonwizard.R.id.container, jsonWizardFormFragment).commit();
    }

    @Override
    protected void widgetsWriteValue(String stepName, String key, String value, String openMrsEntityParent, String openMrsEntity, String openMrsEntityId, boolean popup) throws JSONException {

        synchronized (getmJSONObject()) {
            if (translateForm) {
                value = NativeFormLangUtils.getTranslatedValue(value, getmJSONObject().optString(JsonFormConstants.MLS.PROPERTIES_FILE_NAME), this);
            }
            JSONObject jsonObject = getmJSONObject().getJSONObject(stepName);
            JSONArray fields = fetchFields(jsonObject, popup);
            for (int i = 0; i < fields.length(); i++) {
                JSONObject item = fields.getJSONObject(i);
                String keyAtIndex = item.getString(JsonFormConstants.KEY);
                String itemType = item.has(JsonFormConstants.TYPE) ? item.getString(JsonFormConstants.TYPE) : "";
                boolean isSpecialWidget = isSpecialWidget(itemType);
                String cleanKey = isSpecialWidget ? cleanWidgetKey(key, itemType) : key;

                if (cleanKey.equals(keyAtIndex)) {
                    if (item.has(JsonFormConstants.TEXT)) {
                        item.put(JsonFormConstants.TEXT, value);
                    } else {
                        widgetWriteItemValue(value, item, itemType);
                    }
                    addOpenMrsAttributes(openMrsEntityParent, openMrsEntity, openMrsEntityId, item);
                    invokeRefreshLogic(value, popup, cleanKey, null);
                    return;
                }
            }
        }
    }

    private void widgetWriteItemValue(String value, JSONObject item, String itemType) throws JSONException {
        if (!TextUtils.isEmpty(value)) {
            value = value.trim();
        }
        item.put(JsonFormConstants.VALUE, itemType.equals(JsonFormConstants.HIDDEN) && TextUtils.isEmpty(value) ?
                item.has(JsonFormConstants.VALUE) && !TextUtils.isEmpty(item.getString(JsonFormConstants.VALUE)) ?
                        item.getString(JsonFormConstants.VALUE) : value : value);
    }

    private void addOpenMrsAttributes(String openMrsEntityParent, String openMrsEntity, String openMrsEntityId,
                                      JSONObject item) throws JSONException {
        item.put(JsonFormConstants.OPENMRS_ENTITY_PARENT, openMrsEntityParent);
        item.put(JsonFormConstants.OPENMRS_ENTITY, openMrsEntity);
        item.put(JsonFormConstants.OPENMRS_ENTITY_ID, openMrsEntityId);
    }

    @Override
    public void init(String json) {
        super.init(json);
        rulesEngineFactory = new AddoRulesEngineFactory(this, globalValues);
        setRulesEngineFactory(rulesEngineFactory);

        confirmCloseTitle = getString(com.vijay.jsonwizard.R.string.confirm_form_close);
        confirmCloseMessage = getString(com.vijay.jsonwizard.R.string.confirm_form_close_explanation);
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
    }
}