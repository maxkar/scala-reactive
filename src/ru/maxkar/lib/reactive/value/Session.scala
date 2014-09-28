package ru.maxkar.lib.reactive.value


/**
 * Value proxying session. All proxies created in the context of this
 * session will be detached from underlying values when this session
 * is closed.
 */
final class Session private[value]() {
  /**
   * Items in the session.
   */
  private var items = new scala.collection.mutable.ArrayBuffer[() ⇒ Unit]



  /**
   * Ensures that this session is alive.
   * @throws IllegalException is this session is already destroyed.
   */
  private[value] def ensureAlive() : Unit =
    if (items == null)
      throw new IllegalStateException(
        "Proxying session is already closed")



  /**
   * Adds a destructor to this session.
   */
  private[value] def += (item : () ⇒ Unit) =
    items += item



  /**
   * Destroys a session. All proxies associated with this session
   * will be detached from underlying values.
   * Do nothing if this session is already destroyed.
   */
  def destroy() : Unit = {
    val last = items
    if (last == null)
      return

    items = null

    val itr = last.iterator
    while (itr.hasNext)
      itr.next()()
  }
}
