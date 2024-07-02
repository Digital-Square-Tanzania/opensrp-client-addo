package org.smartregister.addo.sync.helper;

import org.smartregister.CoreLibrary;
import org.smartregister.addo.util.CoreConstants;
import org.smartregister.addo.util.Utils;
import org.smartregister.location.helper.LocationHelper;
import org.smartregister.repository.TaskRepository;
import org.smartregister.sync.helper.TaskServiceHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AddoTaskServiceHelper extends TaskServiceHelper {

    protected static AddoTaskServiceHelper instance;

    public AddoTaskServiceHelper(TaskRepository taskRepository) {
        super(taskRepository);
    }

    public static AddoTaskServiceHelper getInstance() {
        if (instance == null) {
            instance = new AddoTaskServiceHelper(CoreLibrary.getInstance().context().getTaskRepository());
        }
        return instance;
    }

    @Override
    protected List<String> getLocationIds() {
        // Added ward location to the list of locations
        ArrayList<String> locationIds = new ArrayList<>();
        String providerId = Utils.context().allSharedPreferences().fetchRegisteredANM();
        String userLocationId = Utils.context().allSharedPreferences().fetchUserLocalityId(providerId);
        locationIds.add(userLocationId);
        locationIds.addAll(Utils.getWardFacilitiesIds());
        return locationIds;
    }

    @Override
    protected Set<String> getPlanDefinitionIds() {
        Set<String> res = new HashSet<>();
        res.add(CoreConstants.ADDO_LINKAGE_PLAN_ID);
        res.add(CoreConstants.REFERRAL_PLAN_ID_2);
        return res;
    }
}
