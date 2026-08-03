package org.cmb.teamcoordinator.intent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CoordinatorDecision {

    @NotNull
    @JsonProperty("decision_type")
    private DecisionType decisionType;
    private String answer;
    private String question;
    @Valid
    @JsonProperty("task_intent")
    private TaskIntent taskIntent;
    @JsonProperty("analysis_id")
    private String analysisId;
    @JsonProperty("human_request_id")
    private String humanRequestId;

    public DecisionType getDecisionType() { return decisionType; }
    public void setDecisionType(DecisionType decisionType) { this.decisionType = decisionType; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public TaskIntent getTaskIntent() { return taskIntent; }
    public void setTaskIntent(TaskIntent taskIntent) { this.taskIntent = taskIntent; }
    public String getAnalysisId() { return analysisId; }
    public void setAnalysisId(String analysisId) { this.analysisId = analysisId; }
    public String getHumanRequestId() { return humanRequestId; }
    public void setHumanRequestId(String value) { this.humanRequestId = value; }
}
