package org.cmb.teamcoordinator.coordinator;

import javax.validation.constraints.Size;

public class CreateConversationTaskRequest {

    @Size(max = 128)
    private String title;

    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
}
