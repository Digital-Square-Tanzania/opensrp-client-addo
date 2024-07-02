package org.smartregister.addo.util;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.utils.FormUtils;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.CoreLibrary;
import org.smartregister.addo.application.AddoApplication;
import org.smartregister.domain.Task;
import org.smartregister.location.helper.LocationHelper;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.repository.BaseRepository;
import org.smartregister.repository.TaskRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import timber.log.Timber;

public class ReferralUtils {

    public static void createReferralTask(String baseEntityId, String focus, String jsonString, String villageTown, String facility, String formSubmissionId) {
        Task task = new Task();
        task.setIdentifier(UUID.randomUUID().toString());

        String referralProblems = getReferralProblems(jsonString);
        AllSharedPreferences allSharedPreferences = CoreLibrary.getInstance().context().allSharedPreferences();
        LocationHelper locationHelper = LocationHelper.getInstance();

        task.setPlanIdentifier(CoreConstants.REFERRAL_PLAN_ID_2);
        task.setGroupIdentifier(facility);
        task.setStatus(Task.TaskStatus.READY);
        task.setBusinessStatus(CoreConstants.BUSINESS_STATUS.REFERRED);
        task.setPriority(3);
        task.setCode(CoreConstants.JsonAssets.REFERRAL_CODE);
        task.setDescription(referralProblems);
        task.setFocus(focus);
        task.setForEntity(baseEntityId);
        DateTime now = new DateTime();
        task.setExecutionStartDate(now);
        task.setAuthoredOn(now);
        task.setLastModified(now);
        task.setOwner(allSharedPreferences.fetchRegisteredANM());
        task.setSyncStatus(BaseRepository.TYPE_Created);
        task.setRequester(allSharedPreferences.getANMPreferredName(allSharedPreferences.fetchRegisteredANM()));
        task.setLocation(locationHelper.getOpenMrsLocationId(villageTown));
        task.setReasonReference(formSubmissionId);
        AddoApplication.getInstance().getTaskRepository().addOrUpdate(task);
    }

    private static String getReferralProblems(String jsonString) {
        String[] dangerSignKeysArray = { Constants.DangerSignKeys.CHILD, Constants.DangerSignKeys.ANC, Constants.DangerSignKeys.PNC, Constants.DangerSignKeys.ADOLESCENT};
        String referralProblems = "";
        List<String> formValues = new ArrayList<>();
        try {
            JSONObject problemJson = new JSONObject(jsonString);
            JSONArray fields = FormUtils.getMultiStepFormFields(problemJson);
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                String key = field.getString("key");
                if (Arrays.asList(dangerSignKeysArray).contains(key)) {
                    if (field.has(JsonFormConstants.TYPE) && JsonFormConstants.CHECK_BOX.equals(field.getString(JsonFormConstants.TYPE))) {
                        if (field.has(JsonFormConstants.OPTIONS_FIELD_NAME)) {
                            JSONArray options = field.getJSONArray(JsonFormConstants.OPTIONS_FIELD_NAME);
                            String values = getCheckBoxSelectedOptions(options);
                            if (StringUtils.isNotEmpty(values) && !(values.equalsIgnoreCase("Yes") || values.equalsIgnoreCase("Ndiyo"))) {
                                formValues.add(values);
                            }
                        }
                    } else if (field.has(JsonFormConstants.TYPE) && JsonFormConstants.RADIO_BUTTON.equals(field.getString(JsonFormConstants.TYPE))) {
                        if (field.has(JsonFormConstants.OPTIONS_FIELD_NAME) && field.has(JsonFormConstants.VALUE)) {
                            JSONArray options = field.getJSONArray(JsonFormConstants.OPTIONS_FIELD_NAME);
                            String value = field.getString(JsonFormConstants.VALUE);
                            String values = getRadioButtonSelectedOptions(options, value);
                            if (StringUtils.isNotEmpty(values)) {
                                formValues.add(values);
                            }
                        }
                    }
                }
            }

            referralProblems = StringUtils.join(formValues, ", ");
        } catch (JSONException e) {
            Timber.e(e, "CoreReferralUtils --> getReferralProblems");
        }
        return referralProblems;
    }

    private static String getRadioButtonSelectedOptions(@NotNull JSONArray options, String value) {
        String selectedOptionValues = "";
        try {
            for (int i = 0; i < options.length(); i++) {
                JSONObject option = options.getJSONObject(i);
                if ((option.has(JsonFormConstants.KEY) && value.equals(option.getString(JsonFormConstants.KEY))) && (option.has(JsonFormConstants.TEXT) && StringUtils.isNotEmpty(option.getString(JsonFormConstants.VALUE)))) {
                    selectedOptionValues = option.getString(JsonFormConstants.TEXT);
                }
            }
        } catch (JSONException e) {
            Timber.e(e, "CoreReferralUtils --> getSelectedOptions");
        }

        return selectedOptionValues;
    }

    private static String getCheckBoxSelectedOptions(@NotNull JSONArray options) {
        String selectedOptionValues = "";
        List<String> selectedValue = new ArrayList<>();
        try {
            for (int i = 0; i < options.length(); i++) {
                JSONObject option = options.getJSONObject(i);
                boolean useItem = true;

                if (option.optBoolean(CoreConstants.IGNORE, false)) {
                    useItem = false;
                }

                if (option.has(JsonFormConstants.VALUE) && Boolean.valueOf(option.getString(JsonFormConstants.VALUE))
                        && useItem) { //Don't add values for  items with other
                    selectedValue.add(option.getString(JsonFormConstants.TEXT));
                }
            }
            selectedOptionValues = StringUtils.join(selectedValue, ", ");
        } catch (JSONException e) {
            Timber.e(e, "CoreReferralUtils --> getSelectedOptions");
        }

        return selectedOptionValues;
    }

    public static boolean hasReferralTask(String planId, String groupId, String forEntity, String code) {

        return !AddoApplication.getInstance().getTaskRepository().getTasksByEntityAndCode(planId, groupId, forEntity, code).isEmpty();
    }

    public static void closeLinkageAndOpenFollowUp(String baseEntityId, String villageTown) {

        if (StringUtils.isBlank(baseEntityId))
            return;

        List<Task> updatedTasks = updateLinkageTasksFor(baseEntityId);

        // If there is a task updated for this entity id then open a followup task
        if (!updatedTasks.isEmpty()) {
            for (Task updatedTask : updatedTasks) {
                createFollowUpTask(baseEntityId, updatedTask.getFocus(), updatedTask.getDescription(), villageTown, updatedTask.getIdentifier());
            }
        }

    }

    private static List<Task> updateLinkageTasksFor(String baseEntityId) {

        TaskRepository taskRepository = AddoApplication.getInstance().getTaskRepository();
        Set<Task> tasks = taskRepository.getTasksByEntityAndCode(
                CoreConstants.ADDO_LINKAGE_PLAN_ID,
                Utils.getAddoLocationId(),
                baseEntityId,
                CoreConstants.JsonAssets.LINKAGE_CODE);

        List<Task> updatedTasks = new ArrayList<>();
        for (Task task : tasks) {
            if (task.getStatus() == Task.TaskStatus.READY) {
                task.setStatus(Task.TaskStatus.COMPLETED);
                task.setSyncStatus(BaseRepository.TYPE_Unsynced);
                task.setLastModified(new DateTime());
                taskRepository.addOrUpdate(task);
                updatedTasks.add(task);
            }

        }

        return updatedTasks;
    }

    private static void createFollowUpTask(String baseEntityId, String focus, String followUpProblems, String villageTown, String updatedTaskId) {

        Task task = new Task();
        task.setIdentifier(UUID.randomUUID().toString());

        AllSharedPreferences allSharedPreferences = CoreLibrary.getInstance().context().allSharedPreferences();
        LocationHelper locationHelper = LocationHelper.getInstance();

        task.setPlanIdentifier(CoreConstants.REFERRAL_PLAN_ID);
        task.setGroupIdentifier(locationHelper.getOpenMrsLocationId(villageTown));
        task.setStatus(Task.TaskStatus.READY);
        task.setBusinessStatus(CoreConstants.BUSINESS_STATUS.TO_FOLLOW_UP);
        task.setPriority(1);
        task.setCode(CoreConstants.JsonAssets.FOLLOW_UP_VISIT_CODE);
        task.setDescription(followUpProblems);
        task.setFocus(focus);
        task.setForEntity(baseEntityId);
        DateTime now = new DateTime();
        task.setExecutionStartDate(now);
        task.setAuthoredOn(now);
        task.setLastModified(now);
        task.setOwner(allSharedPreferences.fetchRegisteredANM());
        task.setSyncStatus(BaseRepository.TYPE_Created);
        task.setRequester(allSharedPreferences.getANMPreferredName(allSharedPreferences.fetchRegisteredANM()));
        task.setLocation(locationHelper.getOpenMrsLocationId(villageTown));
        task.setReasonReference(updatedTaskId);
        AddoApplication.getInstance().getTaskRepository().addOrUpdate(task);
    }

}
