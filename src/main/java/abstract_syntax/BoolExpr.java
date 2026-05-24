package abstract_syntax;

public final class BoolExpr implements Expr {
    public final boolean value;

    public BoolExpr(boolean value) {
        this.value = value;
    }
}