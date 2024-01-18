import de.fosd.typechef.ErrorXML
import de.fosd.typechef.Frontend.{lex, prepareAST}
import de.fosd.typechef.featureexpr.FeatureModel
import de.fosd.typechef.options.{FrontendOptions, FrontendOptionsWithConfigFiles, Options}
import de.fosd.typechef.parser.c.{CParser, ParserMain, TranslationUnit}

class Parser {
  private val maxOptionIdField = {
    val field = classOf[Options].getDeclaredField("maxOptionId")
    field.setAccessible(true)
    field
  }

  private val maxOptionId = {
    new FrontendOptionsWithConfigFiles()
    maxOptionIdField.get(null)
  }

  private def resetMaxOptionId(): Unit = maxOptionIdField.set(null, maxOptionId)

  def apply(args: Array[String], parserResults: Boolean = false, parserStatistics: Boolean = false,
            isPrintingLexingSuccess: Boolean = false): (TranslationUnit, FeatureModel, Set[String]) = {
    val opt = new FrontendOptionsWithConfigFiles {
      override def isPrintLexingSuccess: Boolean = isPrintingLexingSuccess
    }
    resetMaxOptionId()
    opt.parseOptions(args)
    opt.parserResults = parserResults
    opt.parserStatistics = parserStatistics

    val errorXML = new ErrorXML(opt.getErrorXMLFile)
    opt.setRenderParserError(errorXML.renderParserError)

    val smallFM = opt.getSmallFeatureModel.and(opt.getLocalFeatureModel).and(opt.getFilePresenceCondition)
    opt.setSmallFeatureModel(smallFM)

    val fullFM = opt.getFullFeatureModel.and(opt.getLocalFeatureModel).and(opt.getFilePresenceCondition)
    opt.setFullFeatureModel(fullFM)

    val in = lex(opt)
    val parserMain = new ParserMain(new CParser(smallFM))
    (prepareAST(parserMain.parserMain(in, opt, fullFM)), fullFM, parserMain.getDistinctFeatures(in.tokens))
  }
}
