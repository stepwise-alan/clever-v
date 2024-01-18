import com.opencsv.CSVWriter
import de.fosd.typechef.parser.c.{ExternalDef, FunctionDef}

import java.io.{BufferedWriter, File, FileWriter, OutputStreamWriter}
import scala.annotation.tailrec

object StatsAnalyzer {
  private def isDefinedInFile(externalDef: ExternalDef, filepath: String): Boolean =
    externalDef.getFile.isDefined && File(externalDef.getFile.get.stripPrefix("file ")).equals(File(filepath))

  def main(args: Array[String]): Unit = {
    val parser = Parser()

    var numFile = 0
    var numFunction = 0

    val csvWriter = CSVWriter(OutputStreamWriter(System.out))

    csvWriter.writeNext(Array("File Path",
      "Function Name",
      "Number of Features",
      "Number of Opts",
      "Number of Choices",
      "Number of Inputs",
    ))

    for (filepath <- io.Source.stdin.getLines()) {
      try {
        val (translationUnit, featureModel, features) = parser(args :+ filepath)

        numFile += 1
        translationUnit.defs.foreach(d => {
          if (isDefinedInFile(d.entry, filepath))
            d.entry match {
              case functionDef: FunctionDef =>
                if (isDefinedInFile(functionDef, filepath)) {
                  numFunction += 1
                  //                val result = new VariabilitySearcher(featureModel)(d)
                  //                // if (result.features.nonEmpty) variationalFunctionCount += 1
                  //                val output: Array[String] = Array(
                  //                  filepath, functionDef.getName, result.features.size.toString,
                  //                  result.optCount.toString, result.choiceCount.toString,
                  //                  getInputAsString(functionDef.declarator),
                  //                  result.features.nonEmpty.toString)
                  //                println(output.mkString("", "; ", ""))
                  //                csvWriter.writeNext(output)
                  //                println(s"$filepath: ${functionDef.getName}: $result")
                }
              case _ =>
            }
        })
      } catch {
        case e: Throwable =>
          println(e.getMessage);
      }

    }
    csvWriter.close()
  }
}
