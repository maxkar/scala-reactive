package ru.maxkar.lib.reactive.value

import org.scalatest.FunSuite

import ru.maxkar.lib.reactive.value.Lifespan._
import ru.maxkar.lib.reactive.value.Behaviour._
import ru.maxkar.lib.reactive.wave.Participant
import ru.maxkar.lib.reactive.event.Event


/**
 * Tests dedicated to a lifecycle management.
 */
final class LifecycleTests extends FunSuite {
  implicit val lifecycle = Lifespan.forever

  /** Ref-counting item. */
  private final class RefCount[+T](peer : Behaviour[T]) extends Behaviour[T] {
    private val corrs = new scala.collection.mutable.ArrayBuffer[Participant]

    override def value() : T = peer.value
    override val change = new Event[Boolean] {
      override def addCorrelatedNode(node : Participant) = {
        peer.change.addCorrelatedNode(node)
        corrs += node
      }

      override def removeCorrelatedNode(node : Participant) = {
        peer.change.removeCorrelatedNode(node)
        corrs -= node
      }

      override def defer(target : Participant) =
        peer.change.defer(target)

      override def value() = peer.change.value()
    }


    /** "No listeners" assert. */
    def checkEmpty() : Unit = assert(corrs.isEmpty, "Correlation set is not empty")
  }



  def checkRefs(refs : RefCount[Any]*) : Unit =
    refs.foreach(ref ⇒ ref.checkEmpty())



  test("On-behaviour map live correctly") {
    val v = variable(3)
    val rv = new RefCount(v)
    def fn(x : Int) = x + 1

    implicit val session = mkSession()
    val vv = rv :< fn

    v.set(5)
    assert(6 === vv.value)

    session.destroy()
    v.set(8)
    assert(6 === vv.value)

    checkRefs(rv)
  }



  test("Map function uplift live correctly") {
    val v = variable(3)
    val rv = new RefCount(v)
    def fn(x : Int) = x + 1

    implicit val session = mkSession()
    val vv = fn _ :> rv

    v.set(5)
    assert(6 === vv.value)

    session.destroy()
    v.set(8)
    assert(6 === vv.value)

    checkRefs(rv)
  }



  test("Applicative application live correctly") {
    val v = variable(1)
    val rv = new RefCount(v)
    val w = variable(2)
    var rw = new RefCount(w)
    def fn(x : Int)(y : Int) = x + y

    implicit val session = mkSession()
    val vv = fn _ :> rv :> rw

    v.set(6)
    w.set(8)
    assert(14 === vv.value)

    session.destroy()
    checkRefs(rv, rw)
  }



  test("Applicative application to value live correctly") {
    val v = variable(1)
    val rv = new RefCount(v)
    def fn(x : Int)(y : Int) = x + y

    implicit val session = mkSession()
    val vv = fn _ :> rv :> 4

    v.set(6)
    assert(10 === vv.value)

    session.destroy()
    checkRefs(rv)
  }



  test("Monadic-like function uplift live correctly") {
    val v1 = variable(1)
    val v2 = variable(2)
    val v3 = variable(true)
    val rv1 = new RefCount(v1)
    val rv2 = new RefCount(v2)
    val rv3 = new RefCount(v3)

    def fn(v : Boolean) = if (v) rv1 else rv2

    implicit val session = mkSession()
    val vv = fn _ :>> rv3

    v3.set(false)
    checkRefs(rv1)

    v3.set(true)
    checkRefs(rv2)

    v2.set(8)

    session.destroy()
    checkRefs(rv1, rv2, rv3)

    v3.set(false)
    checkRefs(rv1, rv2, rv3)
  }



  test("Monadic-like function application can be deconstructed on the fly") {
    val v1 = variable(1)
    val v2 = variable(2)
    val v3 = variable(6)
    val rv1 = new RefCount(v1)
    val rv2 = new RefCount(v2)
    val rv3 = new RefCount(v3)

    def fn(v : Int) = if (v < 10) rv1 else rv2

    implicit val session = mkSession()

    rv3 :< (t ⇒ (if (t == 5) session.destroy()))
    val vv = fn _ :>> rv3

    v3.set(11)
    checkRefs(rv1)
    v3.set(5)
    checkRefs(rv1, rv2, rv3)
  }



  test("Monadic-like function application in behaviour live correctly") {
    val v1 = variable(1)
    val v2 = variable(2)
    val v3 = variable(6)
    val v4 = variable(10)
    val rv1 = new RefCount(v1)
    val rv2 = new RefCount(v2)
    val rv3 = new RefCount(v3)
    var rv4 = new RefCount(v4)

    def fn(x : Int)(y : Int) = if (x < y) rv1 else rv2

    implicit val session = mkSession()
    val vv = fn _ :> rv3 :>> rv4

    v3.set(20)
    checkRefs(rv1)

    v3.set(5)
    checkRefs(rv2)

    v4.set(3)

    session.destroy()
    checkRefs(rv1, rv2, rv3, rv4)

    v3.set(0)
    checkRefs(rv1, rv2, rv3, rv4)
  }



  test("Monadic-like function application in behaviour can be deconstructed on the fly") {
    val v1 = variable(1)
    val v2 = variable(2)
    val v3 = variable(6)
    var v4 = variable(10)
    val rv1 = new RefCount(v1)
    val rv2 = new RefCount(v2)
    val rv3 = new RefCount(v3)
    val rv4 = new RefCount(v4)

    def fn(x : Int)(y : Int) = if (x < y) rv1 else rv2

    implicit val session = mkSession()

    v3 :< (t ⇒ (if (t == 5) session.destroy()))
    val vv = fn _ :> rv3 :>> rv4

    v3.set(20)
    checkRefs(rv1)
    v4.set(30)
    checkRefs(rv2)
    v4.set(6)
    checkRefs(rv1)

    v3.set(5)
    checkRefs(rv1, rv2, rv3, rv4)
  }



  test("Monadic-like function application to value in behaviour live correctly") {
    val v1 = variable(1)
    val v2 = variable(2)
    val v3 = variable(6)
    val rv1 = new RefCount(v1)
    val rv2 = new RefCount(v2)
    val rv3 = new RefCount(v3)

    def fn(x : Int)(y : Int) = if (x < y) rv1 else rv2

    implicit val session = mkSession()
    val vv = fn _ :> rv3 :>> 10

    v3.set(20)
    checkRefs(rv1)

    v3.set(5)
    checkRefs(rv2)

    session.destroy()
    checkRefs(rv1, rv2, rv3)

    v3.set(100)
    checkRefs(rv1, rv2, rv3)
  }



  test("Monadic-like function application to value in behaviour can be deconstructed on the fly") {
    val v1 = variable(1)
    val v2 = variable(2)
    val v3 = variable(6)
    val rv1 = new RefCount(v1)
    val rv2 = new RefCount(v2)
    val rv3 = new RefCount(v3)

    def fn(x : Int)(y : Int) = if (x < y) rv1 else rv2

    implicit val session = mkSession()

    v3 :< (t ⇒ (if (t == 5) session.destroy()))
    val vv = fn _ :> rv3 :>> 10

    v3.set(20)
    checkRefs(rv1)
    v3.set(0)
    checkRefs(rv2)
    v3.set(16)
    checkRefs(rv1)

    v3.set(5)
    checkRefs(rv1, rv2, rv3)
  }



  test("Join live correctly") {
    val v1 = variable(2)
    val v2 = variable(4)
    val rv1 = new RefCount(v1)
    val rv2 = new RefCount(v2)
    val v3 = variable(rv1)
    val rv3 = new RefCount(v3)

    implicit val session = mkSession()
    val vv = join(rv3)

    v3.set(rv2)
    checkRefs(rv1)
    v3.set(rv1)
    checkRefs(rv2)

    session.destroy()
    checkRefs(rv1, rv2, rv3)
  }



  test("Join can be deconstructed on the fly") {
    val v1 = variable(2)
    val v2 = variable(4)
    val rv1 = new RefCount(v1)
    val rv2 = new RefCount(v2)
    val v3 = variable(rv1)
    val rv3 = new RefCount(v3)

    implicit val session = mkSession()
    rv3 :< (x ⇒ if (x `eq` rv2) session.destroy())
    val vv = join(rv3)

    v3.set(rv2)

    checkRefs(rv1, rv2, rv3)
  }
}
