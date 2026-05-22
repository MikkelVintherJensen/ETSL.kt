package abstract_syntax;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class Type {
    public static final Type NUM = new Type("NUM", null, null);
    public static final Type BOOL = new Type("BOOL", null, null);
    public static final Type STRING = new Type("STRING", null, null);

    public final String name;
    public final Type elementType;
    public final LinkedHashMap<String, Type> fields;

    private Type(String name, Type elementType, LinkedHashMap<String, Type> fields) {
        this.name = name;
        this.elementType = elementType;
        this.fields = fields;
    }

    public static Type list(Type elementType) {
        return new Type("LIST", elementType, null);
    }

    public static Type record(LinkedHashMap<String, Type> fields) {
        return new Type("RECORD", null, fields);
    }

    public boolean isList() {
        return elementType != null;
    }

    public boolean isRecord() {
        return fields != null;
    }

    public Type fieldType(String fieldName) {
        if (fields == null) {
            return null;
        }

        return fields.get(fieldName);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Type other)) {
            return false;
        }

        if (!name.equals(other.name)) {
            return false;
        }

        if (!Objects.equals(elementType, other.elementType)) {
            return false;
        }

        return Objects.equals(fields, other.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, elementType, fields);
    }

    @Override
    public String toString() {
        if (isList()) {
            return "list<" + elementType + ">";
        }

        if (isRecord()) {
            StringBuilder sb = new StringBuilder();
            sb.append("record<");

            boolean first = true;
            for (Map.Entry<String, Type> entry : fields.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }

                sb.append(entry.getValue()).append(" ").append(entry.getKey());
                first = false;
            }

            sb.append(">");
            return sb.toString();
        }

        return name.toLowerCase();
    }
}