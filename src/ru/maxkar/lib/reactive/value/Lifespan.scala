package ru.maxkar.lib.reactive.value

/**
 * Base lifecycle management trait. Lifespan groups several bindings
 * togever and alllows them to be disposed simultaneously. This is
 * higly convenient for libraries where application parts have different
 * life cycles. For example, dialog window have life time less than
 * core application.
 * <p>This trait do not provide a "disposal" API. It only allows
 * to register binding to that lifespan so action would be invoked
 * on the span end.
 */
trait Lifespan {
  /**
   * Registers a function to be invoked when this lifespan is disposed.
   * @param listener listener to invoke when this lifespan is disposed.
   */
  def onDispose(listener : () ⇒ Unit) : Unit
}
