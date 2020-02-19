package com.twitter.util

import com.twitter.conversions.DurationOps._
import org.scalatest.FunSuite
import com.twitter.util.CoverageChecker

class PromiseTest extends FunSuite {

  test("Promise.detach should not detach other attached promises") {
    val p = new Promise[Unit]
    val attached1 = Promise.attached(p)
    val attached2 = Promise.attached(p)

    // detaching `attached2` doesn't detach `attached1`
    assert(attached2.detach())

    p.setDone()
    assert(!attached2.isDefined)
    assert(attached1.isDefined)
  }

  test("Promise.attached should detach via interruption") {
    val p = new HandledPromise[Unit]()
    val f = Promise.attached(p)
    f.setInterruptHandler {
      case t: Throwable =>
        if (f.detach())
          f.update(Throw(t))
    }
    f.raise(new Exception())
    assert(p.handled == None)
    assert(f.isDefined)
    intercept[Exception] {
      Await.result(f)
    }
  }

  test("Promise.attached should validate success") {
    val p = Promise[Unit]()
    val f = Promise.attached(p)
    p.setValue(())
    assert(f.isDefined)
    assert(Await.result(f.liftToTry) == Return(()))
  }

  test("Promise.attached should validate failure") {
    val p = Promise[Unit]()
    val f = Promise.attached(p)
    val e = new Exception
    p.setException(e)
    assert(f.isDefined)
    val actual = intercept[Exception] {
      Await.result(f)
    }
    assert(actual == e)
  }

  test("Promise.attached should detach properly for futures") {
    val f = Future.Unit
    val p = Promise.attached(f)
    assert(!p.detach())
    assert(p.poll == Some(Return(())))
  }

  test("Detached promises are no longer connected: Success") {
    val p = Promise[Unit]()
    val att = Promise.attached(p)
    att.detach()
    val e = new Exception()
    p.setException(e)
    att.setValue(())
    assert(p.poll == Some(Throw(e)))
    assert(att.poll == Some(Return(())))
  }

  test("Detached promises are no longer connected: Failure") {
    val p = Promise[Unit]()
    val att = Promise.attached(p)
    att.detach()
    val e = new Exception()
    p.setValue(())
    att.setException(e)
    assert(att.poll == Some(Throw(e)))
    assert(p.poll == Some(Return(())))
  }

  test("become not allowed when already satisfied") {
    val value = "hellohello"
    val p = new Promise[String]()
    p.setValue(value)

    val ex = intercept[IllegalStateException] {
      p.become(new Promise[String]())
    }
    assert(ex.getMessage.contains(value))
  }

  test("Updating a Promise more than once should fail") {
    val p = new Promise[Int]()
    val first = Return(1)
    val second = Return(2)

    p.update(first)
    val ex = intercept[Promise.ImmutableResult] {
      p.update(second)
    }
    assert(ex.message.contains(first.toString))
    assert(ex.message.contains(second.toString))
  }

  // this won't work inline, because we still hold a reference to d
  def detach(d: Promise.Detachable): Unit = {
    assert(d != null)
    assert(d.detach())
  }

  test("Promise.attached undone by detach") {
    val p = new Promise[Unit]
    assert(p.waitqLength == 0)
    val q = Promise.attached(p)
    assert(p.waitqLength == 1)
    q.respond(_ => ())
    assert(p.waitqLength == 1)
    q.detach()
    assert(p.waitqLength == 0)
  }

  class HandledMonitor extends Monitor {
    var handled: Throwable = null: Throwable
    def handle(exc: Throwable): Boolean = {
      handled = exc
      true
    }
  }

  test("Promise.respond should monitor fatal exceptions") {
    val p = new Promise[Int]
    val m = new HandledMonitor()
    val exc = new ThreadDeath()

    Monitor.using(m) {
      p ensure { throw exc }
    }

    assert(m.handled == null)
    p.update(Return(1))
    assert(m.handled == exc)
  }

  test("Promise.transform should monitor fatal exceptions") {
    val m = new HandledMonitor()
    val exc = new ThreadDeath()
    val p = new Promise[Int]

    Monitor.using(m) {
      p transform { case _ => throw exc }
    }

    assert(m.handled == null)
    val actual = intercept[ThreadDeath] {
      p.update(Return(1))
    }
    assert(actual == exc)
    assert(m.handled == exc)
  }

  test("Promise can only forwardInterruptsTo one other future") {
    val a = new Promise[Int]
    val b = new HandledPromise[Int]
    val c = new HandledPromise[Int]

    a.forwardInterruptsTo(b)
    a.forwardInterruptsTo(c)

    val ex = new Exception("expected")
    a.raise(ex)

    assert(b.handled == None)
    assert(c.handled == Some(ex))
  }

  test("forwardInterruptsTo does not forward to a completed future") {
    val a = new Promise[Int]
    val b = new HandledPromise[Int]

    b.update(Return(3))

    val ex = new Exception("expected")

    a.forwardInterruptsTo(b)
    a.raise(ex)

    assert(b.handled == None)
  }

  test("ready") {
    // this verifies that we don't break the behavior regarding how
    // the `Scheduler.flush()` is a necessary in `Promise.ready`
    Future.Done.map { _ =>
      Await.result(Future.Done.map(Predef.identity), 5.seconds)
    }
  }

  test("maps use local state at the time of callback creation") {
    val local = new Local[String]
    local.set(Some("main thread"))
    val p = new Promise[String]()
    val f = p.map { _ =>
      local().getOrElse("<unknown>")
    }

    val thread = new Thread(new Runnable {
      def run(): Unit = {
        local.set(Some("worker thread"))
        p.setValue("go")
      }
    })
    thread.start()

    assert("main thread" == Await.result(f, 3.seconds))
  }

  test("interrupts use local state at the time of callback creation") {
    Promise.useLocalInInterruptible(true)

    val local = new Local[String]
    implicit val timer: Timer = new JavaTimer(true)

    local.set(Some("main thread"))
    val p = new Promise[String]

    p.setInterruptHandler({
      case _ =>
        val localVal = local().getOrElse("<empty>")
        p.updateIfEmpty(Return(localVal))
    })
    val f = Future.sleep(1.second).before(p)

    val thread = new Thread(new Runnable {
      def run(): Unit = {
        local.set(Some("worker thread"))
        p.raise(new Exception)
      }
    })
    thread.start()

    assert("main thread" == Await.result(f, 3.seconds))
  }

  test("setInterruptHandler works for Interruptible promises") {
    Promise.useLocalInInterruptible(true)
    /* state == Interruptible */
    val func: PartialFunction[Throwable, Unit] = { case _ => () }
    val p: Promise[String] = new Promise[String](func)

    implicit val timer: Timer = new JavaTimer(true)

    p.setInterruptHandler({
      case _ =>
        p.updateIfEmpty(Return("Success"))
    })

    val f = Future.sleep(1.second).before(p)

    val thread = new Thread(new Runnable {
      def run(): Unit = {
        p.raise(new Exception)        
      }
    })
    thread.start()

    assert("Success" == Await.result(f, 3.seconds))
  }

  test("interrupts use raisers state") {
    Promise.useLocalInInterruptible(false)

    val local = new Local[String]
    implicit val timer: Timer = new JavaTimer(true)

    local.set(Some("main thread"))
    val p = new Promise[String]

    p.setInterruptHandler({
      case _ =>
        val localVal = local().getOrElse("<empty>")
        p.updateIfEmpty(Return(localVal))
    })
    val f = Future.sleep(1.second).before(p)

    val thread = new Thread(new Runnable {
      def run(): Unit = {
        local.set(Some("worker thread"))
        p.raise(new Exception)
      }
    })
    thread.start()

    assert("worker thread" == Await.result(f, 3.seconds))
  }

  test("interrupt then map uses local state at the time of callback creation") {
    val local = new Local[String]
    implicit val timer: Timer = new JavaTimer(true)

    local.set(Some("main thread"))
    val p = new Promise[String]()
    p.setInterruptHandler({
      case _ =>
        val localVal = local().getOrElse("<empty>")
        p.updateIfEmpty(Return(localVal))
    })
    val fRes = p.transform { _ =>
      val localVal = local().getOrElse("<empty>")
      Future.value(localVal)
    }
    val f = Future.sleep(1.second).before(fRes)

    val thread = new Thread(new Runnable {
      def run(): Unit = {
        local.set(Some("worker thread"))
        p.raise(new Exception)
      }
    })
    thread.start()

    assert("main thread" == Await.result(f, 3.seconds))
  }

  test("locals in Interruptible after detatch") {
    Promise.useLocalInInterruptible(true)

    val local = new Local[String]
    implicit val timer: Timer = new JavaTimer(true)

    local.set(Some("main thread"))
    val p = new Promise[String]

    val p2 = Promise.attached(p) // p2 is the detachable promise

    p2.setInterruptHandler({
      case _ =>
        val localVal = local().getOrElse("<empty>")
        p2.updateIfEmpty(Return(localVal))
    })

    p.setInterruptHandler({
      case _ =>
        val localVal = local().getOrElse("<empty>")
        p.updateIfEmpty(Return(localVal))
    })

    val thread = new Thread(new Runnable {
      def run(): Unit = {
        local.set(Some("worker thread"))

        // We need to call detach explicitly
        assert(p2.detach())

        p2.raise(new Exception)
        p.raise(new Exception)
      }
    })
    thread.start()

    assert("main thread" == Await.result(p2, 3.seconds))
    assert("main thread" == Await.result(p, 3.seconds))
  }

  test("setInterruptHandler propagates to linked promise") {
    Promise.useLocalInInterruptible(true)

    val local = new Local[String]
    implicit val timer: Timer = new JavaTimer(true)

    local.set(Some("main thread"))
    val p = new Promise[String]

    val p2 = Promise.attached(p) // p2 is the detachable promise

    // Access the private field state
    val field = classOf[Promise[String]].getDeclaredField("state")
    field.setAccessible(true)
    // Set p to be the linked state of p2
    field.set(p2, p)
    // Set the interrupt handler for p2, which should propagate to p
    p2.setInterruptHandler({
      case _ =>
        local.set(Some("Interrupt handled"))
        val localVal = local().getOrElse("<empty>")
        p.updateIfEmpty(Return(localVal))
    })
    // p2 is no longer necessary
    p2.detach()

    val thread = new Thread(new Runnable {
      def run(): Unit = {
        local.set(Some("worker thread"))

        p.raise(new Exception)
      }
    })
    thread.start()

    assert("Interrupt handled" == Await.result(p, 3.seconds))
  }
  test("CoverageChecker") {
    println("[Coverage - detach] - " + CoverageChecker.map.getOrElse("detach", sys.error(s"unexpected key")).mkString("[",", ", "]"))
    println("[Coverage - setInterruptHandler] - " + CoverageChecker.map.getOrElse("setInterruptHandler", sys.error(s"unexpected key")).mkString("[",", ", "]"))
    println("[Coverage - raise] - " + CoverageChecker.map.getOrElse("raise", sys.error(s"unexpected key")).mkString("[",", ", "]")) 
    println("[Coverage - continue] - " + CoverageChecker.map.getOrElse("continue", sys.error(s"unexpected key")).mkString("[",", ", "]"))
    println("[Coverage - link] - " + CoverageChecker.map.getOrElse("link", sys.error(s"unexpected key")).mkString("[",", ", "]"))
    println("[Coverage - updateIfEmpty] - " + CoverageChecker.map.getOrElse("updateIfEmpty", sys.error(s"unexpected key")).mkString("[",", ", "]"))
  }

}
