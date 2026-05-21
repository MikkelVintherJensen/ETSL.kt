package abstract_syntax;

public final class NumExpr implements Expr {
    public final double value;

    public NumExpr(double value) {
        this.value = value;
    }
}