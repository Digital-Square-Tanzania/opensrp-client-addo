package org.smartregister.addo.presenter;

import android.content.Context;
import android.util.Pair;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.json.JSONObject;
import org.smartregister.addo.R;
import org.smartregister.addo.contract.ChildRegisterContract;
import org.smartregister.addo.contract.FamilyProfileExtendedContract;
import org.smartregister.addo.domain.FamilyMember;
import org.smartregister.addo.interactor.ChildRegisterInteractor;
import org.smartregister.addo.interactor.FamilyProfileInteractor;
import org.smartregister.addo.model.ChildProfileModel;
import org.smartregister.addo.model.ChildRegisterModel;
import org.smartregister.addo.util.Constants;
import org.smartregister.addo.util.JsonFormUtils;
import org.smartregister.addo.util.Utils;
import org.smartregister.clientandeventmodel.Client;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.commonregistry.CommonPersonObjectClient;
import org.smartregister.family.contract.FamilyProfileContract;
import org.smartregister.family.domain.FamilyEventClient;
import org.smartregister.family.presenter.BaseFamilyProfilePresenter;
import org.smartregister.family.util.DBConstants;
import org.smartregister.location.helper.LocationHelper;
import org.smartregister.view.LocationPickerView;

import java.lang.ref.WeakReference;

import timber.log.Timber;

public class FamilyProfilePresenter extends BaseFamilyProfilePresenter implements
        FamilyProfileExtendedContract.Presenter,
        ChildRegisterContract.InteractorCallBack ,
        FamilyProfileExtendedContract.PresenterCallBack {

    private static final String TAG = FamilyProfilePresenter.class.getCanonicalName();
    private WeakReference<FamilyProfileExtendedContract.View> viewReference;
    private ChildRegisterInteractor childRegisterInteractor;
    private ChildProfileModel childProfileModel;

    public FamilyProfilePresenter(FamilyProfileExtendedContract.View view, FamilyProfileContract.Model model, String familyBaseEntityId, String familyHead, String primaryCaregiver, String familyName) {
        super(view, model, familyBaseEntityId, familyHead, primaryCaregiver, familyName);
        viewReference = new WeakReference<>(view);
        childRegisterInteractor = new ChildRegisterInteractor();
        childProfileModel = new ChildProfileModel(familyName);
        interactor = new FamilyProfileInteractor();
        verifyHasPhone();
    }

    @Override
    public void startFormForEdit(CommonPersonObjectClient client) {
        JSONObject form = JsonFormUtils.getAutoPopulatedJsonEditFormString(Constants.JSON_FORM.getFamilyDetailsRegister(), getView().getApplicationContext(), client, Utils.metadata().familyRegister.updateEventType);
        try {
            getView().startFormActivity(form);
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    // Child form

    @Override
    public void startChildForm(String formName, String entityId, String metadata, String currentLocationId) throws Exception {
        if (StringUtils.isBlank(entityId)) {
            Triple<String, String, String> triple = Triple.of(formName, metadata, currentLocationId);
            childRegisterInteractor.getNextUniqueId(triple, this, familyBaseEntityId);
            return;
        }

        JSONObject form = childProfileModel.getFormAsJson(formName, entityId, currentLocationId, familyBaseEntityId);
        getView().startFormActivity(form);

    }

    @Override
    public void onUniqueIdFetched(Triple<String, String, String> triple, String entityId, String familyId) {
        try {
            startChildForm(triple.getLeft(), entityId, triple.getMiddle(), triple.getRight());
        } catch (Exception e) {
            Timber.e(e);
            getView().displayToast(R.string.error_unable_to_start_form);
        }
    }

    @Override
    public void onRegistrationSaved(boolean isEdit) {

    }

    @Override
    public void saveChildRegistration(Pair<Client, Event> pair, String jsonString, boolean isEditMode, ChildRegisterContract.InteractorCallBack callBack) {
        childRegisterInteractor.saveRegistration(pair, jsonString, isEditMode, this);
    }

    @Override
    public void saveChildForm(String jsonString, boolean isEditMode) {
        ChildRegisterModel model = new ChildRegisterModel();
        try {

            getView().showProgressDialog(R.string.saving_dialog_title);

            Pair<Client, Event> pair = model.processRegistration(jsonString);
            if (pair == null) {
                return;
            }
            saveChildRegistration(pair, jsonString, isEditMode, this);

        } catch (Exception e) {
            Timber.e(e);
        }
    }

    @Override
    public void notifyHasPhone(boolean hasPhone) {
    }

    @Override
    public void verifyHasPhone() {
        ((FamilyProfileInteractor) interactor).verifyHasPhone(familyBaseEntityId, this);
    }

    public FamilyProfileExtendedContract.View getView() {
        if (viewReference != null) {
            return viewReference.get();
        } else {
            return null;
        }
    }

    @Override
    public String saveChwFamilyMember(String jsonString) {

        try {
            getView().showProgressDialog(org.smartregister.family.R.string.saving_dialog_title);

            FamilyEventClient familyEventClient = model.processMemberRegistration(jsonString, familyBaseEntityId);
            if (familyEventClient == null) {
                return null;
            }

            interactor.saveRegistration(familyEventClient, jsonString, false, this);
            return familyEventClient.getClient().getBaseEntityId();
        } catch (Exception e) {
            Timber.e(e);
        }
        return null;
    }

    @Override
    public boolean updatePrimaryCareGiver(Context context, String jsonString, String familyBaseEntityId, String entityID) {

        boolean res = false;
        try {
            FamilyMember member = JsonFormUtils.getFamilyMemberFromRegistrationForm(jsonString, familyBaseEntityId, entityID);
            if (member != null && member.getPrimaryCareGiver()) {
                LocationPickerView lpv = new LocationPickerView(context);
                lpv.init();
                String lastLocationId = LocationHelper.getInstance().getOpenMrsLocationId(lpv.getSelectedItem());

                //TODO : Uncomment to fix
                //new FamilyChangeContractInteractor().updateFamilyRelations(context, member, lastLocationId);
                res = true;
            }
        } catch (Exception e) {
            Timber.e(e);
        }
        return res;
    }

    @Override
    public void refreshProfileTopSection(CommonPersonObjectClient client) {

        if (client == null || client.getColumnmaps() == null) {
            return;
        }

        String firstName = org.smartregister.family.util.Utils.getValue(client.getColumnmaps(), DBConstants.KEY.FIRST_NAME, true);
        String famName;

        if (org.smartregister.family.util.Utils.getBooleanProperty(org.smartregister.family.util.Constants.Properties.FAMILY_HEAD_FIRSTNAME_ENABLED)) {

            String familyHeadFirstName = org.smartregister.family.util.Utils.getValue(client.getColumnmaps(), org.smartregister.family.util.Constants.KEY.FAMILY_HEAD_NAME, true);
            famName = String.format(getView().getString(R.string.family_profile_title_with_firstname), familyHeadFirstName, firstName);

        } else {

            famName = String.format(getView().getString(R.string.family_profile_title), firstName);
        }

        getView().setProfileName(famName);

        String villageTown = org.smartregister.family.util.Utils.getValue(client.getColumnmaps(), DBConstants.KEY.VILLAGE_TOWN, false);
        getView().setProfileDetailOne(villageTown);

        getView().setProfileImage(client.getCaseId());

    }
}

