import scala.util.parsing.combinator.JavaTokenParsers

sealed trait SExpr

case class SNumber(value: String) extends SExpr

case class SSymbol(value: String) extends SExpr

case class SString(value: String) extends SExpr

case class SList(sExprs: List[SExpr]) extends SExpr

object SExprParser extends JavaTokenParsers {
  private lazy val sexpr: Parser[SExpr] = atom | list

  private lazy val atom: Parser[SExpr] = stringLiteral ^^ SString.apply
    | floatingPointNumber ^^ SNumber.apply
    | "[^\\s\"()]+".r ^^ SSymbol.apply

  lazy val list: Parser[SExpr] = "(" ~> rep(sexpr) <~ ")" ^^ SList.apply

  def apply(in: String): SExprParser.ParseResult[SExpr] = parseAll(sexpr, in)
}