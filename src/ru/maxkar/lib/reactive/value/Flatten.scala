package ru.maxkar.lib.reactive.value

import ru.maxkar.lib.reactive.event.Event
import ru.maxkar.lib.reactive.wave.Participant
import ru.maxkar.lib.reactive.wave.Wave

/**
 * Flatten behaviour. Joins two Behaviour[Behaviour[T]] into Behaviour[T].
 * @param T value type.
 * @param source behaviour to join.
 */
private[value] final class Flatten[T](
      source : Behaviour[Behaviour[T]])
    extends Behaviour[T] {

  /** Wave participant. */
  private val participant = new Participant(
    participate, resolved, reset)
  source.change.addCorrelatedNode(participant)


  /** Peer behaviour. */
  private var nestedSource = source.value
  nestedSource.change.addCorrelatedNode(participant)



  /** Current value. */
  private var currentValue = nestedSource.value



  /** Flag indicating that value was changed during current wave. */
  private var changed = false



  /** Participation handler. */
  private def participate() : Unit = {
    source.change.defer(participant)
    participant.invokeBeforeResolve(onBaseResolved)
  }



  /** Handles a "base resolved" event. */
  private def onBaseResolved() : Unit =
    source.value.change.defer(participant)



  /** Marks this node as resovled. */
  private def resolved() : Unit = {
    /* No update, just return. */
    if (!source.change.value && !nestedSource.change.value)
      return

    /* Update flattened source. */
    if (source.change.value) {
      nestedSource.change.removeCorrelatedNode(participant)
      nestedSource = source.value
      nestedSource.change.addCorrelatedNode(participant)
    }

    val newValue = nestedSource.value

    if (newValue == currentValue)
      return

    currentValue = newValue
    changed = true
  }



  /** Resets this node after wave completion. */
  private def reset() : Unit = changed = false




  /* IMPLEMENTATION. */

  override def value() : T = currentValue

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
