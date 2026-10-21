package com.ucamp.app.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// src/test/resources/eval/cases.json 형식 (reference/measurement.md 3번)
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvalCaseFile(Meta meta, List<EvalCase> cases) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(String version, boolean reviewed, List<String> reproCaseIds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvalCase(String caseId, List<String> tags, String difficulty, Input input, Expected expected,
                           String rationale) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Input(String respondentType, Map<String, Object> profile, List<String> conditions,
                        List<Medication> medications, Map<String, Object> answers, List<Intake> currentIntake) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Medication(String drugId, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Intake(String ingredient, BigDecimal amount, String unit) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Expected(List<String> mustInclude, List<String> mayInclude, List<String> mustExclude,
                           List<Dose> doses) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dose(String ingredient, BigDecimal amount, String unit) {
    }
}
