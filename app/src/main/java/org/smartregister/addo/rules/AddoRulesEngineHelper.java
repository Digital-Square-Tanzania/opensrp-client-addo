package org.smartregister.addo.rules;

import com.vijay.jsonwizard.rules.RulesEngineHelper;

import org.smartregister.addo.util.DiabeticRiskCalculator;

public class AddoRulesEngineHelper  extends RulesEngineHelper {

    public double getDiabesityRiskScore(String age, String waistCircumference, String familyHistory,
                                        String systolicBloodPressure, String diastolicBloodPressure) {
        return  DiabeticRiskCalculator.calculateDiabeticRiskScore(age, waistCircumference, familyHistory,
                systolicBloodPressure, diastolicBloodPressure);
    }
}