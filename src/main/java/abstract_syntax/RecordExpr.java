package abstract_syntax;

import java.util.LinkedHashMap;

public final class RecordExpr implements Expr {
    public final LinkedHashMap<String, Expr> fields;

    public RecordExpr(LinkedHashMap<String, Expr> fields) {
        this.fields = fields;
    }
}