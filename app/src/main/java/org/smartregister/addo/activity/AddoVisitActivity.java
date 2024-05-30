package org.smartregister.addo.activity;

import static org.smartregister.chw.anc.util.Constants.ANC_MEMBER_OBJECTS.BASE_ENTITY_ID;
import static org.smartregister.chw.anc.util.Constants.ANC_MEMBER_OBJECTS.EDIT_MODE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.domain.Form;

import org.json.JSONObject;
import org.smartregister.addo.R;
import org.smartregister.addo.interactor.AddoVisitInteractor;
import org.smartregister.chw.anc.activity.BaseAncHomeVisitActivity;
import org.smartregister.chw.anc.domain.MemberObject;
import org.smartregister.chw.anc.presenter.BaseAncHomeVisitPresenter;
import org.smartregister.family.util.Constants;
import org.smartregister.family.util.JsonFormUtils;
import org.smartregister.family.util.Utils;
import org.smartregister.util.LangUtils;

public class AddoVisitActivity extends BaseAncHomeVisitActivity {

    public static void startMe(Activity activity, MemberObject memberObject, boolean isEditMode){
        Intent intent = new Intent(activity, AddoVisitActivity.class);
        intent.putExtra("MemberObject", memberObject);
        intent.putExtra(BASE_ENTITY_ID, memberObject.getBaseEntityId());
        intent.putExtra(EDIT_MODE, isEditMode);
        activity.startActivityForResult(intent, org.smartregister.chw.anc.util.Constants.REQUEST_CODE_HOME_VISIT);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        baseEntityID = this.getIntent().getStringExtra(BASE_ENTITY_ID);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void registerPresenter() {
        presenter = new BaseAncHomeVisitPresenter(
                memberObject,
                this,
                new AddoVisitInteractor()//Interactor instance here
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
        form.setActionBarBackground(R.color.family_actionbar);
        form.setWizard(false);

        Intent intent = new Intent(this, Utils.metadata().familyMemberFormActivity);
        intent.putExtra(org.smartregister.family.util.Constants.JSON_FORM_EXTRA.JSON, jsonForm.toString());
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
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == org.smartregister.chw.anc.util.Constants.REQUEST_CODE_GET_JSON){
            if (resultCode == RESULT_OK){
                /*

                1. Save the forms filled in actions here
                2. Return to base

                 */
            }
        }

    }
}
