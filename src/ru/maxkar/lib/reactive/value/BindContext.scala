package ru.maxkar.lib.reactive.value

import ru.maxkar.lib.reactive.wave.Participable

/**
 * Context used for value binding. Holds both
 * lifecycle context for the binding and current
 * update wave (if any).
 * @param lifespan bind lifetime context.
 * @param update update participation context.
 */
final class BindContext(
      val lifespan : Lifespan,
      val update : Participable)
