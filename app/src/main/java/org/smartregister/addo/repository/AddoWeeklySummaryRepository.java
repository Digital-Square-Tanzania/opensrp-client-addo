package org.smartregister.addo.repository;

import android.database.Cursor;

import androidx.annotation.VisibleForTesting;

import org.smartregister.CoreLibrary;
import org.smartregister.addo.application.AddoApplication;
import org.smartregister.domain.Task;
import org.smartregister.family.util.AppExecutors;
import org.smartregister.repository.Repository;

/**
 * Created by Kassim Sheghembe on 2021-08-18
 */
public class AddoWeeklySummaryRepository {

    private final Repository repository;
    private AppExecutors appExecutors;

    @VisibleForTesting
    AddoWeeklySummaryRepository(Repository repository, AppExecutors appExecutors) {
        this.repository = repository;
        this.appExecutors = appExecutors;
    }

    public AddoWeeklySummaryRepository() {
        this(AddoApplication.getInstance().getRepository(), new AppExecutors());
    }

    public void getReferralCounts(WeeklySummaryCallback callback) {

        Runnable runnable = () -> {
            final String numRef;
            numRef = queryReferralCounts();
            appExecutors.mainThread().execute(() -> callback.onComplete(numRef));
        };

        appExecutors.diskIO().execute(runnable);
    }

    private String queryReferralCounts() {
        Cursor cursor = null;
        try {
            String query = "select * from task where code = 'Linkage' and " +
                    "date(datetime(start/1000, 'unixepoch')) > datetime('now', 'start of day', '-6 days');";
            cursor = repository.getReadableDatabase().rawQuery(query, null);
            cursor.moveToFirst();
            return Integer.toString(cursor.getCount());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return "0";
    }

    public void getClosedRefferalCount(WeeklySummaryCallback weeklySummaryCallback) {

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                final String closedRefCounts;
                closedRefCounts = queryClosedReferralCounts();
                appExecutors.mainThread().execute(() -> weeklySummaryCallback.onComplete(closedRefCounts));
            }
        };
        appExecutors.diskIO().execute(runnable);
    }

    private String queryClosedReferralCounts() {
        Cursor cursor = null;

        try {
            String query = "select * from task where code = 'Linkage' and " +
                    "date(datetime(start/1000, 'unixepoch')) > datetime('now', 'start of day', '-6 days') and " +
                    "status IN ('" + Task.TaskStatus.COMPLETED + "', '" + Task.TaskStatus.IN_PROGRESS +"');";
            cursor = repository.getReadableDatabase().rawQuery(query, null);
            cursor.moveToFirst();
            return Integer.toString(cursor.getCount());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return "0";
    }

    public void getnumLinkageClosedThisAddo(WeeklySummaryCallback weeklySummaryCallback) {
        Runnable runnable = () -> {
            final String addoClosedLinkageCounts;
            addoClosedLinkageCounts = queryAddoLinkageClosure();
            appExecutors.mainThread().execute(() -> weeklySummaryCallback.onComplete(addoClosedLinkageCounts));
        };
        appExecutors.diskIO().execute(runnable);
    }

    private String queryAddoLinkageClosure() {
        String addoUser = CoreLibrary.getInstance().context().allSharedPreferences().fetchRegisteredANM();
        Cursor cursor = null;

        try {
            // Closed at a specific addo will generate Linkage_Followup Task, so we can check all tasks that have a Linkage_Followup with a reason reference the
            // of that task or count of linkage_followup task (because the only thing to create linkage follow up task is if the linKage is there in the first place)
            String query = "select * from task where code = 'Linkage_Followup' and " +
                    "date(datetime(start/1000, 'unixepoch')) > datetime('now', 'start of day', '-6 days') and " +
                    "owner = \'" + addoUser + "\' ";
            cursor = repository.getReadableDatabase().rawQuery(query, null);
            cursor.moveToFirst();
            return Integer.toString(cursor.getCount());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return "0";
    }

    public interface WeeklySummaryCallback {
        void onComplete(java.lang.String result);
    }
}
