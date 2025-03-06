package org.smartregister.addo.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.smartregister.addo.repository.AddoWeeklySummaryRepository;

/**
 * Created by Kassim Sheghembe on 2021-08-18
 */
public class AddoHomeViewModel extends ViewModel {

    private final MutableLiveData<String> selectedVillage = new MutableLiveData<String>();

    private final MutableLiveData<String> selectedVillageId = new MutableLiveData<String>();

    private MutableLiveData<String> numRefferalsWeek;
    private MutableLiveData<String> numClosedRefferalsWeek;
    private MutableLiveData<String> numLinkageClosedThisAddo;

    private final AddoWeeklySummaryRepository weeklySummaryRepository = new AddoWeeklySummaryRepository();

    public AddoHomeViewModel() {}

    public void setSelectedVillage(String village) {
        selectedVillage.setValue(village);
    }

    public void setSelectedVillageId(String villageId) {
        selectedVillageId.setValue(villageId);
    }

    public LiveData<String> getSelectedVillage() {
        return selectedVillage;
    }

    public LiveData<String> getSelectedVillageId() {
        return selectedVillageId;
    }

    public LiveData<String> getNumRefferalsWeek() {
        if (numRefferalsWeek == null) {
            numRefferalsWeek = new MutableLiveData<String>();
            weeklySummaryRepository.getReferralCounts(result -> numRefferalsWeek.setValue(result));
        } else {
            weeklySummaryRepository.getReferralCounts(result -> numRefferalsWeek.setValue(result));
        }

        return numRefferalsWeek;
    }

    public LiveData<String> getNumClosedRefferalsWeek() {
        if (numClosedRefferalsWeek == null) {
            numClosedRefferalsWeek = new MutableLiveData<String>();
            weeklySummaryRepository.getClosedRefferalCount(result -> numClosedRefferalsWeek.setValue(result));
        } else {
            weeklySummaryRepository.getClosedRefferalCount(result -> numClosedRefferalsWeek.setValue(result));
        }

        return numClosedRefferalsWeek;
    }

    public LiveData<String> getNumLinkageClosedThisAddo() {
        if (numLinkageClosedThisAddo == null) {
            numLinkageClosedThisAddo = new MutableLiveData<String>();
            weeklySummaryRepository.getnumLinkageClosedThisAddo(result -> numLinkageClosedThisAddo.setValue(result));
        } else {
            weeklySummaryRepository.getnumLinkageClosedThisAddo(result -> numLinkageClosedThisAddo.setValue(result));
        }
        return numLinkageClosedThisAddo;
    }

}
