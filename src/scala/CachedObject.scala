package scala

/** Created by bulat.fattahov 2013
  * Uses for caching objects and refreshing cache by request.
  * Using example:
  *     val _application = new CachedObject(myObject, myInitFunction())
  *     ...
  *     _application().doSomething() // myInitFunction() executed here
  *     _application().doSomething() // will use cached value of myInitFunction()
  *     ...
  *     CachedObject.refresh(myObject)
  *     _application().doSomething() // myInitFunction() will executed again!
  */
class CachedObject[T](key: AnyRef, f: => T) extends (() => T)
{
	@volatile
	private var value: Option[T] = None

	CachedObject.addObserver(key, this)

	def apply(): T =
	{
		if (value.isEmpty) synchronized(value = Some(f))
		value.get
	}

	def needRefresh()
	{ value = None }
}

object CachedObject
{
	private var observers: Map[AnyRef, List[CachedObject[_]]] = Map.empty

	def addObserver(key: AnyRef, observer: CachedObject[_])
	{
		observers = observers + (key -> (observer :: observers.get(key).getOrElse(Nil)))
	}

	def removeObserver(key: AnyRef, observer: CachedObject[_])
	{
		observers = observers.get(key).map(_.filterNot(_ == observer)).map
		{lst: List[CachedObject[_]] => observers + (key -> lst)}.getOrElse(observers)
	}

	def removeKey(key: AnyRef)
	{
		observers = observers - key
	}

	def refresh(key: AnyRef)
	{
		observers(key).foreach(_.needRefresh())
	}
}