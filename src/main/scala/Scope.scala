import de.fosd.typechef.featureexpr.{FeatureExpr, FeatureModel}

class Scope(private val featureModel: FeatureModel, private val m: Map[String, List[(String, FeatureExpr)]] = Map()) {
  def register(id: String, condition: FeatureExpr): Scope = {
    val conditions = m.getOrElse(id, Nil)
    if (conditions.exists(_._2.equivalentTo(condition, featureModel)))
      this
    else
      Scope(featureModel, m + (id -> (conditions :+ (if (conditions.isEmpty) id else id + conditions.size, condition))))
  }

  def lookup(id: String, condition: FeatureExpr): List[(String, FeatureExpr)] = {
    m.getOrElse(id, Nil).map((i, c) => (i, c and condition)).filter(_._2.isSatisfiable(featureModel))
  }
}
