package play.basics

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.*

fun `test different context`() = runBlocking {
  launch {
    delay(100)
    println(Thread.currentThread().name + " there")
  }

  launch(Dispatchers.Default) {
    delay(100)
    println(Thread.currentThread().name + " there")
  }

  delay(1)
  println(Thread.currentThread().name + " here")
  Thread.sleep(200)
}

fun `test simple launch`() = runBlocking {
  launch {
    delay(100)
    println("Work done!")
  }
}

suspend fun simpleSuspendFunction(): Int { //can suspend coroutine execution
  delay(100)
  print(".")
  return 1
}

fun multipleWithSuspension() = runBlocking {
  val routines = (0..100).map {
    launch(Dispatchers.Default) {
      simpleSuspendFunction()
    }
  }

  routines.forEach {
    it.join()
  }

  println()
}

fun cancellation() = runBlocking {
  val routine = launch(Dispatchers.Default) {
    while (isActive) {
      print(".")
      delay(10)
    }
  }

  delay(100)
  print("!")
  delay(50)
  routine.cancel()

  println()
}

fun multipleAsync() = runBlocking {
  val r1 = async(Dispatchers.Default) {
    delay(100)
    return@async 10
  }

  val r2 = async(Dispatchers.Default) {
    delay(100)
    return@async 11
  }

  val r3 = async(start = CoroutineStart.LAZY, context = Dispatchers.Default) {
    if (true) {
      throw RuntimeException("error catched")
    }
  }

  println("The answer is ${r1.await() + r2.await()}")
  try {
    r3.await()
  } catch (e: Exception) {
    println(e.message)
  }

//  produce {
//
//  }
}

//suspend fun produceTest(context: CoroutineContext): ReceiveChannel<Int> = suspendCoroutine {
//    Channel<Int>().apply {
//        println("HEre i am")
//        println(Thread.currentThread().name)
//        launch(Dispatchers.IO) {
//            println(Thread.currentThread().name)
////        val a = a()
////        val b = b()
//
//            channel.send(1)
//            channel.send(2)
//            channel.close()
//            println(Thread.currentThread().name)
//        }
//        println("HEre i am2")
//        println(Thread.currentThread().name)
//        channel
//    }
//}

fun jobCancel() = runBlocking {
  val job = launch {
    println("IN JOB!")
    delay(1000)
    println("JOB DONE!")
  }
  println("JOB STARTED!")
  delay(500)
  job.cancel()
  println("JOB CANCELED")
}

fun sequence(): Sequence<Int> =
    sequence {
      yieldAll(0..1)
    }
//
//fun contexts() = runBlocking(newSingleThreadContext("blocking context")) {
//    run(newSingleThreadContext("test")) {
//        delay(10)
//        println(Thread.currentThread().name + " here")
//
//        var a = "4"
//        run(newSingleThreadContext("test2")) {
//            delay(20)
//            println(Thread.currentThread().name + " here2")
//            a = "5"
//        }
//
//        println(Thread.currentThread().name + " here3 " + a)
//    }
//}

fun CoroutineScope.produceNumbers() = produce {
  repeat(10) {
    send(it + 1)
    delay(400L)
  }
}

fun CoroutineScope.map(channel: ReceiveChannel<Int>, mapper: (Int) -> Int) = produce<Int> {
  for (value in channel) send(mapper(value))
}

suspend fun numberProcessor(id: String, channel: ReceiveChannel<Int>) {
  for (msg in channel) {
    println("Processor #$id received $msg")
  }
}

suspend fun sendToChannel(channel: SendChannel<Any>, value: Any, delay: Long) {
  while (coroutineContext.isActive && !channel.isClosedForSend) {
    delay(delay)
    if (!channel.isClosedForSend)
      channel.send(value)
  }
}

fun produce() = runBlocking {
  //  val channel = produceNumbers()
//  launch { numberProcessor("1", channel) }
//  launch { numberProcessor("2", channel) }
//  launch { numberProcessor("3", channel) }

  val channel = Channel<Any>()

  launch { sendToChannel(channel, "ONE", 200L) }
  launch { sendToChannel(channel, "TWO", 400L) }
  launch { sendToChannel(channel, 1, 500L) }

  launch {
    delay(3000L)
    channel.cancel()
    yield()
  }

  for (value in channel) {
    println(value)
  }

//  producer.map {  }
//  for (value in mapper) {
//    println("$value")
//  }

  print("Done!")
}

fun simple() = runBlocking {
  val channel = Channel<Int>()

  launch(Dispatchers.Default) {
    channel.send(1)
    delay(100)
    channel.send(2)
//    channel.close()
  }

  for (a in channel) {
    print("$a ")
  }
  channel.consumeEach {
    print("$it ")
  }

  println("Consumed!")
}

suspend fun function(): ReceiveChannel<Int> {
  val channel = Channel<Int>()

  withContext(Dispatchers.Default) {
    launch {
      channel.send(1)
      delay(100)
      channel.send(2)
      channel.close()
    }
  }

  return channel
}

fun cascadeCancel12() = runBlocking {
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

//run in main thread
fun justSuspendFn() = iterator {
  var i = 0
  while (true) {
    print(Thread.currentThread().name + " ")
    yield(i++)
  }
}

fun callJustSuspend() {
  val justSuspendFn = justSuspendFn()
  for (i in (0..10)) {
    println(justSuspendFn.next())
  }
}

fun closeInnerCoroutineWhenUpperDone() = runBlocking {
  val ab = AtomicBoolean(true)
  launch(newFixedThreadPoolContext(8, "multi")) {
    repeat(2) { id ->
      launch(Dispatchers.Default) {
        Thread {
          while (ab.get()) {
            print("($id ${Thread.currentThread().name} ${isActive})")
            Thread.sleep(10)
          }
        }.start()

        while (true) {
          print("L")
          delay(1)
        }
      }
    }

    delay(100)
  }

  delay(200)
  println()
  ab.set(false)
}
//
//fun closeInnerCoroutineWhenUpperDoneRunInsteadOfLaunch() = runBlocking(newFixedThreadPoolContext(8, "multi")) {
//    println(Thread.currentThread().name)
//    //run is blocking
//    run(context) {
//        println(Thread.currentThread().name)
//        val jobs = List(2) { id ->
//            launch(context) {
//                repeat(10) {
//                    print("($id ${Thread.currentThread().name})")
//                    delay(1)
//                }
//            }
//        }
//
//        jobs.forEach {
//            it.join()
//        }
//    }
//
//    println()
//}

suspend inline fun <T> T.inScopeOf(scope: CoroutineScope, block: T.() -> Unit) {
  scope.apply {
    block.invoke(this@inScopeOf)
  }
}

fun broadCastChannel() = runBlocking {
  val channel = BroadcastChannel<Int>(Channel.CONFLATED)

  channel.send(0)

  launch {
    channel.openSubscription()
        .consumeEach {
          println("First: $it")
        }

  }
  channel.send(1)
  delay(1000)


  launch {
    channel.openSubscription()
        .consumeEach {
          println("Second: $it")
        }
  }


  delay(1000)
  channel.send(2)
  channel.send(3)
}
//
//suspend fun <T> CoroutineScope.throttleLast(block: (T) -> Unit, time: Long): SendChannel<T> {
//
//
//  return actor(capacity = Channel.UNLIMITED) {
//    var value: T? = null
//    var job : Job
//
//    timer("throttle", period = time) {
//      println("Timer tick ")
//      if (value != null) {
//        if (job.isActive) {
//          job.cancel()
//        }
//        val v = value
//        job = launch {
//          println("Lunched")
//          block(v!!)
//        }
//
//        job.start()
//
//        value = null
//
//      }
//    }
//
//    consumeEach {
//      value = it
//    }
//  }
//}
//
//suspend fun sumSuspend() : Int {
//    return suspendCoroutine<Int> {
//        val a = 1
//        val b = 3
//        println(Thread.currentThread().name)
//        return a + b
//    }
//}

suspend fun sequenceInt() = kotlin.sequences.sequence<Int> {
  yield(1)
  var i = 1
  while (true) {
    yield(i++)
  }
}

suspend fun doOutOf(): Int {
  delay(5000)
  return 2
}

fun CoroutineScope.doIn(): Deferred<Int> {
  return async {
    delay(5000)
    1
  }
}

fun main(args: Array<String>) = runBlocking {
  //  cancellation()
//  `test different context`()
  `test simple launch`()
//  multipleAsync()
//  print(sequenceInt().take(100).joinToString())
//  launch {
//    println(doOutOf())
//  }
//
//  println(doIn().await())

//  simple()
//  produce()
//  launch {
//    delay(1000)
//    val a = async(Dispatchers.IO) { sumSuspend() }
//    println(a.await())
//  }
//  println("fukc")
  Unit
}
