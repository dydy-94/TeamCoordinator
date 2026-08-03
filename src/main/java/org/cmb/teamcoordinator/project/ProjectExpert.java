package org.cmb.teamcoordinator.project;

public class ProjectExpert {

    private String expertId;
    private boolean enabled;

    public ProjectExpert() {}

    public ProjectExpert(String expertId, boolean enabled) {
        this.expertId = expertId;
        this.enabled = enabled;
    }

    public String getExpertId() { return expertId; }
    public void setExpertId(String expertId) { this.expertId = expertId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
