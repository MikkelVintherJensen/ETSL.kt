package interpretation;

import java.util.List;

public final class EventInstance {
    public final String name;
    public final List<Object> values;
    
    public EventInstance(String name, List<Object> values) {
        this.name = name;
        this.values = values;
    }
    
    @Override
    public String toString() {
        return name + values;
    }
}