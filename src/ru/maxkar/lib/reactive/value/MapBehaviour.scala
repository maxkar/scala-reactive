package ru.maxkar.lib.reactive.value

import ru.maxkar.lib.reactive.event.Event
import ru.maxkar.lib.reactive.wave.Participant
import ru.maxkar.lib.reactive.wave.Wave


/**
 * Behaviour which applies a "map" function to get another reactive value.
 * @param S source type.
 * @param T destination (value) type.
 * @param mapper map function.
 * @param source source behaviour.
 */
private[value] final class MapBehaviour[S, T](
      mapper : S ⇒ T, source : Behaviour[S])
    extends Behaviour[T] {

  /** Wave participant. */
  private val participant = new Participant(
    participate, resolved, reset)
  source.change.addCorrelatedNode(participant)



  /** Current value. */
  private var currentValue = mapper(source.value)



  /** Flag indicating that value was changed during current wave. */
  private var changed = false



  /** Participation handler. */
  private def participate(wave : Wave) : Unit =
    source.change.defer(participant)



  /** Marks this node as resovled. */
  private def resolved() : Unit = {
    /* No update, just return. */
    if (!source.change.value)
      return

    val newValue = mapper(source.value)
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
