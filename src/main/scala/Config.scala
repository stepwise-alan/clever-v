import java.io.File

case class Config
(
  newFile: File = File(""),
  oldFile: File = File(""),
  out: File = File("."),
  seahorn: File = File("seahorn"),
  z3: File = File("z3"),
  function: String = "",
  typechefArgs: Seq[String] = Seq(),
)
