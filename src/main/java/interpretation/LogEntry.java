package interpretation;

import java.util.Map;

public final class LogEntry {
    public final String agentName;
    public final Map<String, Object> data;
    
    public LogEntry(String agentName, Map<String, Object> data) {
        this.agentName = agentName;
        this.data = data;
    }
    
    @Override
    public String toString() {
        return agentName + ": " + data;
    }
}