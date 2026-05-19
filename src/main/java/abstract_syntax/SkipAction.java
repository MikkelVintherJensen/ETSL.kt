package abstract_syntax;

public final class SkipAction implements Action {
    public Action next;  // Changed from final
    
    public SkipAction(Action next) {
        this.next = next;
    }
}