package org.smartregister.addo.rules;
import android.content.Context;

import com.vijay.jsonwizard.rules.RuleConstant;
import com.vijay.jsonwizard.rules.RulesEngineFactory;
import com.vijay.jsonwizard.rules.RulesEngineHelper;

import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rule;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.mvel.MVELRuleFactory;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

public class AddoRulesEngineFactory extends RulesEngineFactory {
    private Map<String, String> globalValues;
    private AddoRulesEngineHelper addoRulesEngineHelper;
    private String selectedRuleName;

    private Context context;

    private Map<String, Rules> ruleMap;
    private Rules diabetesRiskRules;
    private String RULE_FOLDER_PATH = "rule/";


    public AddoRulesEngineFactory(Context context, Map<String, String> globalValues) {
        super(context, globalValues);
        this.globalValues = globalValues;
        this.ruleMap = new HashMap<>();
        this.context = context;
        this.addoRulesEngineHelper = new AddoRulesEngineHelper(this.context);

    }

    @Override
    protected Facts initializeFacts(Facts facts) {
        if (globalValues != null) {
            for (Map.Entry<String, String> entry : globalValues.entrySet()) {
                facts.put(RuleConstant.PREFIX.GLOBAL + entry.getKey(), getValue(entry.getValue()));
            }
            facts.asMap().putAll(globalValues);
        }

        selectedRuleName = facts.get(RuleConstant.SELECTED_RULE);

        facts.put("helper", addoRulesEngineHelper);
        return facts;
    }

    @Override
    public boolean beforeEvaluate(Rule rule, Facts facts) {
        return selectedRuleName != null && selectedRuleName.equals(rule.getName());
    }

    @Override
    public String getCalculation(Facts calculationFact, String ruleFilename) {
        // Special handling for diabetes risk calculation rule (No need to format the calculation result)
        if (ruleFilename.equals("diabetes_hypertension_screening_calculation.yml")) {
            Facts facts = this.initializeFacts(calculationFact);
            facts.put("calculation", "");

           this.diabetesRiskRules = getRulesFromAsset(this.RULE_FOLDER_PATH + ruleFilename);
            this.processDefaultRules(this.diabetesRiskRules, facts);
            if (selectedRuleName != null && selectedRuleName.equals("step4_diabetes_risk_score_output"))
                return facts.get("calculation") != null ? facts.get("calculation").toString() : "0.0";
            return this.formatCalculationReturnValue(facts.get("calculation"));
        } else {
            return super.getCalculation(calculationFact, ruleFilename);
        }
    }

    public Rules getRulesFromAsset(String fileName) {
        try {
            if (!ruleMap.containsKey(fileName)) {

                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(context.getAssets().open(fileName)));
                ruleMap.put(fileName, MVELRuleFactory.createRulesFrom(bufferedReader));
            }
            return ruleMap.get(fileName);
        } catch (Exception e) {
            return null;
        }
    }


    private String formatCalculationReturnValue(Object rawValue) {
        String value = String.valueOf(rawValue).trim();
        if (value.isEmpty()) {
            return "";
        } else if (rawValue instanceof Map) {
            return (new JSONObject((Map)rawValue)).toString();
        } else {
            if (value.contains(".")) {
                try {
                    value = String.valueOf((float)Math.round(Float.valueOf(value) * 100.0F) / 100.0F);
                } catch (NumberFormatException e) {
                    Timber.e(e, "%s formatCalculationReturnValue", new Object[]{this.getClass().getCanonicalName()});
                }
            }

            return value;
        }
    }
}
