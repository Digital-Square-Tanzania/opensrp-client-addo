package org.smartregister.addo.interactor;

import org.smartregister.addo.dao.AncDao;
import org.smartregister.addo.dao.VisitDao;
import org.smartregister.chw.anc.contract.BaseAncHomeVisitContract;
import org.smartregister.chw.anc.domain.MemberObject;
import org.smartregister.chw.anc.interactor.BaseAncHomeVisitInteractor;
import org.smartregister.chw.anc.model.BaseAncHomeVisitAction;
import org.smartregister.chw.anc.util.VisitUtils;
import org.smartregister.clientandeventmodel.Event;

import java.util.LinkedHashMap;
import java.util.Map;

import timber.log.Timber;

public class AddoVisitInteractor extends BaseAncHomeVisitInteractor {

    private Flavor flavor = new AddoVisitInteractorFlv();

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
                for (Map.Entry<String, BaseAncHomeVisitAction> entry : flavor.calculateActions(view, memberObject, callBack).entrySet()) {
                    actionList.put(entry.getKey(), entry.getValue());
                }
            } catch (BaseAncHomeVisitAction.ValidationException e) {
                Timber.e(e);
            }

            appExecutors.mainThread().execute(() -> callBack.preloadActions(actionList));
        };

        appExecutors.diskIO().execute(runnable);
    }

    public interface Flavor {

        LinkedHashMap<String, BaseAncHomeVisitAction> calculateActions(final BaseAncHomeVisitContract.View view, MemberObject memberObject, final BaseAncHomeVisitContract.InteractorCallBack callBack) throws BaseAncHomeVisitAction.ValidationException;

        void addExtraObs(Event baseEvent);

    }

}
