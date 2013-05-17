package scala

import java.util.concurrent.{ TimeUnit, Executors}
import java.util.concurrent.atomic.AtomicBoolean

/** Created by bulat.fattahov 2013
  * Uses for caching objects and refreshing cache by request.
  * Using example:
  * val _applications = new CachedObject(myObject, myInitFunction())
  * ...
  * _application().doSomething() // myInitFunction() executed here
  * _application().doSomething() // will use cached value of myInitFunction()
  * ...
  * CachedObject.refresh(myObject)
  * _application().doSomething() // myInitFunction() will executed again!
  */
class CachedObject[T](key: AnyRef, f: => T) extends (() => T) {
  @volatile
  private var value: Option[T] = None

  @volatile
  private var reloadNeeded: Boolean = false

  private final val loading: AtomicBoolean = new AtomicBoolean(false)

  CachedObject.addObserver(key, this)

  def apply(): T = {
    if (value.isEmpty) { synchronized { if (value.isEmpty) { value = Some(f)}}}

    if (reloadNeeded) {
      if (loading.compareAndSet(false, true)) {
        try {
          value = Some(f)
        }
        finally {
          loading.set(false)
          reloadNeeded = false
        }
      }
    }
    value.get
  }

  protected def invalidate() { reloadNeeded = true }
}

object CachedObject {

  private var observers: Map[AnyRef, List[CachedObject[_]]] = Map.empty

  def addObserver(key: AnyRef, observer: CachedObject[_]) {
    observers = observers + (key -> (observer :: observers.getOrElse(key, Nil)))
  }

  def removeObserver(key: AnyRef, observer: CachedObject[_]) {
    observers = observers.get(key).map(_.filterNot(_ == observer)).map {lst: List[CachedObject[_]] => observers + (key -> lst)}.getOrElse(observers)
  }

  def removeKey(key: AnyRef) {
    observers = observers - key
  }

  def refresh(key: AnyRef) {
    observers(key).foreach(_.invalidate())
  }
}

object CachedObjectTest {
  def main(args: Array[String]) {

    var i = 0
    def func = {
      println("Sleep at "+ Thread.currentThread())
      Thread.sleep(1000)
      println("Wake up at "+ Thread.currentThread())
      i += 1
      i
    }

    val cachedObject = new CachedObject(CachedObjectTest.getClass, func)

    Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(new Runnable {
      def run() { CachedObject.refresh(CachedObjectTest.getClass) }
    }, 0, 1, TimeUnit.SECONDS)

    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Runnable {
      def run() {
        println("Thread: " + Thread.currentThread() + " value: " + cachedObject())
      }
    }, 0, 100, TimeUnit.MILLISECONDS)

    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Runnable {
      def run() {
        println("Thread: " + Thread.currentThread() + " value: " + cachedObject())
      }
    }, 0, 100, TimeUnit.MILLISECONDS)
  }
}