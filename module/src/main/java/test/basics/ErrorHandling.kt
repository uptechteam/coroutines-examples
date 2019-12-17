package test.basics

import kotlinx.coroutines.*
import java.lang.IllegalArgumentException
import java.lang.NumberFormatException

//Error handling
private val defaultCoroutineExceptionHandler =
    CoroutineExceptionHandler { coroutineContext, throwable ->
      println("In a coroutine exception handler, caught $throwable in context $coroutineContext")
    }

fun `test no exception handler in custom scope`() = runBlocking {
  //represents custom scope
  println("test no exception handler in custom scope")
  val launch = launch {
    println("inside launch, throwing Exception")
    throw IllegalArgumentException()
  }

  val async = async {
    println("inside async, throwing Exception")
    throw NumberFormatException()
  }

  launch.join()
  println("Joined failed job")
  async.await()
  println("Not able to reach this")
}

fun `test no exception handler in global scope`() = runBlocking {
  println("test no exception handler in global scope")
  val launch = GlobalScope.launch {
    println("inside launch, throwing Exception")
    throw IllegalArgumentException()
  }

  val async = GlobalScope.async {
    println("inside async, throwing Exception")
    throw NumberFormatException()
  }

  launch.join()
  println("Joined failed job")
  try {
    async.await()
  } catch (ex: NumberFormatException) {
    println("Caught async exception $ex")
  }
}

fun `test await in custom scope`() {
  runBlocking {
    println("test await in custom scope")
    val async = async {
      println("inside async, throwing Exception")
      throw NumberFormatException()
    }

    try {
      async.await()
    } catch (ex: NumberFormatException) {
      println("Caught an exception $ex")
    }

    println("proceed execution")
  }
}

fun `test with exception handler`() = runBlocking {
  println("test with exception handle")
  val launch = GlobalScope.launch(defaultCoroutineExceptionHandler) {
    println("Throwing IllegalArgumentException in launch")
    throw IllegalArgumentException()
  }
  val deferred = GlobalScope.async(defaultCoroutineExceptionHandler) {
    //This brings no value since exceptions are exposed to user
    println("Throwing NumberFormatException in async")
    throw NumberFormatException()
  }
  launch.join()
  deferred.await() //exception rises here and Default handler catches it
  Unit
}

fun `test inner cancellation`() = runBlocking {
  println("test inner cancellation")
  var c2: Job? = null
  val c1 = launch(Dispatchers.Default) {
    c2 = launch(Dispatchers.Default) {
      try {
        while (isActive) {
          delay(10)
          print("+")
        }
      } finally {
        print("Child is cancelled")
      }
    }

    while (isActive) {
      delay(10)
      print(".")
    }
  }

  delay(100)
  c2?.cancel()
  delay(150)
  c1.cancel()
  println()
}

fun `test cascade(outer) cancellation`() = runBlocking {
  println("test cascade(outer) cancellation")
  var c2: Job? = null
  val c1 = launch(Dispatchers.Default) {
    c2 = launch(Dispatchers.Default) {
      while (isActive) {
        delay(10)
        print("+")
      }
    }

    while (isActive) {
      delay(10)
      print(".")
    }
  }

  delay(100)
  c1.cancel()
  delay(50)
  println()
}
//+.+.+.+.+.+.+.+.

fun `test cancel two children`() = runBlocking {
  println("test cancel two children")
  val job = GlobalScope.launch(defaultCoroutineExceptionHandler) {
    launch {
      // the first child
      try {
        delay(Long.MAX_VALUE)
      } finally {
        withContext(NonCancellable) {
          println(
              "Children are cancelled, but exception is not handled until all children terminate")
          delay(100)
          println("The first child finished its non cancellable block")
        }
      }
    }
    launch {
      // the second child
      delay(10)
      println("Second child throws an ArithmeticException")
      throw ArithmeticException()
    }
  }
  job.join()
}

fun `check exception suppression`() = runBlocking {
  println("check exception suppression")
  val job = GlobalScope.launch(defaultCoroutineExceptionHandler) {
    launch {
      try {
        delay(Long.MAX_VALUE)
      } finally {
        throw ArithmeticException()
      }
    }
    launch {
      delay(100)
      throw IllegalArgumentException()
    }
    delay(Long.MAX_VALUE)
  }
  job.join()
}

fun `supervise child coroutines`() = runBlocking {
  println("supervise child coroutines")
  val job = SupervisorJob()
  CoroutineScope(coroutineContext + job + defaultCoroutineExceptionHandler).apply {
    val first = launch {
      delay(10)
      println("First throws an IllegalArgumentException")
      throw IllegalArgumentException()
    }

    val second = launch {
      try {
        println("Second goes to sleep")
        delay(Long.MAX_VALUE)
      } finally {
        println("Second cancelled")
      }
    }

    first.join()
    println("First is down")
    println("Second is sleeping")
    println("cancelling supervisor")
    job.cancel()

  }
}

fun `supervise with scope`() = runBlocking {
  println("supervise with scope")
  supervisorScope {
    val first = launch(defaultCoroutineExceptionHandler) {
      delay(10)
      println("First throws an IllegalArgumentException")
      throw IllegalArgumentException()
    }

    val second = launch(defaultCoroutineExceptionHandler) {
      try {
        println("Second goes to sleep")
        delay(Long.MAX_VALUE)
      } finally {
        println("Second cancelled")
      }
    }

    first.join()
    println("First is down")
    println("Second is sleeping")
    println("cancelling supervisor")
    this.cancel()
  }
}

fun setDefaultExceptionhandler() {
  Thread.setDefaultUncaughtExceptionHandler { t, e ->
    println("Default uncaught handler caught $e")
  }
}

fun main(args: Array<String>) {
  setDefaultExceptionhandler()
//  `test no exception handler in custom scope`()
//  `test no exception handler in global scope`()
//  `test await in custom scope`()
//  `test with exception handler`()
//  `test inner cancellation`()
//  `test cascade(outer) cancellation`()
//  `test cancel two children`()
//  `check exception suppression`()
//  `supervise child coroutines`()
//  `supervise with scope`()
}