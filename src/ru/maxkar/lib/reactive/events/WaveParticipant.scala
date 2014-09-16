package ru.maxkar.lib.reactive.events

import scala.collection.mutable.Queue

/**
 * Node for participating inside the flow. This node
 * is used to track resolution order between different
 * components in each flow.
 * @param onBoot boot handler. This handler is invoke during
 * the first attempt to resolve this node.
 * @param onResolved resolved handler. This handler is invoked
 * after this node was resolved. It gives user a chance to
 * complete node update (by checking dependency nodes and updating
 * current node appropriately for example). It is too late to
 * perform wave-related operations (like deferring) so wave is
 * not passed to this handler.
 * @param onCleanup cleanup listener. This listener would be
 * invoked after the propagation wave. Main goal for this method
 * is to reset "events" to some "default" state. Events can't be reset
 * in the <code>onResolved</code> listeners because that events can
 * be consumed by other nodes in the flow.
 */
final class WaveParticipant(
      onBoot : Wave ⇒ Unit,
      onResolved : () ⇒ Unit,
      onCleanup : () ⇒ Unit) {

  /**
   * Nodes correlated to this participant. That correlated nodes should
   * be engaged if this node is engaged.
   */
  private val correlatedNodes = new java.util.ArrayList[WaveParticipant]



  /**
   * Functions to ivoke before this wave node is finally resolved.
   * These function may install additional deferrances and dependencies.
   */
  private val preResolutionListeners = new Queue[Wave ⇒ Unit]



  /**
   * Queue of functions to invoke when this node is resolved.
   */
  private val resolutionListeners = new Queue[WaveParticipant]



  /**
   * Number of pending dependencies.
   */
  private var pendingDeps = 0



  /**
   * Code for the current state.
   */
  private var state = WaveParticipant.STATE_READY



  /* WAVE CLIENT SECTION. */

  /**
   * Registers a node as correlated to this node.  This method creates
   * uni-directional relationship: if <code>this</code> node is added to
   * a wave, then <code>corr</code> node is added to the same wave.
   * <p>Correlation does not establish any sort of "order" between nodes.
   * So <code>corr</code> node may be resolved before <code>this</node>.
   * @param node node correlated to this.
   */
  def addCorrelatedNode(corr : WaveParticipant) : Unit =
    correlatedNodes.add(corr)



  /**
   * Removes correlation between <code>this</code> node and <code>corr</code>
   * node. Does nothing if there is no correlation. If correlation was
   * established several times, removes one correlation only.
   */
  def removeCorrelatedNode(corr : WaveParticipant) : Unit =
    correlatedNodes.remove(corr)



  /**
   * Attempts to engage this participant into the target wave. Each node
   * must participate in one wave only.
   * @param wave wave to join to.
   * @return <code>true</code> iff this node was added to the wave.
   * @throws IllegalStateException if wave is advanced after the engagement
   * phase.
   */
  def engage(implicit wave : Wave) : Unit = {
    if (state == WaveParticipant.STATE_READY) {
      state = WaveParticipant.STATE_ENGAGED
      wave.enqueueParticipant(this)
    }
    else if (state != WaveParticipant.STATE_ENGAGED)
      throw new IllegalStateException(
        "This node is in incorrect engagement position")
  }



  /**
   * Adds a dependency between nodes in a way that this node will
   * be resolved only after the dependent node is resolved.
   * @param target "previous" node before this node.
   * @return this node.
   * @throws IllegalStateException if this node is not engaged
   * in any wave.
   * @throws IllegalStateException if target node is engaged in different * wave.
   */
  def defer(target : WaveParticipant) : this.type = {
    if (state != WaveParticipant.STATE_ENGAGED)
      throw new IllegalStateException(
        "Cannot defer non-engaged node, state is " + state)

    /* Either already resolved or not in this wave. */
    if (target.state != WaveParticipant.STATE_ENGAGED)
      return this

    pendingDeps += 1
    target.resolutionListeners += this
    this
  }



  /**
   * List version of defer.
   */
  def deferI(target : Iterable[WaveParticipant]) : this.type = {
    val itr = target.iterator
    while (itr.hasNext)
      defer(itr.next)
    this
  }



  /**
   * Vararg version of defer.
   */
  def deferV(target : WaveParticipant*) : this.type =
    deferI(target)



  /**
   * Adds a dependency between nodes along with the callback
   * to invoke when all the nodes are resolved.
   * @param callback callback to invoke when previous node is resolved.
   * This callback can create additional dependencies (further deferrances)
   * for this node.
   * @param target "previous" node before this node.
   * @throws IllegalStateException if this node is not engaged
   * in any wave.
   * @throws IllegalStateException if target node is engaged in different * wave.
   */
  def deferCb(callback : Wave ⇒ Unit, target : WaveParticipant) : this.type = {
    preResolutionListeners += callback
    defer(target)
  }



  /**
   * List version of the deferCb.
   */
  def deferCbI(callback : Wave ⇒ Unit, targets : Iterable[WaveParticipant]) : this.type = {
    preResolutionListeners += callback
    deferI(targets)
  }




  /**
   * Vararg version of the deferCb.
   */
  def deferCbV(callback : Wave ⇒ Unit, targets : WaveParticipant*) : this.type =
    deferCbI(callback, targets)



  /* QUEUE INTEGRATION API. */

  /**
   * Handles an engagement event. Adds all correlated nodes into the flow
   * but does not attempts to resolve anything because some nodes
   * may be out of the flow. Gives a chance to participate in the wave to
   * all the dependencies.
   * @param wave current wave.
   */
  private[events] def engageComplete(wave : Wave) : Unit = {
    val itr = correlatedNodes.iterator()
    while (itr.hasNext())
      itr.next().engage(wave)
  }



  /**
   * Bootstraps this wave. This is the first attempt to resolve the
   * node after wave completed initial engagement (so no other nodes
   * will be added into the propagation).
   */
  private[events] def boot(wave : Wave) : Unit = {
    onBoot(wave)

    /* Try to resolve immediately if possible. */
    tryResolve(wave)
  }



  /**
   * Attempts to resolve this node. This method will run all listeners
   * which was registered during calls do defer* methods. That method
   * may install additional dependencies. If there are no unsatisfied
   * dependencies at the end of this process, this node is considered
   * "resolved" and added into the cleanup queue. Otherwise this node
   * will wait until all dependencies are resolved.
   * @param wave current wave.
   */
  private[events] def tryResolve(wave : Wave) : Unit = {
    /* TODO: Copy into another batch? This may stop a little bit
     * earlier than allowed.
     */
    while (pendingDeps == 0 && !preResolutionListeners.isEmpty) {
      val listener = preResolutionListeners.dequeue
      listener(wave)
    }

    /* New deps were discovered, return. */
    if (pendingDeps > 0)
      return

    state = WaveParticipant.STATE_RESOLVED
    onResolved()
    wave.enqueueResolved(this)
  }



  /**
   * Handles an event where one of dependencies was resolved.
   * If all the dependencies are resolved, attempts to resolve this
   * node. If node is resolved, it will be enqueued to notify other
   * nodes about it's completion.
   * @param wave current wave.
   */
  private def depResolved(wave : Wave) : Unit = {
    pendingDeps -= 1

    if (pendingDeps == 0)
      tryResolve(wave)
  }



  /**
   * Resets dependencies for all listeners.
   * @param wave current wave.
   */
  private[events] def notifyDeps(wave : Wave) : Unit =
    while (!resolutionListeners.isEmpty)
      resolutionListeners.dequeue.depResolved(wave)



  /**
   * Cleans this node after the wave propagation.
   */
  private[events] def cleanup() : Unit = {
    state = WaveParticipant.STATE_READY
    onCleanup()
  }
}




/** Wave participant companion. */
final object WaveParticipant {
  /** Participant is ready to be engaged. */
  private val STATE_READY = 0

  /** Participant is engaged in the wave. */
  private val STATE_ENGAGED = 1


  /** Node is resolved during the propagation. */
  private val STATE_RESOLVED = 2
}
