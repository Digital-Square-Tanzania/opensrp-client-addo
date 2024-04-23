package org.smartregister.addo.dao;

import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.addo.application.AddoApplication;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.dao.AbstractDao;
import org.smartregister.sync.helper.ECSyncHelper;

import java.util.List;

import timber.log.Timber;

public class EventDao extends AbstractDao {


    public static List<Event> getEvents(String baseEntityID, String eventType, int limit) {
        String sql = "select json from event where baseEntityId = '" + baseEntityID + "' COLLATE NOCASE and eventType = '" + eventType + "' COLLATE NOCASE order by updatedAt desc limit " + limit;

        final ECSyncHelper syncHelper = AddoApplication.getInstance().getEcSyncHelper();
        DataMap<Event> dataMap = c -> {
            try {
                return syncHelper.convert(new JSONObject(getCursorValue(c, "json")), Event.class);
            } catch (JSONException e) {
                Timber.e(e);
            }
            return null;
        };

        return AbstractDao.readData(sql, dataMap);
    }

    @Nullable
    public static Event getLatestEvent(String baseEntityID, List<String> eventTypes) {
        StringBuilder types = new StringBuilder();
        for (String eventType : eventTypes) {
            if (types.length() > 0)
                types.append(" , ");

            types.append("'").append(eventType).append("'");
        }

        String sql = "select json from event where baseEntityId = '" + baseEntityID + "' COLLATE NOCASE and eventType in (" + types.toString() + ") COLLATE NOCASE order by updatedAt desc limit 1";

        final ECSyncHelper syncHelper = AddoApplication.getInstance().getEcSyncHelper();
        DataMap<Event> dataMap = c -> {
            try {
                return syncHelper.convert(new JSONObject(getCursorValue(c, "json")), Event.class);
            } catch (JSONException e) {
                Timber.e(e);
            }
            return null;
        };

        List<Event> res = AbstractDao.readData(sql, dataMap);
        if (res != null && res.size() > 0)
            return res.get(0);

        return null;
    }

    /**
     * Deletes an event from the database by its event Type.
     * This method deletes the event with the specified event ID from the event table in the database.
     *
     * @param eventType The event type to be deleted. Must not be null.
     *                The event with the specified Type will be removed from the database.
     */
    public static void deleteEventByEventType(String eventType) {
        String sql2 = "delete from event where eventType = '" + eventType + "'";
        updateDB(sql2);
    }
}