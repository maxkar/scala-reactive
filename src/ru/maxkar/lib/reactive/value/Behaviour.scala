package ru.maxkar.lib.reactive.value

import ru.maxkar.lib.reactive.event.Event
import ru.maxkar.lib.reactive.wave.Participable


/**
 * Common trait for all behaviours. Each behaviour is
 * just a "state" at the current moment of the time. Behaviour
 * is somewhat similar to events. However where event have some
 * "neutral" state, behaviour do not have such state but have
 * some "current" state instead. That "current" state is not reset
 * after each wave.
 * <p>Behaviour users are interested in state changes. However,
 * flow/event API does not have this definition by itself. And
 * handling equality if behaviour deps can be bothersome, each
 * behaviour have a corresponding "change" event source. This
 * event is set to "true" on each wave where value of this
 * behaviour changes.
 * @param T type of the behaviour's value.
 */
trait Behaviour[+T] {
  /**
   * Change event source. This event is activated when value
   * of this behaviour is going to change during the wave.
   * <p> This event is generic change event. Concrete behaviours
   * may define additional event types. For example, list behaviour may
   * define a "list change" event which will be a more fine-grained
   * change description (it may include changed indices).
   */
  val change : Event[Boolean]



  /**
   * Returns current value of this behaviour.
   * During the propagation value is guaranteed to be updated
   * only after the corresponding event is fired/resolved.
   * @return current value of this behaviour.
   */
  def value() : T



  /**
   * Map function applicator.
   * @param mapper mapper function.
   */
  def :<[R](mapper : T ⇒ R)(implicit ctx : BindContext) : Behaviour[R] = {
    val res = new MapBehaviour(mapper, this, ctx)
    ctx.lifespan.onDispose(res.dispose)
    res
  }



  /**
   * Value-based submodel dispatch. Creates a new
   * submodel which lifetime is bound to lifetime
   * of <em>current</em> value of this behaviour.
   * When value changes, subcontext would be disposed and
   * new subcontext would be created allowing for a
   * completely new model.
   */
  def :/<[R](
        mapper : (T, BindContext) ⇒ R)(
        implicit ctx : BindContext)
      : Behaviour[R] = {
    var res = new SubmodelDispatch(mapper, this, ctx)
    ctx.lifespan.onDispose(res.dispose)
    res
  }



  /**
   * Monad-like submodel dispatch.
   */
  def :/<<[R](
        mapper : (T, BindContext) ⇒ Behaviour[R])(
        implicit ctx : BindContext)
      : Behaviour[R] =
    Behaviour.join(this :/< mapper)
}



/**
 * Behaivour black magic.
 */
object Behaviour {
  import scala.language.implicitConversions


  /** Default variable binding context. Binds values
   * with an infinite lifespan and no current wave.
   */
  val defaultBindContext : BindContext =
    new BindContext(Lifespan.forever, Participable.DefaultParticipable)



  /** Creates a new behaviour variable.
   * @param v initial value.
   */
  def variable[T](v : T) : Variable[T] = new Variable(v)



  /**
   * Convents value into behaviour constant.
   * @param v behaviour value.
   */
  def const[T](v : T) : Behaviour[T] = new Behaviour[T] {
    override def value() = v
    override val change = Event.constFalseEvent
  }


  /** Automatic function uplift. */
  implicit class MapFnUplift[S, D](val value : S ⇒ D) extends AnyVal {
    @inline
    def :> (src : Behaviour[S])(implicit ctx : BindContext) : Behaviour[D] =
      src.:<(value)(ctx)
  }



  /** More applicative ops. */
  implicit class AppUnUplift[S, D](val value : Behaviour[S ⇒ D]) extends AnyVal {
    def :> (src : Behaviour[S])(implicit ctx : BindContext) : Behaviour[D] = {
      val res = new ApplicativeBehaviour(value, src, ctx)
      ctx.lifespan.onDispose(res.dispose)
      res
    }

    def :> (src : S)(implicit ctx : BindContext) : Behaviour[D] =
      value.:<(fn ⇒ fn(src))(ctx)
  }



  /** Monad-like function application. */
  implicit class MonadLikeFnApp[S, D](val value : S ⇒ Behaviour[D]) extends AnyVal {
    def :>> (src : Behaviour[S])(implicit ctx : BindContext) : Behaviour[D] =
      join(value.:>(src)(ctx))(ctx)
  }



  /** Monadic ops! */
  implicit class MonadLikeUnApp[S, D](
        val value : Behaviour[S ⇒ Behaviour[D]])
      extends AnyVal {
    def :>> (src : Behaviour[S])(implicit ctx : BindContext) : Behaviour[D] =
      join(value.:>(src)(ctx))(ctx)


    def :>> (src : S)(implicit ctx : BindContext) : Behaviour[D] =
      join(value.:>(src)(ctx))(ctx)
  }



  /** Dispatch (lifecycle binding) operation on function. */
  implicit class DispatchFlat[S, D](
        val value : (S, BindContext) ⇒ D)
      extends AnyVal {
    @inline
    def :/> (src : Behaviour[S])(implicit ctx : BindContext) : Behaviour[D] =
      src :/< value
  }



  /** Dispatch (lifecycle binding) operation on monadic function. */
  implicit class DispatchMonadic[S, D](
        val value : (S, BindContext) ⇒ Behaviour[D])
      extends AnyVal {
    @inline
    def :/>> (src : Behaviour[S])(implicit ctx : BindContext) : Behaviour[D] =
      src :/<< value
  }



  /**
   * Joins nested behaviours into one simple behaviour.
   */
  def join[T](base : Behaviour[Behaviour[T]])(implicit ctx : BindContext) : Behaviour[T] = {
    val res = new Flatten(base, ctx)
    ctx.lifespan.onDispose(res.dispose)
    res
  }
}
