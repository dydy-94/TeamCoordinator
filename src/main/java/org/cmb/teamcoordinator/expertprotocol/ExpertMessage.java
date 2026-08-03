package org.cmb.teamcoordinator.expertprotocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.PositiveOrZero;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "message_type",
        visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ExpertTaskRequest.class, name = "EXPERT_TASK_REQUEST"),
    @JsonSubTypes.Type(value = ExpertTaskAccepted.class, name = "EXPERT_TASK_ACCEPTED"),
    @JsonSubTypes.Type(value = ExpertTaskProgress.class, name = "EXPERT_TASK_PROGRESS"),
    @JsonSubTypes.Type(value = ExpertTaskResult.class, name = "EXPERT_TASK_RESULT"),
    @JsonSubTypes.Type(value = ExpertTaskFailed.class, name = "EXPERT_TASK_FAILED"),
    @JsonSubTypes.Type(value = ExpertTaskCancel.class, name = "EXPERT_TASK_CANCEL"),
    @JsonSubTypes.Type(value = ExpertHumanInputRequired.class, name = "EXPERT_HUMAN_INPUT_REQUIRED"),
    @JsonSubTypes.Type(value = ExpertTaskResume.class, name = "EXPERT_TASK_RESUME")
})
public abstract class ExpertMessage {

    @NotBlank
    @JsonProperty("message_type")
    private String messageType;
    @NotBlank
    @JsonProperty("request_id")
    private String requestId;
    @JsonProperty("event_id")
    private String eventId;
    @PositiveOrZero
    private long sequence;

    protected ExpertMessage(String messageType) {
        this.messageType = messageType;
    }

    protected ExpertMessage() {
    }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public long getSequence() { return sequence; }
    public void setSequence(long sequence) { this.sequence = sequence; }
}
