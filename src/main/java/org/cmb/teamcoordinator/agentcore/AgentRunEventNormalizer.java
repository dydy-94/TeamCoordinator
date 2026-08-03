package org.cmb.teamcoordinator.agentcore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentRunEventNormalizer {

    public List<AgentRunEvent> normalize(List<AgentRunEvent> events) {
        Map<Long, AgentRunEvent> bySequence = new LinkedHashMap<>();
        for (AgentRunEvent event : events) {
            bySequence.putIfAbsent(event.getSequence(), event);
        }
        List<AgentRunEvent> normalized = new ArrayList<>(bySequence.values());
        normalized.sort(Comparator.comparingLong(AgentRunEvent::getSequence));
        return normalized;
    }
}
