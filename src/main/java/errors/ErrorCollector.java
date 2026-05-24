package errors;

import java.util.ArrayList;
import java.util.List;

public class ErrorCollector {
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private boolean hasErrors = false;
    private String sourceCode;
    private boolean hasSyntaxError = false;
    
    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }
    
    public void add(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
        if (diagnostic.getSeverity() == Diagnostic.Severity.ERROR) {
            hasErrors = true;
            if (diagnostic.getType().toString().startsWith("SYNTAX")) {
                hasSyntaxError = true;
            }
        }
        diagnostic.printWithContext(sourceCode);
    }
    
    public void addSyntaxError(String message, int line, int column, String hint) {
        Diagnostic diag = Diagnostic.builder()
            .type(ErrorType.SYNTAX_INVALID_STRUCTURE)
            .message(message)
            .location(new Diagnostic.SourceLocation(line, column, 0, ""))
            .hint(hint)
            .build();
        add(diag);
    }
    
    public void addSyntaxErrorMissingSemicolon(int line, int column) {
        Diagnostic diag = Diagnostic.builder()
            .type(ErrorType.SYNTAX_MISSING_SEMICOLON)
            .message("Missing semicolon at end of declaration")
            .location(new Diagnostic.SourceLocation(line, column, 0, ""))
            .hint("Add ';' at the end of the declaration")
            .build();
        add(diag);
    }
    
    public void addAll(List<Diagnostic> diags) {
        for (Diagnostic d : diags) {
            add(d);
        }
    }
    
    public boolean hasErrors() {
        return hasErrors;
    }
    
    public boolean hasSyntaxError() {
        return hasSyntaxError;
    }
    
    public boolean hasWarnings() {
        return diagnostics.stream().anyMatch(d -> d.getSeverity() == Diagnostic.Severity.WARNING);
    }
    
    public List<Diagnostic> getDiagnostics() {
        return new ArrayList<>(diagnostics);
    }
    
    public List<Diagnostic> getErrors() {
        return diagnostics.stream()
            .filter(d -> d.getSeverity() == Diagnostic.Severity.ERROR)
            .toList();
    }
    
    public void clear() {
        diagnostics.clear();
        hasErrors = false;
        hasSyntaxError = false;
    }
    
    public int getErrorCount() {
        return getErrors().size();
    }
}