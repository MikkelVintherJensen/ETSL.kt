package abstract_syntax;

public final class StringExpr implements Expr {
    public final String value;

    public StringExpr(String value) {
        this.value = value;
    }
}