package ru.maxkar.lib.reactive.value

import org.scalatest.FunSuite

import ru.maxkar.lib.reactive.value.Behaviour._
import ru.maxkar.lib.reactive.wave.Wave

final class ValueTest extends FunSuite{

  /** Creates a function which counts number of changes of peer behaviour. */
  def count[T](v : Behaviour[T]) : () ⇒ Int = {
    /* -1 because initial setup will increment update count. */
    var updates = -1

    ((t : T) ⇒ updates += 1) :> v

    () ⇒ updates
  }



  test("Test value of the variable") {
    val v1 = variable(44)
    assert(44 === v1.b.value)

    v1.set(55)
    assert(55 === v1.b.value)
  }



  test("Test batch update of the variables") {
    val v1 = variable("AOE")
    val v2 = variable("EOA")

    assert("AOE" === v1.b.value)
    assert("EOA" === v2.b.value)

    Wave.group(txn ⇒ {
      v1.wavedSet("35", txn)
      v2.wavedSet("TT", txn)
    })

    assert("35" === v1.b.value)
    assert("TT" === v2.b.value)
  }



  test("Test that no extra events are fired on vars") {
    val v = variable(3)
    val ups = count(v.b)

    assert(0 === ups())

    v.set(4)
    assert(1 === ups())

    v.set(4)
    assert(1 === ups())
  }



  test("Test one-arg function lifting") {
    def fn(x : Int) : Int = x + 5

    val v = variable(6)
    val vv = fn _ :> v.b

    assert(11 === vv.value)

    v.set(77)
    assert(82 === vv.value)
  }



  test("One-arg lifting ignores duplicates on update.") {
    def fn(x : Int) : Int = if (x > 6) 77 else x - 3

    val v = variable(33)
    val vv = fn _ :> v.b
    val ups = count(vv)

    assert(vv.value === 77)
    assert(0 === ups())

    v.set(55)
    assert(77 === vv.value)
    assert(0 === ups())

    v.set(5)
    assert(2 === vv.value)
    assert(1 === ups())
  }



  test("Test basic multi-arg application.") {
    def fn(x : Int)(y : Int) = 2 * x + y

    val v1 = variable(10)
    val v2 = variable(3)

    val v = fn _ :> v1.b :> v2.b
    assert(23 === v.value)

    v1.set(5)
    assert(13 === v.value)

    v2.set(0)
    assert(10 === v.value)
  }



  test("Test mutliarg update count.") {
    def fn(x : Int)(y : Int)(z : Int) = x * y + z

    val v1 = variable(9)
    val v2 = variable(0)
    val v3 = variable(5)

    val v = fn _ :> v1.b :> v2.b :> v3.b
    val ups = count(v)
    assert(5 === v.value)
    assert(0 === ups())

    v1.set(6)
    assert(5 === v.value)
    assert(0 === ups())


    Wave.group(w ⇒ {
      v1.wavedSet(3, w)
      v2.wavedSet(2, w)
      v3.wavedSet(-1, w)
    })
    assert(5 === v.value)
    assert(0 === ups())


    v3.set(0)
    assert(6 === v.value)
    assert(1 === ups())
  }
}
