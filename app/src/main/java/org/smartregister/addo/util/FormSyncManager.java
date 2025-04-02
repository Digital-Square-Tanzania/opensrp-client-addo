package org.smartregister.addo.util;

import android.content.Context;

import java.io.File;

import timber.log.Timber;

import androidx.annotation.NonNull;
import com.evernote.android.job.Job;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;

import java.util.concurrent.TimeUnit;

public class FormSyncManager {
    private final Context context;
    private final File formsDIR;
    private final File modifiedDates;
//    private final static String BASE_URL="https://raw.githubusercontent.com/Digital-Square-Tanzania/opensrp-client-addo/refs/heads/updatable-forms/";
    //run python -m http.server 7989 first to test this
    private final static String BASE_URL="http://192.168.1.135:7989//app/src/main/assets/";
    public FormSyncManager(){
        this(org.smartregister.family.util.Utils.context().applicationContext());
    }
    public FormSyncManager(Context context){
        this.context = context;
        this.formsDIR = new File(context.getFilesDir(),"updatable-forms/" + getFormsFolderName());
        if (!formsDIR.exists() && !formsDIR.mkdirs()){
            Timber.e("Failed to create updatable-forms directory in disk");
        }
        modifiedDates = new File(formsDIR,"../json_forms_modified_date.json");
    }

    private String getFormsFolderName(){
        String locale = context.getResources().getConfiguration().locale.getLanguage();
        String ext = locale.matches("(?i)en") ? "" : "-" + locale;
        return String.format("json.form%s/",ext);
    }

    public void fetchFormsOnline() {
        String dirNamePtn=".*" + getFormsFolderName() + ".*";
        int[] updatedFileCount = {0};

        JsonQ diskMetaData = JsonQ.fromIO(modifiedDates);
        JsonQ onlineMetadata = JsonQ.fromURL(BASE_URL + "json_forms_modified_date.json");
        onlineMetadata.where("form ~ $ ", dirNamePtn).forEach((k,v) ->{
            String form =v.str("form");
            String date = v.str("modifiedDate");
            File file=new File(formsDIR,"../"+form);

            boolean outdated = diskMetaData.where("modifiedDate < $ and form=$ ", date, form).hasThings();
            boolean shouldUpdate = !file.exists() || outdated;
            if (shouldUpdate) {
                String url = BASE_URL + form;
                updatedFileCount[0] += JsonQ.fromURL(url).toFile(file) ? 1 : 0;
            }
        });

        if(updatedFileCount[0]>0) onlineMetadata.toFile(modifiedDates);
        removeUnusedForms(diskMetaData,onlineMetadata);
    }

    public String getFormJson(String formName){
        return getForm(formName).toString();
    }

    public JsonQ getForm(String formName){
        formName = formName + ".json";
        File formFile = new File(formsDIR,formName);

        return  formFile.isFile()? JsonQ.fromIO(formFile) : JsonQ.fromAsset(context,getFormsFolderName()+formName);
    }

    private void removeUnusedForms(JsonQ oldMeta, JsonQ newMeta){
         oldMeta.forEach((k,v)->{
             String form=v.str("form");
             File file=new File(formsDIR,"../"+form);
             if(file.exists() && newMeta.where("form=$",form).isEmpty()){
                if(!file.delete()){
                    Timber.w("Could not remove unused  form file %s", file.getName());
                }
             }
         });
    }

    public static class FormSyncJob extends Job {
        public static final String JOB_TAG = "FormFetchingJob";
        @NonNull @Override protected Result onRunJob(@NonNull Params params) {
            new FormSyncManager(getContext()).fetchFormsOnline();
            return Result.SUCCESS;
        }
    }

    public void scheduleSyncingJob(int daysInterval) {
        // Cancel existing jobs with the same tag
        JobManager.instance().cancelAllForTag(FormSyncJob.JOB_TAG);
        new JobRequest.Builder(FormSyncJob.JOB_TAG)
                .setPeriodic(TimeUnit.DAYS.toMillis(daysInterval), TimeUnit.MINUTES.toMillis(15)) // flex window
                .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                .setUpdateCurrent(true) // equivalent to KEEP policy
//                .setPersisted(true) // survive reboots
                .build()
                .schedule();
    }

}
