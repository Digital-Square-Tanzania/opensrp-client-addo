package org.smartregister.addo.rules;

import com.vijay.jsonwizard.rules.RulesEngineHelper;

import org.smartregister.addo.util.DiabeticRiskCalculator;
import android.content.Context;


public class AddoRulesEngineHelper  extends RulesEngineHelper {

    Context context;
    public AddoRulesEngineHelper(Context context) {
        this.context = context;
    }

    public double getDiabesityRiskScore(String age, String familyHistory, String waistCircumference,
                                        String systolicBloodPressure, String diastolicBloodPressure) {
        return DiabeticRiskCalculator.calculateDiabeticRiskScore(age, familyHistory, waistCircumference,
                systolicBloodPressure, diastolicBloodPressure);
    }

    public String getDiabesityRiskCondition(String riskScore, String systolicBloodPressure,
                                            String diastolicBloodPressure) {
        return DiabeticRiskCalculator.getDiabesityRiskCondition(context, riskScore, systolicBloodPressure,
                diastolicBloodPressure);
    }
}