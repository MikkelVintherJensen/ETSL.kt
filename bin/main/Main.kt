import syntactic_analysis.Parser
import syntactic_analysis.Scanner
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        error("Usage: .\\gradlew.bat run --args=\"Examples/hello.etsl\"")
    }

    val fileName = args[0]
    val file = File(fileName)

    if (!file.exists()) {
        error("File not found: $fileName")
    }

    val scanner = Scanner(file.absolutePath)
    val parser = Parser(scanner)

    parser.Parse()

    if (parser.errors.count == 0) {
        println("Parse OK")
    } else {
        println("Parse failed with ${parser.errors.count} error(s)")
    }
}