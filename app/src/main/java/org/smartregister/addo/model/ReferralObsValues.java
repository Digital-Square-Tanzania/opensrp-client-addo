package org.smartregister.addo.model;

import java.util.List;

public class ReferralObsValues {
    private List<String> problemValues;
    private List<String> problemHumanReadableValues;


    public ReferralObsValues(List<String> problemValues, List<String> problemHumanReadableValues) {
        this.problemValues = problemValues;
        this.problemHumanReadableValues = problemHumanReadableValues;
    }

    public List<String> getHumanReadableValues() {
        return problemHumanReadableValues;
    }

    public void setHumanReadableValues(List<String> problemHumanReadableValues) {
        this.problemHumanReadableValues = problemHumanReadableValues;
    }

    public List<String> getValues() {
        return problemValues;
    }

    public void setValues(List<String> problemValues) {
        this.problemValues = problemValues;
    }
}
