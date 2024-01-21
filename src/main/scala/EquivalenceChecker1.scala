import de.fosd.typechef.featureexpr.FeatureExprFactory
import scopt.OParser

import java.io.{ByteArrayOutputStream, File, PrintStream}

object EquivalenceChecker1 {
  private val out = System.out
  
  private def time[R](name: String)(block: => R): R = {
    val t0 = System.nanoTime()
    val result = block
    val t1 = System.nanoTime()
    out.println(s"${"%-50s".format(name)} Elapsed time: ${"%10f".format((t1 - t0) / 1.0e9)} s")
    result
  }

  def main(args: Array[String]): Unit = {
    val parser = Parser()

    val builder = OParser.builder[Config]
    OParser.parse({
      import builder._
      scopt.OParser.sequence(
        programName("clever-veq1"),
        head("CLEVER-V Variability-Aware Equivalence Checker 1"),
        opt[File]("new")
          .required()
          .valueName("<file>")
          .action((x, c) => c.copy(newFile = x))
          .text("new C source file path for equivalence checking"),
        opt[File]("old")
          .required()
          .valueName("<file>")
          .action((x, c) => c.copy(oldFile = x))
          .text("old C source file path for equivalence checking"),
        opt[String]("function")
          .required()
          .valueName("<function>")
          .action((x, c) => c.copy(function = x)),
        opt[File]('o', "out")
          .valueName("<file>")
          .action((x, c) => c.copy(out = x))
          .text("output path"),
        opt[File]("sea")
          .valueName("<file>")
          .action((x, c) => c.copy(seahorn = x))
          .text("path to SeaHorn executable"),
        opt[File]("z3")
          .valueName("<file>")
          .action((x, c) => c.copy(z3 = x))
          .text("path to z3 executable"),
        opt[Unit]('q', "quiet")
          .action((_, c) => c.copy(quiet = true))
          .text("quiet"),
        opt[Seq[String]]("typechef-args")
          .valueName("<arg1>,<arg2>,...")
          .action((x, c) => c.copy(typechefArgs = x))
          .text("arguments to be forwarded to TypeChef"),
        help('h', "help").text("print this usage text"),
      )
    }, args, Config()) match {
      case Some(config) =>
        if (config.quiet) {
          System.setOut(new PrintStream(new ByteArrayOutputStream))
          System.setErr(new PrintStream(new ByteArrayOutputStream))
        }
        time("Total") {
          config.out.mkdirs()
          FeatureExprFactory.setDefault(FeatureExprFactory.sat)
          val (oldTranslationUnit, oldFeatureModel, oldFeatures) = time("Parse Old Translation Unit") {
            parser(config.typechefArgs.toArray :+ config.newFile.getPath, parserResults = true)
          }

          val (newTranslationUnit, newFeatureModel, newFeatures) = time("Parse New Translation Unit") {
            parser(config.typechefArgs.toArray :+ config.oldFile.getPath, parserResults = true)
          }

          val features = newFeatures ++ oldFeatures
          val featureModel = newFeatureModel

          val oldMetaProduct = time("Variability Encode Old Translation Unit") {
            VariabilityEncoder(oldFeatureModel, oldFeatures)(oldTranslationUnit)
          }
          val newMetaProduct = time("Variability Encode New Translation Unit") {
            VariabilityEncoder(newFeatureModel, newFeatures)(newTranslationUnit)
          }
          val selfComposition = time("Self Compose") {
            SelfComposer(oldMetaProduct, newMetaProduct,
              config.function, config.out, config.typechefArgs.toArray)
          }
          time("Verify") {
            Verifier1(config.seahorn.toPath, config.z3.toPath)(selfComposition, features)
          }
        }
      case _ =>
    }
  }
}
