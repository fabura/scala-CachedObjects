   Uses for caching objects and refreshing cache by request.
   Using example:
       val application = new CachedObject(myObject, myInitFunction())
       ...
       application().doSomething() // myInitFunction() executed here
       application().doSomething() // will use cached value of myInitFunction()
       ...
       CachedObject.refresh(myObject)
       application().doSomething() // myInitFunction() will executed again!