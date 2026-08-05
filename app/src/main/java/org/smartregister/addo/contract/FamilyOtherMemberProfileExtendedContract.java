package org.smartregister.addo.contract;


import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.domain.tag.FormTag;
import org.smartregister.family.contract.FamilyOtherMemberContract;

import java.util.Map;

public interface FamilyOtherMemberProfileExtendedContract {

    interface Presenter extends FamilyOtherMemberContract.Presenter {

        void submitVisit(Map<String, String> formForSubmission);
        void submitReferralEvent(String baseEntityId, JSONArray jsonArray, FormTag formTag, String referralFacilityName);
        void submitDiabetesAndHypertensionScreeningEvent(String baseEntityId, JSONArray jsonArray,
                                                         FormTag formTag, String chwLocationId, String encounterType,
                                                         JSONObject formMetadata);
    }

    interface View extends FamilyOtherMemberContract.View {

        void showProgressDialog(int saveMessageStringIdentifier);

        void hideProgressDialog();

        Context getContext();
    }

    interface Interactor {

        void onDestroy(boolean isChangingConfiguration);

        void submitVisit(boolean editMode, String memberID, Map<String, String> formForSubmission, FamilyOtherMemberProfileExtendedContract.InteractorCallBack callBack);

    }

    interface InteractorCallBack {

        void onSubmitted(boolean successful);
    }
}
