package org.cmb.teamcoordinator.coordinator;

public enum ProjectEventType {
    /** Internal audit marker: message received (never sent to SSE clients). */
    MESSAGE_ACCEPTED_INTERNAL,
    /** Generic DB type for Coordinator-generated AgentEvents. */
    COORDINATOR_ANALYZING,
    /** Replay marker: agent events fetched from AgentCore on reconnect. */
    AGENT_RUN_MARKER,
}
