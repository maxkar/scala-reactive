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

  /**
   * Flag, indicating that this node is disposed.
   */
  private var disposed : Boolean = false



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
    if (disposed)
      return

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



  /** Disposes this node. */
  private[value] def dispose() : Unit = {
    source.change.removeCorrelatedNode(participant)
    nestedSource.change.removeCorrelatedNode(participant)
    disposed = true
  }




  /* IMPLEMENTATION. */

  override def value() : T = currentValue

  override val change = Event.fromParticipant(participant, changed)
}
