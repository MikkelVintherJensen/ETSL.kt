package syntactic_analysis

import interpretation.eval
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

    val ast = parser.getResult()
    val value = eval(ast)

    println(value)
}