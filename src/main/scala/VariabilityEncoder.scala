import de.fosd.typechef.conditional.{Choice, Conditional, One, Opt}
import de.fosd.typechef.featureexpr.sat.*
import de.fosd.typechef.featureexpr.{FeatureExpr, FeatureExprFactory, FeatureModel}
import de.fosd.typechef.parser.c.*

class VariabilityEncoder(val featureModel: FeatureModel, val features: Set[String]) {
  private val definedFunctions: scala.collection.mutable.Set[String] = scala.collection.mutable.Set()

  private def convertClausesToExpr(clauses: Set[SATFeatureExpr], operator: String): Expr = {
    if (clauses.isEmpty) {
      if (operator == "&&") {
        Constant("1")
      } else if (operator == "||") {
        Constant("0")
      } else {
        assert(false)
      }
    } else if (clauses.size == 1) {
      convertFeatureExpr(clauses.head)
    } else {
      NAryExpr(convertFeatureExpr(clauses.head), clauses.tail.map(c =>
        TrueOpt(NArySubExpr(operator, convertFeatureExpr(c)))
      ).toList)
    }
  }

  private def convertFeatureExpr(featureExpr: FeatureExpr): Expr = {
    featureExpr match {
      case expr: SATFeatureExpr => expr match {
        case And(clauses) => convertClausesToExpr(clauses, "&&")
        case Or(clauses) => convertClausesToExpr(clauses, "||")
        case Not(clause) => UnaryOpExpr("!", convertFeatureExpr(clause))
        case d: DefinedExternal => Id(d.feature)
        case _ => ???
      }
      case _ => assert(false)
    }
  }

  private def convertStringLitName(name: List[Opt[String]], acc: List[Opt[String]] = List()): Expr = {
    if (name.isEmpty) {
      StringLit(acc)
    } else if (name.head.condition == FeatureExprFactory.True) {
      convertStringLitName(name.tail, acc :+ name.head)
    } else {
      ConditionalExpr(
        convertFeatureExpr(name.head.condition),
        Some(convertStringLitName(name.tail, acc :+ TrueOpt(name.head.entry))),
        convertStringLitName(name.tail, acc)
      )
    }
  }

  private def convertExpr(expr: Expr): Expr = {
    expr match {
      case expr: PrimaryExpr =>
        expr match {
          case id: Id => id
          case constant: Constant => constant
          case StringLit(name) => convertStringLitName(name)
          case BuiltinOffsetof(typeName, offsetofMemberDesignator) => expr
          case BuiltinTypesCompatible(typeName1, typeName2) => expr
          case BuiltinVaArgs(expr, typeName) => expr
          case CompoundStatementExpr(compoundStatement) =>
            CompoundStatementExpr(convertCompoundStatement(compoundStatement))
        }
      case PostfixExpr(p, s) =>
        PostfixExpr(convertExpr(p), s match {
          case SimplePostfixSuffix(t) => s
          case PointerPostfixSuffix(kind, id) => s
          case FunctionCall(params) =>
            p match {
              case Id(name) if definedFunctions contains name =>
                FunctionCall(ExprList(params.exprs.map(optExpr => TrueOpt(convertExpr(optExpr.entry)))
                  ++ features.map(f => TrueOpt(Id(f)))))
              case _ =>
                FunctionCall(ExprList(params.exprs.map(optExpr => TrueOpt(convertExpr(optExpr.entry)))))
            }
          case ArrayAccess(expr) => ArrayAccess(convertExpr(expr))
        })
      case UnaryExpr(kind, e) => UnaryExpr(kind, convertExpr(e))
      case SizeOfExprT(typeName) => expr
      case SizeOfExprU(expr) => SizeOfExprU(convertExpr(expr))
      case CastExpr(typeName, expr) => CastExpr(typeName, convertExpr(expr))
      case PointerDerefExpr(castExpr) => PointerDerefExpr(convertExpr(castExpr))
      case PointerCreationExpr(castExpr) => PointerCreationExpr(convertExpr(castExpr))
      case UnaryOpExpr(kind, castExpr) => UnaryOpExpr(kind, convertExpr(castExpr))
      case NAryExpr(e, others) =>
        NAryExpr(convertExpr(e), others.map(f => TrueOpt(NArySubExpr(f.entry.op, convertExpr(f.entry.e)))))
      case ConditionalExpr(condition, thenExpr, elseExpr) =>
        ConditionalExpr(convertExpr(condition), thenExpr.map(e => convertExpr(e)), convertExpr(elseExpr))
      case AssignExpr(target, operation, source) => AssignExpr(convertExpr(target), operation, convertExpr(source))
      case ExprList(exprs) => ExprList(exprs.map(f => TrueOpt(f.entry)))
      case LcurlyInitializer(inits) =>
        LcurlyInitializer(inits.map(f => TrueOpt(convertInitializer(f.entry))))
      case AlignOfExprT(typeName) => expr
      case AlignOfExprU(expr) => AlignOfExprU(convertExpr(expr))
      case GnuAsmExpr(isVolatile, isGoto, expr, stuff) => expr
      case RangeExpr(from, to) => RangeExpr(convertExpr(from), convertExpr(to))
    }
  }

  private def convertInitializer(initializer: Initializer): Initializer = {
    Initializer(initializer.initializerElementLabel.map(convertInitializerElementLabel), convertExpr(initializer.expr))
  }

  private def convertInitializerElementLabel(initializerElementLabel: InitializerElementLabel): InitializerElementLabel = {
    initializerElementLabel match {
      case InitializerArrayDesignator(expr) => InitializerArrayDesignator(convertExpr(expr))
      case InitializerDesignatorC(id) => initializerElementLabel
      case InitializerDesignatorD(id) => initializerElementLabel
      case InitializerAssigment(designators) =>
        InitializerAssigment(designators.map(f => TrueOpt(convertInitializerElementLabel(f.entry))))
    }
  }

  private def convertConditionalStatement(conditionalStatement: Conditional[Statement]): One[Statement] = {
    conditionalStatement match {
      case Choice(condition, thenBranch, elseBranch) =>
        One(IfStatement(
          One(convertFeatureExpr(condition)),
          convertConditionalStatement(thenBranch),
          List(),
          Some(convertConditionalStatement(elseBranch))
        ))
      case One(value) =>
        value match {
          case statement: CompoundStatement => One(convertCompoundStatement(statement))
          case _ => One(convertCompoundStatement(CompoundStatement(List(TrueOpt(value)))))
        }
    }
  }

  private def convertConditionalExpr(conditionalExpr: Conditional[Expr]): Expr = {
    conditionalExpr match {
      case Choice(condition, thenBranch, elseBranch) =>
        if (condition == FeatureExprFactory.True) {
          convertConditionalExpr(thenBranch)
        } else {
          ConditionalExpr(
            convertFeatureExpr(condition),
            Some(convertConditionalExpr(thenBranch)),
            convertConditionalExpr(elseBranch)
          )
        }
      case One(value) => convertExpr(value)
    }
  }

  private def convertCompoundStatement(compoundStatement: CompoundStatement): CompoundStatement = {
    val (optStatements, optDeclarationStatements) = compoundStatement.innerStatements
      .foldLeft(List[Opt[Statement]](), List[Opt[DeclarationStatement]]())((acc, optStatement) => {
        val (optStatements, optDeclarationStatements) = optStatement.entry match {
          case statement: CompoundStatement =>
            val compoundStatement = convertCompoundStatement(statement)
            (List(TrueOpt(compoundStatement)), List())
          case statement: EmptyStatement => (List(TrueOpt(statement)), List())
          case ExprStatement(expr) => (List(TrueOpt(ExprStatement(convertExpr(expr)))), List())
          case WhileStatement(expr, s) =>
            (List(TrueOpt(WhileStatement(convertExpr(expr), convertConditionalStatement(s)))), List())
          case DoStatement(expr, s) =>
            (List(TrueOpt(DoStatement(convertExpr(expr), convertConditionalStatement(s)))), List())
          case ForStatement(expr1, expr2, expr3, s) =>
            (List(TrueOpt(ForStatement(expr1.map(convertExpr), expr2.map(convertExpr), expr3.map(convertExpr),
              convertConditionalStatement(s)))), List())
          case GotoStatement(target) => (List(TrueOpt(GotoStatement(convertExpr(target)))), List())
          case statement: ContinueStatement => (List(TrueOpt(statement)), List())
          case statement: BreakStatement => (List(TrueOpt(statement)), List())
          case ReturnStatement(expr) => (List(TrueOpt(ReturnStatement(expr.map(convertExpr)))), List())
          case statement: LabelStatement => (List(TrueOpt(statement)), List())
          case CaseStatement(c) => (List(TrueOpt(CaseStatement(convertExpr(c)))), List())
          case statement: DefaultStatement => (List(TrueOpt(statement)), List())
          case IfStatement(condition, thenBranch, elifs, elseBranch) =>
            (
              List(TrueOpt(IfStatement(
                One(convertConditionalExpr(condition)),
                convertConditionalStatement(thenBranch),
                elifs.map {
                  case Opt(condition, ElifStatement(condition1, thenBranch)) =>
                    TrueOpt(ElifStatement(
                      One(NAryExpr(
                        convertFeatureExpr(condition),
                        List(TrueOpt(NArySubExpr("&&", convertConditionalExpr(condition1))))
                      )),
                      convertConditionalStatement(thenBranch))
                    )
                },
                elseBranch.map(convertConditionalStatement)
              ))),
              List()
            )
          case SwitchStatement(expr, s) =>
            (List(TrueOpt(SwitchStatement(convertExpr(expr), convertConditionalStatement(s)))), List())
          case declaration: CompoundDeclaration => declaration match {
            case DeclarationStatement(decl) =>
              if (decl.declSpecs.exists(_.entry.isInstanceOf[TypedefSpecifier])) {
                (List(TrueOpt(declaration)), List())
              } else {
                decl.init.foldLeft(
                  List[Opt[Statement]](), List[Opt[DeclarationStatement]]()
                )((acc, optInitDeclarator) => (
                  optInitDeclarator.entry.getExpr.map(
                    AssignExpr(Id(optInitDeclarator.entry.getName), "=", _)
                  ) match {
                    case Some(a) =>
                      acc._1 :+ TrueOpt(if (optInitDeclarator.condition == FeatureExprFactory.True) {
                        ExprStatement(convertExpr(a))
                      } else {
                        IfStatement(
                          One(convertFeatureExpr(optInitDeclarator.condition)),
                          One(ExprStatement(convertExpr(a))), List(), None
                        )
                      })
                    case None => acc._1
                  },
                  acc._2 :+ TrueOpt(DeclarationStatement(Declaration(decl.declSpecs, List(TrueOpt(InitDeclaratorI(
                    optInitDeclarator.entry.declarator, optInitDeclarator.entry.attributes, None
                  ))))))
                ))
              }
            case declaration: LocalLabelDeclaration => (List(TrueOpt(declaration)), List())
            case functionDef: NestedFunctionDef => (List(TrueOpt(functionDef)), List())
          }
        }
        if (optStatement.condition == FeatureExprFactory.True) {
          (acc._1 ++ optStatements, acc._2 ++ optDeclarationStatements)
        } else {
          (
            acc._1 :+ TrueOpt(IfStatement(
              One(convertFeatureExpr(optStatement.condition)),
              One(CompoundStatement(optStatements)), List(), None
            )),
            acc._2 ++ optDeclarationStatements
          )
        }
      }
      )
    CompoundStatement(optDeclarationStatements ++ optStatements)
  }

  private def convertFunctionDeclarator(declarator: Declarator): Declarator = {
    declarator match {
      case AtomicNamedDeclarator(pointers, id, extensions) =>
        AtomicNamedDeclarator(pointers, id, extensions.map(optDeclaratorExtension =>
          TrueOpt(optDeclaratorExtension.entry match {
            case extension: DeclaratorAbstrExtension => extension match {
              case DeclParameterDeclList(parameterDecls) => DeclParameterDeclList(parameterDecls ++ features.map(s =>
                TrueOpt(ParameterDeclarationD(
                  List(TrueOpt(OtherPrimitiveTypeSpecifier("_Bool"))),
                  AtomicNamedDeclarator(List(), Id(s), List()),
                  List()
                ))
              ))
              case _: DeclArrayAccess => ???
            }
            case DeclIdentifierList(idList) => DeclIdentifierList(idList ++ features.map(s => TrueOpt(Id(s))))
          })
        ))
      case _: NestedNamedDeclarator => ???
    }
  }

  def apply(translationUnit: TranslationUnit): TranslationUnit = {
    translationUnit.defs.foreach(e => e.entry match {
      case _: Declaration =>
      case _: AsmExpr =>
      case functionDef: FunctionDef => definedFunctions += functionDef.getName
      case _: EmptyExternalDef =>
      case _: TypelessDeclaration =>
      case _: Pragma =>
    })
    TranslationUnit(translationUnit.defs.map(e => e.entry match {
      case _: Declaration => TrueOpt(e.entry)
      case _: AsmExpr => TrueOpt(e.entry)
      case FunctionDef(specifiers, declarator, oldStyleParameters, stmt) =>
        TrueOpt(FunctionDef(
          specifiers,
          convertFunctionDeclarator(declarator),
          if (oldStyleParameters.isEmpty) {
            oldStyleParameters
          } else {
            oldStyleParameters ++ features.map(s => TrueOpt(Declaration(
              List(TrueOpt(OtherPrimitiveTypeSpecifier("_Bool"))),
              List(TrueOpt(InitDeclaratorI(AtomicNamedDeclarator(List(), Id(s), List()), List(), None)))
            )))
          },
          convertCompoundStatement(stmt)
        ))
      case _: EmptyExternalDef => TrueOpt(e.entry)
      case _: TypelessDeclaration => TrueOpt(e.entry)
      case _: Pragma => TrueOpt(e.entry)
    }))
  }
}

object VariabilityEncoder {
  def main(args: Array[String]): Unit = {
    val parser = Parser()
    val (translationUnit, featureModel, features) = parser(Array("examples/coreutils.ls.6b01b71e/new.c"))
    println("-------")
    val metaProduct = VariabilityEncoder(featureModel, features)(translationUnit)
  }
}
