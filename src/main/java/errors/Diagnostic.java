package errors;

import java.util.Optional;

public class Diagnostic {
    private final ErrorType type;
    private final String code;
    private final String message;
    private final SourceLocation location;
    private final Severity severity;
    private final Optional<String> hint;
    
    public enum Severity {
        ERROR, WARNING, INFO
    }
    
    public static class SourceLocation {
        public final int line;
        public final int column;
        public final int offset;
        public final String source;
        
        public SourceLocation(int line, int column, int offset, String source) {
            this.line = line;
            this.column = column;
            this.offset = offset;
            this.source = source;
        }
        
        @Override
        public String toString() {
            return line + ":" + column;
        }
    }
    
    private Diagnostic(Builder builder) {
        this.type = builder.type;
        this.code = builder.code;
        this.message = builder.message;
        this.location = builder.location;
        this.severity = builder.severity;
        this.hint = Optional.ofNullable(builder.hint);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private ErrorType type;
        private String code;
        private String message;
        private SourceLocation location;
        private Severity severity = Severity.ERROR;
        private String hint;
        
        public Builder type(ErrorType type) {
            this.type = type;
            this.code = type.name();
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder location(SourceLocation location) {
            this.location = location;
            return this;
        }
        
        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }
        
        public Builder hint(String hint) {
            this.hint = hint;
            return this;
        }
        
        public Diagnostic build() {
            return new Diagnostic(this);
        }
    }
    
    public ErrorType getType() {
        return type;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public SourceLocation getLocation() {
        return location;
    }
    
    public Severity getSeverity() {
        return severity;
    }
    
    public Optional<String> getHint() {
        return hint;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(severity).append("] ");
        sb.append(code);
        if (location != null) {
            sb.append(" at ").append(location);
        }
        sb.append(": ").append(message);
        if (hint.isPresent()) {
            sb.append("\n  Hint: ").append(hint.get());
        }
        return sb.toString();
    }
    
    public void printWithContext(String sourceCode) {
        System.err.println(this);
        if (location != null && sourceCode != null && location.line > 0) {
            String[] lines = sourceCode.split("\n");
            if (location.line - 1 < lines.length) {
                String errorLine = lines[location.line - 1];
                System.err.println("  " + errorLine);
                System.err.print("  ");
                for (int i = 0; i < location.column - 1 && i < errorLine.length(); i++) {
                    System.err.print(errorLine.charAt(i) == '\t' ? "\t" : " ");
                }
                System.err.println("^");
            }
        }
    }
}