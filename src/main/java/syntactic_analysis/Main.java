package syntactic_analysis;

import abstract_syntax.Program;
import interpretation.TypeChecker;
import interpretation.Evaluator;
import interpretation.EventInstance;
import errors.ErrorCollector;
import errors.Diagnostic;
import errors.ErrorType;
import static errors.Diagnostic.Severity;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: run --args=\"<file.etsl> [event:arg1,arg2...]\"");
            System.err.println("Example: run --args=\"Examples/test.etsl PriceUpdate:42\"");
            System.exit(1);
        }

        String fileName = args[0];
        File file = new File(fileName);

        if (!file.exists()) {
            System.err.println("File not found: " + fileName);
            System.exit(1);
        }

        String sourceCode = "";
        try {
            sourceCode = Files.readString(Path.of(fileName));
        } catch (IOException e) {
            System.err.println("Could not read file: " + e.getMessage());
            System.exit(1);
        }

        ErrorCollector errorCollector = new ErrorCollector();
        errorCollector.setSourceCode(sourceCode);

        Scanner scanner = new Scanner(file.getAbsolutePath());
        Parser parser = new Parser(scanner);
        parser.setErrorCollector(errorCollector);
        parser.Parse();

        Program program = parser.getProgram();

        if (parser.errors.count > 0 || errorCollector.hasErrors()) {
            System.err.println("Parsing failed with " + parser.errors.count + " syntax errors");
            System.err.println("Total diagnostics: " + errorCollector.getErrorCount());
            System.exit(1);
        }

        TypeChecker typeChecker = new TypeChecker(errorCollector);
        if (!typeChecker.check(program)) {
            System.err.println("Type checking failed with " + errorCollector.getErrorCount() + " errors");
            System.exit(1);
        }

        List<EventInstance> initialEvents = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String eventSpec = args[i];
            String[] parts = eventSpec.split(":", 2);
            if (parts.length != 2) {
                Diagnostic diag = Diagnostic.builder()
                    .type(ErrorType.RUNTIME_TYPE_ERROR)
                    .message("Invalid event specification: " + eventSpec)
                    .severity(Severity.ERROR)
                    .hint("Use format: eventName:value1,value2,...")
                    .build();
                errorCollector.add(diag);
                continue;
            }
            String eventName = parts[0];
            String[] valueStrs = parts[1].split(",");
            List<Object> values = new ArrayList<>();
            for (String vs : valueStrs) {
                vs = vs.trim();
                try {
                    values.add(Integer.parseInt(vs));
                } catch (NumberFormatException e1) {
                    try {
                        values.add(Double.parseDouble(vs));
                    } catch (NumberFormatException e2) {
                        values.add(vs);
                    }
                }
            }
            initialEvents.add(new EventInstance(eventName, values));
        }

        if (errorCollector.hasErrors()) {
            System.exit(1);
        }

        Evaluator evaluator = new Evaluator(errorCollector);
        try {
            Object result = evaluator.evaluate(program, initialEvents);
            System.out.println("Result: " + result);
        } catch (RuntimeException e) {
            Diagnostic diag = Diagnostic.builder()
                .type(ErrorType.RUNTIME_TYPE_ERROR)
                .message(e.getMessage())
                .severity(Severity.ERROR)
                .build();
            errorCollector.add(diag);
            System.exit(1);
        }
    }
}