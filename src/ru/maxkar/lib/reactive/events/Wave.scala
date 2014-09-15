package ru.maxkar.lib.reactive.events

import scala.collection.mutable.Queue

/**
 * One propagation (update) wave. Each wave is one "transaction"
 * of the events. Each node involved in the transaction fire
 * exactly one event which can be consumed by all the registered
 * listeners.
 */
final class Wave {

  /** Current propagation state. */
  private var stage = Wave.STAGE_NEW



  /**
   * Wave queue. These participants will be "engaged" during the
   * engage phase and will have a goal to establish all nodes reacheable
   * by this wave. Wave may include extra nodes (for example, nodes which
   * do not produce event for this wave), but would not include nodes not
   * reacheable from the engaged nodes.
   */
  private val engageQueue = new Queue[WaveParticipant]



  /**
   * Queue of nodes ready to resolve. These nodes have all dependencies
   * satisfied but "resolution listeners" not fired. This helps to keep
   * stack depth manageable (no recursive calls from listeners to listeners).
   */
  private val resolveQueue = new Queue[WaveParticipant]



  /**
   * Queue of successfully resolved. These nodes are waiting for
   * the cleanup. Event pins associated with this nodes are still
   * holding the value because other nodes in resolveQueue can access
   * that state. Cleaning will allow nodes to return to "initial" state.
   */
  private val cleanupQueue = new Queue[WaveParticipant]



  /**
   * Adds a listener which will be invoked during the engage state.
   * <strong>This method should be invoked from the participant only.</strong>
   * @param participant participanT to join this wave.
   * @throws IllegalStateException if this wave has passed engagement phase.
   */
  private[events] def enqueueParticipant(participant : WaveParticipant) : Unit = {
    if (stage > Wave.STAGE_ENGAGEMENT)
      throw new IllegalStateException(
        "Too late to engage in this wave at phase " + stage)
    engageQueue += participant
  }



  /**
   * Enqueues a node into the resolve queue. This mean that node
   * wants to attempt to resolve it. During the resolution process
   * node may discover additional dependencies and still remain
   * in "undecided" state.
   * @param node node to add.
   * @throws IllegalStateException if this wave is not in resolution state.
   */
  private[events] def enqueueResolved(node : WaveParticipant) : Unit = {
    if (stage < Wave.STAGE_ENGAGEMENT || stage > Wave.STAGE_RESOLUTION)
      throw new IllegalStateException(
        "Bad state for resolution attemts, stage = " + stage)
    resolveQueue += node
  }



  /**
   * Enqueues a node as completely resolved and ready for cleanup.
   */
  private[events] def enqueueCleanup(node : WaveParticipant) : Unit = {
    if (stage < Wave.STAGE_ENGAGEMENT || stage > Wave.STAGE_RESOLUTION)
      throw new IllegalStateException(
        "Bad state for cleanup attemts, stage = " + stage)
    cleanupQueue += node
  }



  /**
   * Runs a wave.
   * @throws Error if some nodes were unable to resolve.
   */
  private[events] def run() : Unit = {
    val bootQueue = new Queue[WaveParticipant]

    stage = Wave.STAGE_ENGAGEMENT
    while (!engageQueue.isEmpty) {
      val node = engageQueue.dequeue
      bootQueue += node
      node.engageComplete(this)
    }

    val totalNodes = bootQueue.size

    stage = Wave.STAGE_RESOLUTION
    while (!bootQueue.isEmpty)
      bootQueue.dequeue.boot(this)
    while (!resolveQueue.isEmpty)
      resolveQueue.dequeue.notifyDeps(this)

    if (totalNodes != cleanupQueue.size)
      throw new Error(
        "Node completion mismatch, expected " + totalNodes +
        " but got " + cleanupQueue.size)

    stage = Wave.STAGE_CLEANUP
    while (!cleanupQueue.isEmpty)
      cleanupQueue.dequeue.cleanup()
  }
}


/**
 * Wave companion object.
 */
final object Wave {
  /** Wave is new and not started. */
  private val STAGE_NEW = 0

  /** Wave is processing engagements. */
  private val STAGE_ENGAGEMENT = 1

  /** Resolution phase. */
  private val STAGE_RESOLUTION = 2

  /** Cleanup phase. */
  private val STAGE_CLEANUP = 3

  /** Final death. */
  private val STAGE_DEAD = 4
}
