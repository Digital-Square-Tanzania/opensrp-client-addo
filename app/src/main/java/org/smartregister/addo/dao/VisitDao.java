package org.smartregister.addo.dao;

import org.smartregister.chw.anc.domain.MemberObject;
import org.smartregister.chw.anc.domain.Visit;
import org.smartregister.dao.AbstractDao;

import java.util.Date;
import java.util.List;

public class VisitDao extends AbstractDao {

    public static Visit getLatestVisit(String baseEntityID, String visitType, String encounterType) {
        String sql = "select * from visits\n" +
                "inner join visit_details on visits.visit_id = visit_details.visit_id\n" +
                "where visits.base_entity_id = '" + baseEntityID + "'\n" +
                "and visits.visit_type = '" + visitType + "'\n" +
                "and visit_details.visit_key = 'encounter_type'\n" +
                "and visit_details.details = '" + encounterType + "'\n" +
                "order by visits.visit_date DESC limit 1;";

        DataMap<Visit> dataMap = cursor -> {
            Visit visit = new Visit();
            visit.setVisitId(getCursorValue(cursor, "visit_id"));
            visit.setVisitType(getCursorValue(cursor, "visit_type"));
            visit.setParentVisitID(getCursorValue(cursor, "parent_visit_id"));
            visit.setPreProcessedJson(getCursorValue(cursor, "pre_processed"));
            visit.setBaseEntityId(getCursorValue(cursor, "base_entity_id"));
            visit.setDate(new Date(Long.parseLong(getCursorValue(cursor, "visit_date"))));
            visit.setJson(getCursorValue(cursor, "visit_json"));
            visit.setFormSubmissionId(getCursorValue(cursor, "form_submission_id"));
            visit.setProcessed(getCursorIntValue(cursor, "processed") == 1);
            visit.setCreatedAt(new Date(Long.parseLong(getCursorValue(cursor, "created_at"))));
            visit.setUpdatedAt(new Date(Long.parseLong(getCursorValue(cursor, "updated_at"))));

            return visit;
        };

        List<Visit> visits = readData(sql, dataMap);
        if (visits == null || visits.size() != 1)
            return null;

        return visits.get(0);
    }

    public static MemberObject getMember(String baseEntityID) {
        String sql = "select " +
                "m.base_entity_id , " +
                "m.unique_id , " +
                "m.relational_id , " +
                "m.dob , " +
                "m.first_name , " +
                "m.middle_name , " +
                "m.last_name , " +
                "m.gender , " +
                "m.phone_number , " +
                "m.other_phone_number , " +
                "f.first_name family_name , " +
                "f.primary_caregiver , " +
                "f.family_head , " +
                "fh.first_name family_head_first_name , " +
                "fh.middle_name family_head_middle_name, " +
                "fh.last_name family_head_last_name, " +
                "fh.phone_number family_head_phone_number " +
                "from " +
                "ec_family_member m " +
                "inner join ec_family f on m.relational_id = f.base_entity_id " +
                "left join ec_family_member fh on fh.base_entity_id = f.family_head where m.base_entity_id = '"+ baseEntityID +"'";

        DataMap<MemberObject> dataMap = cursor -> {
            MemberObject memberObject = new MemberObject();
            memberObject.setLastMenstrualPeriod(getCursorValue(cursor, "last_menstrual_period"));
            memberObject.setChwMemberId(getCursorValue(cursor, "unique_id", ""));
            memberObject.setBaseEntityId(getCursorValue(cursor, "base_entity_id", ""));
            memberObject.setFamilyBaseEntityId(getCursorValue(cursor, "relational_id", ""));
            memberObject.setFamilyHead(getCursorValue(cursor, "family_head", ""));

            String familyHeadName = getCursorValue(cursor, "family_head_first_name", "") + " "
                    + getCursorValue(cursor, "family_head_middle_name", "");

            familyHeadName = (familyHeadName.trim() + " " + getCursorValue(cursor, "family_head_last_name", "")).trim();

            memberObject.setFamilyHeadName(familyHeadName);
            memberObject.setFamilyHeadPhoneNumber(getCursorValue(cursor, "family_head_phone_number", ""));
            memberObject.setPrimaryCareGiver(getCursorValue(cursor, "primary_caregiver"));
            memberObject.setFamilyName(getCursorValue(cursor, "family_name", ""));
            memberObject.setLastInteractedWith(getCursorValue(cursor, "last_interacted_with"));
            memberObject.setFirstName(getCursorValue(cursor, "first_name", ""));
            memberObject.setMiddleName(getCursorValue(cursor, "middle_name", ""));
            memberObject.setLastName(getCursorValue(cursor, "last_name", ""));
            memberObject.setDob(getCursorValue(cursor, "dob"));
            memberObject.setPhoneNumber(getCursorValue(cursor, "phone_number", ""));
            memberObject.setDateCreated(getCursorValue(cursor, "date_created"));
            memberObject.setAddress(getCursorValue(cursor, "village_town"));

            return memberObject;
        };

        List<MemberObject> res = readData(sql, dataMap);
        if (res == null || res.size() != 1)
            return null;

        return res.get(0);
    }
}
