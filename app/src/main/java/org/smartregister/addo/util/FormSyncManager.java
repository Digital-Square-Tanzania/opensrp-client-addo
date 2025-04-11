package org.smartregister.addo.util;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;

import timber.log.Timber;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class FormSyncManager {
    private final Context context;
    private final File formsDIR;
    private final File modifiedDates;
    private static long lastTimeFetched = 0;
    private static final long HALF_HOUR = TimeUnit.MINUTES.toMillis(30);
    private static final List<String> ALLOWED_DOMAINS = Arrays.asList("raw.githubusercontent.com","ucs.nacp.go.tz");

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
    public void fetchOnlineForms(){
        try{updateForms();}
        catch (Exception e){Timber.e(e);}
    }
    public String getFormJson(String formName){
        return getForm(formName).toString();
    }
    public JsonQ getForm(String formName){
        String filename = formName + ".json";
        File formFile = new File(formsDIR,filename);

        return  formFile.isFile()? JsonQ.fromIO(formFile) : JsonQ.fromAsset(context,getFormsFolderName()+filename);
    }
    private String getFormsFolderName(){
        String locale = context.getResources().getConfiguration().locale.getLanguage();
        String ext = locale.matches("(?i)en") ? "" : "-" + locale;
        return String.format("json.form%s/",ext);
    }
    private synchronized boolean canSkipFetchingForNow(){
        long now = System.currentTimeMillis();
        lastTimeFetched = now - lastTimeFetched > HALF_HOUR ? now: lastTimeFetched ;
        return lastTimeFetched != now;
    }
    private void updateForms() {
        if(canSkipFetchingForNow()) return;

        String dirNamePtn=".*" + getFormsFolderName() + ".*";
        int[] updatedFileCount = {0};

        JsonQ diskMetaData = JsonQ.fromIO(modifiedDates);
        JsonQ onlineMetadata = fromOnline("json_forms_modified_date.json");
        onlineMetadata.where("form ~ $ ", dirNamePtn).forEach((k,v) ->{
            String form =v.str("form");
            String date = v.str("modifiedDate");
            File file=new File(formsDIR,"../"+form);

            boolean outdated = diskMetaData.where("modifiedDate < $ and form=$ ", date, form).hasThings();
            boolean shouldUpdate = !file.exists() || outdated;
            if (shouldUpdate) {
                updatedFileCount[0] += fromOnline(form).toFile(file) ? 1 : 0;
            }
        });

        if(updatedFileCount[0]>0) onlineMetadata.toFile(modifiedDates);
        removeUnusedForms(diskMetaData,onlineMetadata);
    }
    private void removeUnusedForms(JsonQ oldMeta, JsonQ newMeta){
         oldMeta.forEach((k,v)->{
             String form=v.str("form");
             File file=new File(formsDIR,"../"+form);
             if(file.exists() && newMeta.where("form=$",form).isEmpty() && !file.delete()){
                    Timber.w("Could not remove unused  form file %s", file.getName());
             }
         });
    }
    private JsonQ fromOnline(String path){
        String base = "https://raw.githubusercontent.com/Digital-Square-Tanzania/opensrp-client-addo/refs/heads/online-forms/app/src/main/assets/";
        return JsonQ.fromURL(base+path);
    }
}
