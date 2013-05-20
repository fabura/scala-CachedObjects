package scala

import java.util.concurrent.{TimeUnit, Executors}
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable

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
  private var reloadNeeded: Boolean = true

  private final val loading: AtomicBoolean = new AtomicBoolean(false)

  CachedObject.addObserver(key, this)

  def apply(): T = {
    if (value.isEmpty) {
      synchronized {
        if (value.isEmpty) {
          load()
        }
      }
    }

    if (reloadNeeded) {
      if (loading.compareAndSet(false, true) || CachedObject.isLoadingThread(key)) {
        load()
      }
    }
    value.get
  }


  def load() {
    synchronized {
      if (reloadNeeded) {
        try {
          val thisThreadStartUpdateHere = CachedObject.trySetThreadLoading(key)
          value = Some(f)
          reloadNeeded = false
          if (thisThreadStartUpdateHere) CachedObject.unsetThreadLoading(key)
        }
        finally {
          loading.set(false)
        }
      }
    }
  }

  protected def invalidate() {
    reloadNeeded = true
  }
}

object CachedObject {

  private var observers: Map[AnyRef, List[CachedObject[_]]] = Map.empty

  def addObserver(key: AnyRef, observer: CachedObject[_]) {
    observers = observers + (key -> (observer :: observers.getOrElse(key, Nil)))
  }

  def removeObserver(key: AnyRef, observer: CachedObject[_]) {
    observers = observers.get(key).map(_.filterNot(_ == observer)).map {
      lst: List[CachedObject[_]] => observers + (key -> lst)
    }.getOrElse(observers)
  }

  def removeKey(key: AnyRef) {
    observers = observers - key
  }

  def refresh(key: AnyRef) {
    key.synchronized {
      observers(key).foreach(_.invalidate())
    }
  }

  private val threadLoading = new ThreadLocal[mutable.WeakHashMap[AnyRef, Boolean]] {
    override def initialValue(): mutable.WeakHashMap[AnyRef, Boolean] = mutable.WeakHashMap.empty
  }

  protected def trySetThreadLoading(key: AnyRef): Boolean = {
    if (!isLoadingThread(key)) {
      println(this + "loading in " + Thread.currentThread())
      threadLoading.get().put(key, true)
      true
    }
    else false
  }

  protected def unsetThreadLoading(key: AnyRef) {
    threadLoading.get().put(key, false)
  }

  protected def isLoadingThread(key: AnyRef): Boolean = {
    threadLoading.get.get(key).getOrElse(false)
  }
}

object CachedObjectTest {
  def main(args: Array[String]) {

    var i = 0
    def func = {
      println("Sleep in first at "+ Thread.currentThread())
      Thread.sleep(5000)
      println("Wake up in first at "+ Thread.currentThread())
      i += 1
      println("I value set to " + i )
      i
    }

    val cachedObject = new CachedObject(CachedObjectTest.getClass, func)

    var j = 0
    def func2 = {
      println("Sleep in second at "+ Thread.currentThread())
      Thread.sleep(5000)
      println("Wake up in second at "+ Thread.currentThread())
      j = cachedObject() + 1
      println("J value set to " + j )
      j
    }

    val cachedObject2 = new CachedObject(CachedObjectTest.getClass,func2)

    Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(new Runnable {
      def run() {
        CachedObject.refresh(CachedObjectTest.getClass)
      }
    }, 0, 10, TimeUnit.SECONDS)

    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Runnable {
      def run() {
        println("Thread: " + Thread.currentThread() + " value: " + cachedObject2())
      }
    }, 0, 870, TimeUnit.MILLISECONDS)

    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Runnable {
      def run() {
        println("Thread: " + Thread.currentThread() + " value: " + cachedObject2())
      }
    }, 0, 123, TimeUnit.MILLISECONDS)
  }
}