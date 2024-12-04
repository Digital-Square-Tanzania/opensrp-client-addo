package org.smartregister.addo.util;

import static org.smartregister.addo.util.Constants.HOME_VISIT_GROUP;

import com.google.gson.Gson;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.chw.anc.AncLibrary;
import org.smartregister.chw.anc.domain.Visit;
import org.smartregister.chw.anc.domain.VisitDetail;
import org.smartregister.chw.anc.repository.VisitDetailsRepository;
import org.smartregister.chw.anc.repository.VisitRepository;
import org.smartregister.chw.anc.util.Constants;
import org.smartregister.chw.anc.util.NCUtils;
import org.smartregister.chw.anc.util.VisitUtils;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.repository.AllSharedPreferences;

import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public class AddoVisitUtils extends VisitUtils {

    public static void processVisits(String baseEntityID) throws Exception {
        processVisits(AncLibrary.getInstance().visitRepository(), AncLibrary.getInstance().visitDetailsRepository(), baseEntityID);
    }

    public static void processVisits(VisitRepository visitRepository, VisitDetailsRepository visitDetailsRepository, String baseEntityID) throws Exception {
        Calendar calendar = Calendar.getInstance();
        List<Visit> visits = StringUtils.isNotBlank(baseEntityID) ?
                visitRepository.getAllUnSynced(calendar.getTime().getTime(), baseEntityID) :
                visitRepository.getAllUnSynced(calendar.getTime().getTime());
        processVisits(visits, visitRepository, visitDetailsRepository);
    }


    public static void processVisits(List<Visit> visits, VisitRepository visitRepository, VisitDetailsRepository visitDetailsRepository) throws Exception {
        String visitGroupId = UUID.randomUUID().toString();
        for (Visit v : visits) {
            if (!v.getProcessed()) {

                // persist to db
                Event baseEvent = new Gson().fromJson(v.getPreProcessedJson(), Event.class);
                if (StringUtils.isBlank(baseEvent.getFormSubmissionId()))
                    baseEvent.setFormSubmissionId(UUID.randomUUID().toString());

                baseEvent.addDetails(HOME_VISIT_GROUP, visitGroupId);

                AllSharedPreferences allSharedPreferences = AncLibrary.getInstance().context().allSharedPreferences();
                NCUtils.addEvent(allSharedPreferences, baseEvent);

                // process details
                processVisitDetails(visitDetailsRepository, v.getVisitId());

                visitRepository.completeProcessing(v.getVisitId());
            }
        }

        // process after all events are saved
        NCUtils.startClientProcessing();
    }

    private static void processVisitDetails(VisitDetailsRepository visitDetailsRepository, String visitID) throws Exception {
        List<VisitDetail> visitDetailList = visitDetailsRepository.getVisits(visitID);
        for (VisitDetail visitDetail : visitDetailList) {
            if (!visitDetail.getProcessed()) {
                if (Constants.HOME_VISIT_TASK.SERVICE.equalsIgnoreCase(visitDetail.getPreProcessedType())) {
                    visitDetailsRepository.completeProcessing(visitDetail.getVisitDetailsId());
                    continue;
                }


                if (
                        Constants.HOME_VISIT_TASK.VACCINE.equalsIgnoreCase(visitDetail.getParentCode()) ||
                                Constants.HOME_VISIT_TASK.VACCINE.equalsIgnoreCase(visitDetail.getPreProcessedType())
                ) {
                    //saveVisitDetailsAsVaccine(visitGroupId, visitDetail, baseEntityID, visit.getDate());
                    visitDetailsRepository.completeProcessing(visitDetail.getVisitDetailsId());
                    continue;
                }

                visitDetailsRepository.completeProcessing(visitDetail.getVisitDetailsId());
            }
        }
    }

}
