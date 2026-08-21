package org.cmb.application.domain;

import java.util.ArrayList;
import java.util.List;

public class ExpertDescriptor {

    private String expertId;
    private String displayName;
    private String description;
    private boolean enabled;
    private boolean available;
    private int concurrencyLimit;
    private List<String> capabilities = new ArrayList<>();

    public ExpertDescriptor() {
    }

    public ExpertDescriptor(String expertId, String displayName, List<String> capabilities) {
        this(expertId, displayName, capabilities, null);
    }

    public ExpertDescriptor(String expertId, String displayName,
                            List<String> capabilities, String description) {
        this.expertId = expertId;
        this.displayName = displayName;
        this.capabilities = capabilities;
        this.description = description;
        this.enabled = true;
        this.available = true;
        this.concurrencyLimit = 2;
    }

    public String getExpertId() {
        return expertId;
    }

    public void setExpertId(String expertId) {
        this.expertId = expertId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getConcurrencyLimit() {
        return concurrencyLimit;
    }

    public void setConcurrencyLimit(int concurrencyLimit) {
        this.concurrencyLimit = concurrencyLimit;
    }

    public List<String> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<String> capabilities) {
        this.capabilities = capabilities;
    }
}
