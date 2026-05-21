package abstract_syntax;

public final class SkipAction implements Action {
    public Action next;
    
    public SkipAction(Action next) {
        this.next = next;
    }
}