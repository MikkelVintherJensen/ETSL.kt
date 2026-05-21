package syntactic_analysis;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import abstract_syntax.Program;
import interpretation.Evaluator;
import interpretation.EventInstance;
import interpretation.TypeChecker;

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

        Scanner scanner = new Scanner(file.getAbsolutePath());
        Parser parser = new Parser(scanner);
        parser.Parse();

        Program program = parser.getProgram();

        if (parser.errors.count > 0) {
            System.err.println("Parsing failed with " + parser.errors.count + " errors");
            System.exit(1);
        }

        // Type check
        TypeChecker typeChecker = new TypeChecker();
        if (!typeChecker.check(program)) {
            System.err.println("Type checking failed");
            System.exit(1);
        }

        // Parse event instances from command line
        List<EventInstance> initialEvents = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String eventSpec = args[i];
            String[] parts = eventSpec.split(":", 2);
            if (parts.length != 2) {
                System.err.println("Invalid event spec: " + eventSpec);
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
                    } catch (NumberFormatException e) {
                        values.add(vs);
                    }
                }
            }
            initialEvents.add(new EventInstance(eventName, values));
        }

        // Evaluate
        Evaluator evaluator = new Evaluator();
        Object result = evaluator.evaluate(program, initialEvents);

        System.out.println("Result: " + result);
    }
}