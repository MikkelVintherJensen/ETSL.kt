package syntactic_analysis;

import abstract_syntax.Program;
import interpretation.TypeChecker;
import interpretation.Evaluator;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: run --args=\"Examples/hello.etsl\"");
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

        // Evaluate
        Evaluator evaluator = new Evaluator();
        Object result = evaluator.evaluate(program);

        System.out.println("Result: " + result);
    }
}