package ru.maxkar.lib.reactive.value

import ru.maxkar.lib.reactive.event.Event
import ru.maxkar.lib.reactive.wave.Participant
import ru.maxkar.lib.reactive.wave.Wave

/**
 * Proxy behavior. This behavior have exactly the same value
 * as the underlying behaviour while session is not detached.
 * Value of this node after detaching is not defined.
 * This can be usefull for "temporary data views". For example,
 * to display a data editing dialog and then abandon the session.
 * This will remove all references from core model to proxy and
 * the whole "layer" will be eligible for GC.
 * @param T value type.
 */
private[value] final class Proxy[T](
      peer : Behaviour[T])
    extends Behaviour[T] {


  /** Wave participant. */
  private var participant = new Participant(
    participate, () ⇒ (), () ⇒ ())
  peer.change.addCorrelatedNode(participant)



  /** Engages in the participation. */
  private def participate(w : Wave) : Unit =
    if (participant != null)
      peer.change.defer(participant)



  /** Detaches this proxy from the underlying value. */
  def detach() : Unit =
    if (participant != null) {
      peer.change.removeCorrelatedNode(participant)
      participant = null
    }



  /* IMPLEMENTATION. */

  override def value() : T = peer.value()


  override val change : Event[Boolean] = new Event[Boolean] {
    override def addCorrelatedNode(node : Participant) : Unit =
      if (participant != null)
        participant.addCorrelatedNode(node)

    override def removeCorrelatedNode(node : Participant) : Unit =
      if (participant != null)
        participant.removeCorrelatedNode(node)

    override def defer(node : Participant) : Unit =
      if (participant != null)
        node.defer(participant)

    override def deferBy(node : Participant, cb : () ⇒ Unit) : Unit =
      if (participant != null)
        node.deferCb(cb, participant)
      else
        node.invokeBeforeResolve(cb)

    override def value() : Boolean =
      if (participant != null)
        peer.change.value
      else
        false
  }
}
