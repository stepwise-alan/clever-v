import de.fosd.typechef.parser.c.AST
import de.fosd.typechef.parser.c.PrettyPrinter.{print, printW}

import java.io.{BufferedWriter, FileWriter, Writer}
import java.nio.file.Path

object CodeGenerator {
  def apply(ast: AST, path: Path): Unit = {
    val writer = new BufferedWriter(new FileWriter(path.toFile))
    apply(ast, writer)
    writer.close()
  }
  
  def apply(ast: AST, writer: Writer): Unit = {
    printW(ast, writer)
  }
  
  def apply(ast: AST): String = {
    print(ast)
  }
}
