package abstract_syntax;

public final class FieldAccessExpr implements Expr {
    public final Expr record;
    public final String fieldName;

    public FieldAccessExpr(Expr record, String fieldName) {
        this.record = record;
        this.fieldName = fieldName;
    }
}