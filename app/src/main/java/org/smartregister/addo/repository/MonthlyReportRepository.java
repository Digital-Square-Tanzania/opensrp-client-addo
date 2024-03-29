package org.smartregister.addo.repository;

import android.database.Cursor;

import androidx.annotation.VisibleForTesting;

import org.smartregister.CoreLibrary;
import org.smartregister.addo.application.AddoApplication;
import org.smartregister.addo.util.ChartUtil;
import org.smartregister.addo.util.QueryBuilder;
import org.smartregister.family.util.AppExecutors;
import org.smartregister.repository.Repository;

public class MonthlyReportRepository {

    private String anmUser;
    private final Repository repository;
    private AppExecutors appExecutors;


    public MonthlyReportRepository(){
        this(AddoApplication.getInstance().getRepository(), new AppExecutors());
    }

    @VisibleForTesting
    MonthlyReportRepository(Repository repository, AppExecutors appExecutors) {
        this.repository = repository;
        this.appExecutors = appExecutors;
        anmUser = CoreLibrary.getInstance().context().allSharedPreferences().fetchRegisteredANM();
    }

    /////////// CURRENT MONTH INDICATORS

    public String getCurrentMonthVisit(){

        String query = "select base_entity_id, date(datetime(visit_date/1000, 'unixepoch')) as dt " +
                "from visits "+
                "where visit_type in ('Adolescent ADDO Visit','Child ADDO Visit', 'ANC ADDO Visit', 'PNC ADDO Visit', 'Other Member ADDO Visit') " +
                "and datetime(visit_date/1000, 'unixepoch') > date('now', 'start of month') " +
                "and visit_json like \"%"+anmUser+"%\" " +
                "group by dt, base_entity_id";

        return getQueryCount(query);
    }

    public String getCurrentMonthAttendedClients(){

        int totalVisits = 0;

        int childCount = Integer.parseInt(getCurrentMonthChildVisits());
        int ancCount = Integer.parseInt(getCurrentMonthANCVisits());
        int pncCount = Integer.parseInt(getCurrentMonthPNCVisits());
        int adolescentCount = Integer.parseInt(getCurrentMonthAdolescentVisit());
        int otherVisitCount = Integer.parseInt(getCurrentMonthOtherMemberVisits());

        totalVisits = childCount + ancCount + pncCount + adolescentCount + otherVisitCount;

        return totalVisits+"";
    }

    public String getCurrentMonthChildVisits(){
        String query = "select base_entity_id, date(datetime(visit_date/1000, 'unixepoch')) as date_visited, visit_json, visit_type " +
                "from visits " +
                "where datetime(visit_date/1000, 'unixepoch') > date('now', 'start of month') " +
                "and visit_type in ('Child ADDO Visit' ) " +
                "and visit_json like \"%"+anmUser+"%\" "+
                "group by base_entity_id, date_visited";
        return getQueryCount(query);
    }

    public String getCurrentMonthAdolescentVisit(){
        String query = "select base_entity_id, date(datetime(visit_date/1000, 'unixepoch')) as date_visited, visit_json, visit_type " +
                "from visits " +
                "where datetime(visit_date/1000, 'unixepoch') > date('now', 'start of month') " +
                "and visit_type in ('Adolescent ADDO Visit') " +
                "and visit_json like \"%"+anmUser+"%\" "+
                "group by base_entity_id, date_visited";
        return getQueryCount(query);
    }

    public String getCurrentMonthANCVisits(){
        String query = "select base_entity_id, date(datetime(visit_date/1000, 'unixepoch')) as date_visited, visit_json, visit_type " +
                "from visits " +
                "where datetime(visit_date/1000, 'unixepoch') > date('now', 'start of month') " +
                "and visit_type in ('ANC ADDO Visit' ) " +
                "and visit_json like \"%"+anmUser+"%\" "+
                "group by base_entity_id, date_visited";
        return getQueryCount(query);
    }

    public String getCurrentMonthPNCVisits(){
        String query = "select base_entity_id, date(datetime(visit_date/1000, 'unixepoch')) as date_visited, visit_json, visit_type " +
                "from visits " +
                "where datetime(visit_date/1000, 'unixepoch') > date('now', 'start of month') " +
                "and visit_type in ('PNC ADDO Visit' ) " +
                "and visit_json like \"%"+anmUser+"%\" "+
                "group by base_entity_id, date_visited";
        return getQueryCount(query);
    }

    public String getCurrentMonthOtherMemberVisits(){
        String query = "select base_entity_id, date(datetime(visit_date/1000, 'unixepoch')) as date_visited, visit_json, visit_type " +
                "from visits " +
                "where datetime(visit_date/1000, 'unixepoch') > date('now', 'start of month') " +
                "and visit_type in ('Other Member ADDO Visit') " +
                "and visit_json like \"%"+anmUser+"%\" "+
                "group by base_entity_id, date_visited";
        return getQueryCount(query);
    }

    /**
     * Referrals issued by this ADDO to the facility
     * @return
     */
    public String getReferralsToFacilityThisMonth(){
        String query = "select * from task where code = 'Referral' and " +
                "owner = '"+anmUser+"' and " +
                currentMonthLimit();
        return getQueryCount(query);
    }

    public String getCompletedReferralsToFacilityThisMonth(){
        String query = "select * from task where code = 'Referral' and " +
                "owner = '"+anmUser+"' and status in ('IN_PROGRESS', 'COMPLETED') and " +
                currentMonthLimit();
        return getQueryCount(query);
    }

    /////////LAST MONTH BEGINS HERE

    public String getTotalVisitsConductedLastMonth(){
        String query = "select * from visits where " +
                "visit_type in ('Adolescent ADDO Visit','Child ADDO Visit','ANC ADDO Visit','PNC ADDO Visit', 'Other Member ADDO Visit')" +
                " and datetime(visit_date/1000, 'unixepoch') > date('now', 'start of month', '-1 months) and" +
                " datetime(visit_date/1000, 'unixepoch') < date('now', 'start of month')";
        return getQueryCount(query);
    }

    public String getReferralsToFacilityLastMonth(){
        String query = "select * from task where code = 'Referral' and " +
                "owner = '"+anmUser+"' and " +
                lastMonthLimit();
        return getQueryCount(query);
    }

    public String getCompletedReferralsToFacilityLastMonth(){
        String query = "select * from task where code = 'Referral' and " +
                "owner = '"+anmUser+"' and status in ('IN_PROGRESS', 'COMPLETED') and " +
                lastMonthLimit();
        return getQueryCount(query);
    }

    private String lastMonthLimit(){
        return "datetime(authored_on/1000, 'unixepoch') < date('now', 'start of month') and " +
                "datetime(authored_on/1000, 'unixepoch') > date('now', 'start of month', '-1 months') ";
    }

    private String currentMonthLimit(){
        return "datetime(authored_on/1000, 'unixepoch') > date('now', 'start of month') ";
    }

    private String getQueryCount(String query){
        Cursor cursor = null;
        try {
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

}
