package test.basics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

//delay(1000) cant' use delay here since SequenceScope is synchronous
fun sequence(range: IntRange = 1..5) = sequence<Int> {
  range.forEach {
    Thread.sleep(200)
    yield(it)
  }
}

fun flow(range: IntRange = 1..5) = flow<Int> {
  println("Inside flow builder")
  range.forEach {
    delay(200)
    emit(it)
  }
}

fun `test flow not blocking`() = runBlocking {
  println("test flow not blocking")
  launch {
    flow().collect { println(it) }
  }

  launch {
    (11..15).forEach {
      delay(100)
      println(it)
    }
  }
}

fun `test sequence blocks thread`() = runBlocking {
  println("test sequence blocks thread")
  launch {
    sequence().forEach { println(it) }
  }

  launch {
    (11..15).forEach {
      delay(100)
      println(it)
    }
  }
}

fun `test flows are cold`() = runBlocking {
  println("test flows are cold")
  val intFlow = flow(1..2)
  println("collecting")
  intFlow.collect {
    println(it)
  }
  println("collecting one more time")
  intFlow.collect {
    println(it)
  }
}

fun `test cancel flow`() = runBlocking {
  println("test cancel flow")
  withTimeoutOrNull(500) {
    val flow = flow()
    flow.collect {
      println(it)
    }
  }
  println("cancelled")
}

fun `test flow builders`() = runBlocking {
  println("test flow builders")
  println("asFlow")
  listOf(1, 2, 3, 4, 5)
      .asFlow()
      .collect {
        println(it)
      }
  println("flowOf")
  flowOf(1, 2, 3, 4, 5, 6)
      .collect {
        println(it)
      }
}

fun `test flow map`() = runBlocking {
  println("test flow map")
  flow()
      .map {
        println("In map")
        it * 2
      }
      .collect {
        println(it)
      }
}

fun `test flow reduce`() = runBlocking {
  println("test flow reduce")
  println(flow().reduce { accumulator, value -> accumulator * value })
}

fun `test flow operators`() = runBlocking {
  flow(1..10)
      .filter { it % 2 == 0 }
      .map { it + 2 }
      .collectIndexed { index, value ->
        println("Collected $value at index $index")
      }
}

suspend fun factorial(n: Int): Int = when {
  n == 0 -> 1
  n > 0 -> flow(1..n).reduce { accumulator, value -> accumulator * value }
  else -> 0
}

fun `test factorial`() = runBlocking {
  println("test factorial")
  val five = async { factorial(5) }
  val six = async { factorial(6) }
  val seven = async { factorial(7) }

  println("${five.await()} ${six.await()} ${seven.await()}")
}

fun `test flow collectLatest operator`() = runBlocking {
  println("test flow collectLatest operator")
  flow()
      .collectLatest {
        println("collected $it")
        delay(1000)
        println("Finished work")
      }
}

fun `test flow flatMapLatest operator`() = runBlocking {
  println("test flow flatMapLatest operator")
  flow(1..2)
      .flatMapLatest {
        println("In flatmapLatest")
        delay(200)
        flowOf(10, 12)
      }
      .collect {
        println("collected $it")
      }
}

fun `test flow flatMapMerge operator`() = runBlocking {
  println("test flow flatMapMerge operator")
  flow()
      .flatMapMerge {
        //regular flatMap
        println("In flatmapMerge")
        flow {
          val randomInt = Random(it).nextInt(1, 3)
          (1..randomInt).forEach {
            delay(100L * randomInt)
            emit(it)
          }
        }
      }
      .collectIndexed { index, value ->
        println("collected $value at index $index")
      }
}

fun `test flow flatMapConcat operator`() = runBlocking {
  println("test flow flatMapConcat operator")
  flow()
      .flatMapConcat {
        println("In flatmapConcat")
        flow {
          val randomInt = Random(it).nextInt(1, 3)
          (1..randomInt).forEach {
            delay(100L * randomInt)
            emit(it)
          }
        }
      }
      .collectIndexed { index, value ->
        println("collected $value at index $index")
      }
}

fun `test flow take operator`() = runBlocking {
  println("test flow take operator")
  flow<Int> {
    try {
      emitAll(flow()) //take cancels flow with CancellationException
    } catch (ex: CancellationException) {
      println("Took 2")
    }
  }
      .take(2)
      .collect {
        println("$it")
      }
}

fun `test flow transform operator`() = runBlocking {
  println("test flow transform operator")
  flow(1..2)
      .transform { int ->
        println("transforming $int")
        emit(2)
      }
      .collect {
        println("collected $it")
      }
}

fun `test flow change context`() = runBlocking {
  println("test flow change context")
  flow {
    kotlinx.coroutines.withContext(Dispatchers.Default) {
      emit(1)
      emit(2)
    }
  }.collect {
    println(it)
  }
}

fun `test flow flowOn operator`() = runBlocking {
  println("test flow flowOn operator")
  flow {
    println(Thread.currentThread().name)
    emit(1)
    emit(2)
  }
      .flowOn(Dispatchers.Default)
      .collect {
        println("collected $it on " + Thread.currentThread().name)
      }
}

fun `test flow buffer`() = runBlocking {
  println("test flow buffer")
  val start = System.currentTimeMillis()
  flow()
      .buffer()
      .collect {
        delay(300)
        println("collected $it")
      }

  println("Time: ${System.currentTimeMillis() - start} ms")
}

fun `test flow conflated buffer`() = runBlocking {
  println("test flow conflated buffer")
  flow()
      //.conflate()
      .buffer(Channel.CONFLATED) //same as conflate() operator
      .collect {
        delay(500)
        println("collected $it")
      }
}

fun `test flow zip unequal size`() = runBlocking {
  println("test flow zip unequal size")
  val other = flow(100..106) //7 values
  flow(1..3) //3 values
      .zip(other) { f, s -> f + s }
      .collect {
        println(it)
      }

  println("DONE!")
}

fun `test flow zip equal size`() = runBlocking {
  println("test flow zip equal size")
  val other = flow(100..102) //3 values
  flow(1..3) //3 values
      .zip(other) { f, s -> f + s }
      .collect {
        println(it)
      }

  println("DONE!")
}

fun `test flow combine`() = runBlocking {
  println("test flow combine") //combines latest emitted values
  val other = flowOf(100, 101, 102).onEach { delay(200) }
  flowOf(1, 2).onEach { delay(100) }
      .combine(other) { f, s ->
        println("first $f")
        println("second $s")
        f + s
      }
      .collect {
        println(it)
      }
}

fun `test flow exception`() = runBlocking {
  val flow = flow()
      .onEach { check(it <= 3) }

  try {
    flow
        .collect {
          println(it)
        }
  } catch (ex: Throwable) {
    println("Exception $ex")
  }
}

fun `test flow exception catch`() = runBlocking {
  println("test flow exception catch")
  flow(1..5)
      .map { check(it <= 3) }
      .catch { println("Exception $it") }
      .collect {
        println(it)
      }
}

fun `test flow exception catch declarative`() = runBlocking {
  println("test flow exception catch declarative")
  flow(1..5)
      .onEach {
        check(it <= 3)
        println("$it")
      }
      .catch { println("Exception $it") }
      .collect()
}

fun `test flow use finally on completion`() = runBlocking {
  println("test flow use finally on completion")
  try {
    flow()
        .collect {
          println(it)
        }
  } finally {
    println("done")
  }
}

fun `test flow use onCompletion`() = runBlocking {
  println("test flow use onCompletion")
  flow()
      .onCompletion { println("done") }
      .collect {
        println(it)
      }
}

fun `test flow launch in`() = runBlocking {
  println("test flow launch in")
  flow()
      .onEach { println(it) }
      .launchIn(this)

  println("Done")
}

fun main(args: Array<String>) {
//  `test flow not blocking`()
//  `test sequence blocks thread`()
//  `test flows are cold`()
//  `test cancel flow`()
//  `test flow builders`()
//  `test flow map`()
//  `test flow reduce`()
//  `test factorial`()
//  `test flow collectLatest operator`()
//  `test flow flatMapLatest operator`()
//  `test flow flatMapMerge operator`()
//  `test flow flatMapConcat operator`()
//  `test flow take operator`()
//  `test flow transform operator`()
//  `test flow change context`()
//  `test flow flowOn operator`()
//  `test flow buffer`()
//  `test flow conflated buffer`()
//  `test flow zip unequal size`()
//  `test flow zip equal size`()
//  `test flow combine`()
//  `test flow exception`()
//  `test flow exception catch`()
//  `test flow exception catch declarative`()
//  `test flow use finally on completion`()
//  `test flow use onCompletion`()
//  `test flow launch in`()
  Unit
}