package ru.maxkar.lib.reactive.value

import ru.maxkar.lib.reactive.event.Event
import ru.maxkar.lib.reactive.wave.Participant
import ru.maxkar.lib.reactive.wave.Wave

/**
 * Applicative function application implementation.
 * @palam fn applicative function.
 * @param base base value.
 */
private[value] final class ApplicativeBehaviour[S, R](
      fn : Behaviour[S ⇒ R],
      base : Behaviour[S])
    extends Behaviour[R] {


  /** Wave participant. */
  private val participant = new Participant(
    participate, resolved, reset)
  fn.change.addCorrelatedNode(participant)
  base.change.addCorrelatedNode(participant)



  /** Current value. */
  private var currentValue = fn.value()(base.value)



  /** Flag indicating that value was changed during current wave. */
  private var changed = false



  /** Participation handler. */
  private def participate(wave : Wave) : Unit = {
    fn.change.defer(participant)
    base.change.defer(participant)
  }



  /** Marks this node as resovled. */
  private def resolved() : Unit = {
    /* No update, just return. */
    if (!fn.change.value && !base.change.value)
      return

    val newValue = fn.value()(base.value)
    if (newValue == currentValue)
      return

    currentValue = newValue
    changed = true
  }



  /** Resets this node after wave completion. */
  private def reset() : Unit = changed = false




  /* IMPLEMENTATION. */

  override def value() : R = currentValue

  override val change : Event[Boolean] = new Event[Boolean] {
    override def addCorrelatedNode(node : Participant) : Unit =
      participant.addCorrelatedNode(node)

    override def removeCorrelatedNode(node : Participant) : Unit =
      participant.removeCorrelatedNode(node)

    override def defer(node : Participant) : Unit =
      node.defer(participant)

    override def value() : Boolean = changed
  }
}
