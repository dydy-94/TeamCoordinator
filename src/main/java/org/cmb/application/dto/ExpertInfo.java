package org.cmb.application.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight expert descriptor returned by the /experts query API.
 * Mapped from {@code ExpertDescriptor} (the ExpertRegistry is the single
 * source of truth for expert definitions).
 */
public class ExpertInfo {

    private String id;
    private String name;
    private String description;
    private List<String> capabilities = new ArrayList<>();

    public ExpertInfo() {}

    public ExpertInfo(String id, String name, String description,
                      List<String> capabilities) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.capabilities = capabilities;
    }

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public List<String> getCapabilities() { return capabilities; }
    public void setCapabilities(List<String> v) { this.capabilities = v; }
}
