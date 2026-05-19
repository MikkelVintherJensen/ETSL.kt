package abstract_syntax;

public final class LetAction implements Action {
    public final Type type;
    public final String name;
    public final Expr value;
    public final Action body;
    public Action next;  // Changed from final
    
    public LetAction(Type type, String name, Expr value, Action body, Action next) {
        this.type = type;
        this.name = name;
        this.value = value;
        this.body = body;
        this.next = next;
    }
}