package ru.maxkar.lib.reactive.value

import ru.maxkar.lib.reactive.event.Event


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
  def :<[R](mapper : T ⇒ R)(implicit lifespan : Lifespan) : Behaviour[R] = {
    val res = new MapBehaviour(mapper, this)
    lifespan.onDispose(res.dispose)
    res
  }
}



/**
 * Behaivour black magic.
 */
object Behaviour {
  import scala.language.implicitConversions


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
    def :> (src : Behaviour[S])(implicit lifespan : Lifespan) : Behaviour[D] =
      src.:<(value)(lifespan)
  }



  /** More applicative ops. */
  implicit class AppUnUplift[S, D](val value : Behaviour[S ⇒ D]) extends AnyVal {
    def :> (src : Behaviour[S])(implicit lifespan : Lifespan) : Behaviour[D] = {
      val res = new ApplicativeBehaviour(value, src)
      lifespan.onDispose(res.dispose)
      res
    }

    def :> (src : S)(implicit lifespan : Lifespan) : Behaviour[D] =
      value.:<(fn ⇒ fn(src))(lifespan)
  }



  /** Monad-like function application. */
  implicit class MonadLikeFnApp[S, D](val value : S ⇒ Behaviour[D]) extends AnyVal {
    def :>> (src : Behaviour[S])(implicit lifespan : Lifespan) : Behaviour[D] =
      join(value.:>(src)(lifespan))(lifespan)
  }



  /** Monadic ops! */
  implicit class MonadLikeUnApp[S, D](
        val value : Behaviour[S ⇒ Behaviour[D]])
      extends AnyVal {
    def :>> (src : Behaviour[S])(implicit lifespan : Lifespan) : Behaviour[D] =
      join(value.:>(src)(lifespan))(lifespan)


    def :>> (src : S)(implicit lifespan : Lifespan) : Behaviour[D] =
      join(value.:>(src)(lifespan))(lifespan)
  }



  /**
   * Joins nested behaviours into one simple behaviour.
   */
  def join[T](base : Behaviour[Behaviour[T]])(implicit lifespan : Lifespan) : Behaviour[T] = {
    val res = new Flatten(base)
    lifespan.onDispose(res.dispose)
    res
  }
}
