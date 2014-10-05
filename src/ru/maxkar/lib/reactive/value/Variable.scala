package ru.maxkar.lib.reactive.value

import ru.maxkar.lib.reactive.event.Event
import ru.maxkar.lib.reactive.wave.Wave

/**
 * Behaviour "variable". This class allows direct "push" request
 * to current state. However, this behaviour never can depend on
 * other behaviours. In the dependency graph this behaviour represents
 * a leaf node.
 * @param T type of the value.
 * @param value currentValue (and initial) value.
 */
final class Variable[T] private[value] (
      private var currentValue : T)
    extends Behaviour[T] {

  /** Event trigger associated with this variable. */
  private val trigger = Event.trigger()



  /**
   * Sets value as a part of the wave. If new value equals to old value,
   * then new value is ignored.
   * @param newValue new value to set.
   * @param wave current propagation wave.
   */
  def wavedSet(newValue : T, wave : Wave) : Unit = {
    if (currentValue == newValue)
      return
    currentValue = newValue
    trigger.trigger(wave)
  }



  /**
   * Sets a new value in a brand new wave. If new value equals to
   * old value, then new value is ignored and event is not fired.
   * @param newValue new value to set.
   */
  def set(newValue : T) : Unit =
    Wave.group(wave ⇒ wavedSet(newValue, wave))



  /* IMPLEMENTATION. */
  override def value() : T = currentValue
  override val change : Event[Boolean] = trigger.event
}
