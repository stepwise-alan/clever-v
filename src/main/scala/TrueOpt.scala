import de.fosd.typechef.conditional.Opt
import de.fosd.typechef.featureexpr.FeatureExprFactory

object TrueOpt {
  def apply[T](opt: Opt[T]): Opt[T] = {
    Opt(FeatureExprFactory.True, opt.entry)
  }

  def apply[T](t: T): Opt[T] = {
    Opt(FeatureExprFactory.True, t)
  }
}
