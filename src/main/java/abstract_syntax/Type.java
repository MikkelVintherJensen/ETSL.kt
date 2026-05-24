package abstract_syntax;

public enum Type {
    NUM,
    BOOL,
    STRING;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}