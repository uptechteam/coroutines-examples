package play.channels

import play.line
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.selects.select

fun simple() = runBlocking {
  println()
  val channel = Channel<Int>()

  launch(Dispatchers.Default) {
    for (i in (0..10)) {
      channel.send(i)
      delay(10)
    }

    channel.close()
  }

  channel.consumeEach {
    print("$it ")
  }

  println()
}

fun produces() = runBlocking {
  val produce = produce<Int>(Dispatchers.Default) {
    repeat(10) {
      send(it)
    }
  }

  produce.consumeEach {
    print("$it ")
  }

  println()
}

fun multipleConsumer() = runBlocking {
  //capacity - buffer size (otherwise they behave like exchanger)
  val produce = produce<Int>(Dispatchers.Default, capacity = 2) {
    repeat(10) {
      print("(SEND)")
      send(it)
    }
  }

  launch(newFixedThreadPoolContext(8, "multi")) {
    val jobs = mutableListOf<Job>()
    repeat(2) { id ->
      jobs.add(launch(Dispatchers.Default) {
        produce.consumeEach {
          print("($id ${Thread.currentThread().name} ${it})")
        }
      })
    }

    jobs.forEach { it.join() }
  }

  delay(1000)
  produce.cancel()
  println()
}

fun multiselect() = runBlocking {
  val ch1 = Channel<Int>()
  val ch2 = Channel<String>()

  launch(Dispatchers.Default) {
    while (!ch1.isClosedForReceive || !ch2.isClosedForReceive) {
      select<Unit> {
        ch1.onReceiveOrNull {
          if (it != null) {
            print(it)
          }
        }
        ch2.onReceiveOrNull {
          if (it != null) {
            print(it)
          }
        }
      }
    }
  }

  ch1.send(1)
  ch1.send(2)
  ch2.send("3")
  ch2.send("4")
  ch1.send(5)
  ch1.close()
  ch2.close()

  delay(100)
  println()
}

fun main(args: Array<String>) {
  line("simple", ::simple)
  line("produces", ::produces)
  line("multipleConsumer", ::multipleConsumer)
  line("multiselect", ::multiselect)

  Thread.sleep(100)
}