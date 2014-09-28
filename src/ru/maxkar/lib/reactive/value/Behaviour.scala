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
  def :<[R](mapper : T ⇒ R) : Behaviour[R] =
    new MapBehaviour(mapper, this)
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
   * Creates a proxying session.
   * @return new proxying session.
   */
  def proxySession() : Session = new Session()



  /** Automatic function uplift. */
  implicit class MapFnUplift[S, D](val value : S ⇒ D) extends AnyVal {
    @inline
    def :> (src : Behaviour[S]) : Behaviour[D] = src :< value
  }


  /** More applicative ops. */
  implicit class AppUnUplift[S, D](val value : Behaviour[S ⇒ D]) extends AnyVal {
    @inline
    def :> (src : Behaviour[S]) : Behaviour[D] =
      new ApplicativeBehaviour(value, src)

    @inline
    def :> (src : S) : Behaviour[D] = value :< (fn ⇒ fn(src))
  }



  /** Monad-like function application. */
  implicit class MonadLikeFnApp[S, D](val value : S ⇒ Behaviour[D]) extends AnyVal {
    @inline
    def :>> (src : Behaviour[S]) : Behaviour[D] =
      join(value :> src)
  }



  /** Monadic ops! */
  implicit class MonadLikeUnApp[S, D](
        val value : Behaviour[S ⇒ Behaviour[D]])
      extends AnyVal {
    @inline
    def :>> (src : Behaviour[S]) : Behaviour[D] = join(value :> src)


    @inline
    def :>> (src : S) : Behaviour[D] = join(value :> src)
  }



  /** Automatic var conversion. */
  @inline
  implicit def variable2behavior[T](variable : Variable[T]) : Behaviour[T] =
    variable.behaviour



  /**
   * Creates a proxy for the behaviour. While session is alive, proxy
   * will engage in the same wave as the original behaviour and will
   * have the same change event value. However, after session is destroyed,
   * proxy will be detached from the underlying value.
   * This can be usefull for temporary graphs (like models for dialogs).
   * @param T value type.
   * @param base value to proxy to.
   * @param session proxying session. Sessions group proxies to each other.
   * @return proxy for the <code>base</code> behaviour.
   */
  def proxy[T](base : Behaviour[T])(implicit session : Session) : Behaviour[T] = {
    session.ensureAlive()
    val proxy = new Proxy(base)
    session += proxy.detach
    proxy
  }



  /**
   * Joins nested behaviours into one simple behaviour.
   */
  def join[T](base : Behaviour[Behaviour[T]]) : Behaviour[T] =
    new Flatten(base)
}
