package org.smartregister.addo.domain;

public class DukaLaDawaPayload {
    private String baseEntityId;
    private String dob;
    private String gender;
    private String prescriptionNote;

    public DukaLaDawaPayload(String baseEntityId, String dob, String gender, String prescriptionNote) {
        this.baseEntityId = baseEntityId;
        this.dob = dob;
        this.gender = gender;
        this.prescriptionNote = prescriptionNote;
    }
}
