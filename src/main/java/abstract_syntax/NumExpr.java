package abstract_syntax;

public final class NumExpr implements Expr {
    public final int value;

    public NumExpr(int value) {
        this.value = value;
    }
}