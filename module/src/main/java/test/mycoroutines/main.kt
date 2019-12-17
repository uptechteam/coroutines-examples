package play.mycoroutines

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadLocalRandom
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext
import kotlin.coroutines.experimental.createCoroutine
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.experimental.intrinsics.suspendCoroutineOrReturn

/**
 *  Метод будет разбит на континуейшены на каждом suspend поинте.
 *  suspend здесь один - метод Controller.sleep.
 *  Созданием корутины и результата CompletableFuture занимается метод async.
 *
 */
fun work(name: String): CompletableFuture<String> = async {
    val sb = StringBuilder()
    sb.append("$name start on ${Thread.currentThread().name}\n\n")

    (0..3).forEach {
        val requestedSleep = if (shouldSleep()) 100 else 0

        val actualSleep = sleep(requestedSleep)

        sb.append("$name $it: $requestedSleep $actualSleep on ${Thread.currentThread().name}\n")
    }

    sb.append("\n$name done on ${Thread.currentThread().name}").toString()
}

/**
 * Метод создает корутину из лямбды и передает ее на выполнение контроллеру.
 * Самим созданием занимается котлин.
 * В континуейшен CR будет возвращен финальный результат лямбды c.
 * StartStep - континуейшен до первого саспенд поинта.
 *
 */
fun <T>async(c: suspend Controller.() -> T): CompletableFuture<T> {
    val cr = CR<T>()

    val startStep = c.createCoroutine(Controller, cr)
    Controller.append(startStep)

    return cr.result
}

/**
 * Экшен для Controller. В него заворачиваются континуейшены, чтобы быть выполнены в тредах.
 */
class Action<T>(val delay: Int, val c: (Int) -> T) : Comparable<Action<T>> {
    val created = System.currentTimeMillis()

    override fun compareTo(other: Action<T>): Int = (created + delay).compareTo(other.created + other.delay)

    fun tryRun(): Boolean {
        if (created + delay <= System.currentTimeMillis()) {
            c((System.currentTimeMillis() - created).toInt())
            return true
        }

        return false
    }
}

/**
 * Контолер для выполнения корутин.
 * Содержит треды для выполенния экшенов.
 * Метод append() отправляет "начальный" континуейшен в очередь.
 * Самое интересное - метод sleep().
 *  В слипе может быть прервано выполнение текущего континуейшена с помощью COROUTINE_SUSPENDED, и тогда для nextStep нужно вызывать resume.
 *  Либо может быть не прервано вернув результат и тогда выполнение будет продолжено сразу же.
 *  suspendCoroutineOrReturn решает будет ли корутина приостановлена или из suspend метода будет сразу возвращено значение.
 *  Результатом метода sleep() будет то что передано в resume или то что возвращено из suspendCoroutineOrReturn (если рутина не приостановлена)
 */
object Controller {
    val poolSize = 2
    val queue = PriorityBlockingQueue<Action<*>>()
    val service = Executors.newFixedThreadPool(poolSize)

    init {
        (0..poolSize).forEach{
            service.submit { loop() }
        }
    }

    fun loop() {
        while (!Thread.currentThread().isInterrupted) {
            try {
                val action = queue.poll()

                if (action != null) {
                    val wasExecuted = action.tryRun()
                    if (!wasExecuted) {
                        queue.put(action)
                    }
                }

                Thread.sleep(20)
            } catch (i: InterruptedException) {
                return
            } catch (th: Throwable) {
                th.printStackTrace()
            }
        }
    }

    fun stop() {
        service.shutdownNow()
    }

    fun Action<*>.schedule() {
        queue.add(this)
    }

    fun append(startStep: Continuation<Unit>) {
        Action(0) { startStep.resume(Unit) }.schedule()
    }

    suspend fun sleep(delay: Int): Int {
        return suspendCoroutineOrReturn { stepAfterSleep: Continuation<Int> ->

            if (delay > 0) {
                Action(delay) { stepAfterSleep.resume(it) }.schedule()

                COROUTINE_SUSPENDED
            } else {
                -1
            }
        }
    }
}

/**
 * CR - будет вызван с результатом выполнения последней рутины.
 */
class CR<T>(override val context: CoroutineContext = EmptyCoroutineContext) : Continuation<T> {
    val result = CompletableFuture<T>()

    override fun resume(value: T) {
        result.complete(value)
    }

    override fun resumeWithException(exception: Throwable) {
        result.completeExceptionally(exception)
    }

}

fun myGen( c: suspend GeneratorController.() -> Unit): GeneratorController {
    val generatorController = GeneratorController()

    //CR is not used
    val coroutine = c.createCoroutine(generatorController, CR())
    generatorController.init(coroutine)

    return generatorController
}

class GeneratorController {
    private var nextVal: Int? = null
    private var nextStep: Continuation<Unit>? = null

    fun init(c: Continuation<Unit>) {
        nextStep = c
    }

    fun consume(): Int {
        if (nextVal == null) {
            nextStep!!.resume(Unit)
        }

        try {
            return nextVal!!
        } finally {
            nextVal = null
        }
    }

    suspend fun provide(v: Int) {
        nextVal = v

        suspendCoroutineOrReturn<Unit> { step ->
            nextStep = step
            COROUTINE_SUSPENDED
        }
    }
}

fun trickyRandom() = myGen {
    provide(4)
    provide(2)
    provide(1)

    while (true) {
        provide(ThreadLocalRandom.current().nextInt(100))
    }
}

fun main(args: Array<String>) {
    val futures = (0..10).toList().map { work(('a' + it).toString()) }.toList()

    futures.forEach {
        val message = it.get()

        println("----------------------------")
        println(message)
        println("----------------------------\n")
    }

    Controller.stop()

    println("----------------------------")
    val trickyRandom = trickyRandom()
    (0..10).forEach {
        print("${trickyRandom.consume()}, ")
    }
    println("\n----------------------------")
}

/**
 * В реальности будет создан экземпляр CoroutineImpl.
 * Для него будут сгенерены пара invoke() методов и пара create() методов и метод doResume().
 *
 *
 * вызывает create, а затем doResume()
 * public final java.lang.Object invoke(play.mycoroutines.Controller, kotlin.coroutines.experimental.Continuation<? super kotlin.Unit>);
 *     Code:
 *        0: aload_1
 *        1: ldc           #111                // String $receiver
 *        3: invokestatic  #117                // Method kotlin/jvm/internal/Intrinsics.checkParameterIsNotNull:(Ljava/lang/Object;Ljava/lang/String;)V
 *        6: aload_2
 *        7: ldc           #119                // String $continuation
 *        9: invokestatic  #117                // Method kotlin/jvm/internal/Intrinsics.checkParameterIsNotNull:(Ljava/lang/Object;Ljava/lang/String;)V
 *       12: aload_0
 *       13: aload_1
 *       14: aload_2
 *       15: invokevirtual #124                // Method create:(Lplay/mycoroutines/Controller;Lkotlin/coroutines/experimental/Continuation;)Lkotlin/coroutines/experimental/Continuation;
 *       18: checkcast     #2                  // class play/mycoroutines/MainKt$simpleRoutine$1
 *       21: getstatic     #84                 // Field kotlin/Unit.INSTANCE:Lkotlin/Unit;
 *       24: aconst_null
 *       25: invokevirtual #126                // Method doResume:(Ljava/lang/Object;Ljava/lang/Throwable;)Ljava/lang/Object;
 *       28: areturn
 *
 *
 * Создает первый континуейшен и запоминает "ресивер" континуейшен.
 * public final kotlin.coroutines.experimental.Continuation<kotlin.Unit> create(play.mycoroutines.Controller, kotlin.coroutines.experimental.Continuation<? super kotlin.Unit>);
 *     Code:
 *        0: aload_1
 *        1: ldc           #111                // String $receiver
 *        3: invokestatic  #117                // Method kotlin/jvm/internal/Intrinsics.checkParameterIsNotNull:(Ljava/lang/Object;Ljava/lang/String;)V
 *        6: aload_2
 *        7: ldc           #119                // String $continuation
 *        9: invokestatic  #117                // Method kotlin/jvm/internal/Intrinsics.checkParameterIsNotNull:(Ljava/lang/Object;Ljava/lang/String;)V
 *       12: new           #2                  // class play/mycoroutines/MainKt$simpleRoutine$1
 *       15: dup
 *       16: aload_2
 *       17: invokespecial #121                // Method "<init>":(Lkotlin/coroutines/experimental/Continuation;)V
 *       20: astore_3
 *       21: aload_1
 *       22: aload_3
 *       23: aload_1
 *       24: putfield      #36                 // Field p$:Lplay/mycoroutines/Controller;
 *       27: aload_3
 *       28: areturn
 *
 *
 *
 * Диспатчинг того что делать в зависимости от поля CoroutineImpl лейбла в котором хранится айди текущего блока.
 *
 * Короче внутри обычный свитч от значения этого лейбла.
 * Точки свича - это каждый саспеншен поинт (вызов sleep() в данном случае).
 * Если слип() вернул COROUTINE_SUSPENDED, тогда мы выходим из метода, возвращая COROUTINE_SUSPENDED.
 * И продолжим когда будет вызван наш resume() (начнем сразу со следующего лейбла).
 * Если слип вернул что то другое - то продолжаем дальше (switch без брейка).
 * Когда мы достигли последнего поинта, то результат в ресивер верет уже реализация CoroutineImpl.
 *
 * Основная история коненчо в том как разбить наш метод на doResume блоки
 *
 * public final java.lang.Object doResume(java.lang.Object, java.lang.Throwable);
 *     Code:
 *        0: invokestatic  #30                 // Method kotlin/coroutines/experimental/intrinsics/IntrinsicsKt.getCOROUTINE_SUSPENDED:()Ljava/lang/Object;
 *        3: astore        6
 *        5: aload_0
 *        6: getfield      #34                 // Field kotlin/coroutines/experimental/jvm/internal/CoroutineImpl.label:I
 *        9: tableswitch   { // 0 to 3
 *                      0: 40
 *                      1: 100
 *                      2: 165
 *                      3: 225
 *                default: 270
 *           }
 *       40: aload_2
 *       41: dup
 *       42: ifnull        46
 *       45: athrow
 *       46: pop
 *       47: aload_0
 *       48: getfield      #36                 // Field p$:Lplay/mycoroutines/Controller;
 *       51: astore_3
 *       52: new           #38                 // class java/lang/StringBuilder
 *       55: dup
 *       56: invokespecial #42                 // Method java/lang/StringBuilder."<init>":()V
 *       59: astore        4
 *       61: aload         4
 *       63: ldc           #44                 // String Point 1
 *       65: invokevirtual #48                 // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
 *       68: pop
 *       69: aload_3
 *       70: iconst_1
 *       71: aload_0
 *       72: aload_0
 *       73: aload_3
 *       74: putfield      #50                 // Field L$0:Ljava/lang/Object;
 *       77: aload_0
 *       78: aload         4
 *       80: putfield      #52                 // Field L$1:Ljava/lang/Object;
 *       83: aload_0
 *       84: iconst_1
 *       85: putfield      #34                 // Field kotlin/coroutines/experimental/jvm/internal/CoroutineImpl.label:I
 *       88: invokevirtual #56                 // Method play/mycoroutines/Controller.sleep:(ILkotlin/coroutines/experimental/Continuation;)Ljava/lang/Object;
 *       91: dup
 *       92: aload         6
 *       94: if_acmpne     125
 *       97: aload         6
 *       99: areturn
 *      100: aload_0
 *      101: getfield      #52                 // Field L$1:Ljava/lang/Object;
 *      104: checkcast     #38                 // class java/lang/StringBuilder
 *      107: astore        4
 *      109: aload_0
 *      110: getfield      #50                 // Field L$0:Ljava/lang/Object;
 *      113: checkcast     #11                 // class play/mycoroutines/Controller
 *      116: astore_3
 *      117: aload_2
 *      118: dup
 *      119: ifnull        123
 *      122: athrow
 *      123: pop
 *      124: aload_1
 *      125: pop
 *      126: aload         4
 *      128: ldc           #58                 // String Point 2
 *      130: invokevirtual #48                 // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
 *      133: pop
 *      134: aload_3
 *      135: iconst_2
 *      136: aload_0
 *      137: aload_0
 *      138: aload_3
 *      139: putfield      #50                 // Field L$0:Ljava/lang/Object;
 *      142: aload_0
 *      143: aload         4
 *      145: putfield      #52                 // Field L$1:Ljava/lang/Object;
 *      148: aload_0
 *      149: iconst_2
 *      150: putfield      #34                 // Field kotlin/coroutines/experimental/jvm/internal/CoroutineImpl.label:I
 *      153: invokevirtual #56                 // Method play/mycoroutines/Controller.sleep:(ILkotlin/coroutines/experimental/Continuation;)Ljava/lang/Object;
 *      156: dup
 *      157: aload         6
 *      159: if_acmpne     190
 *      162: aload         6
 *      164: areturn
 *      165: aload_0
 *      166: getfield      #52                 // Field L$1:Ljava/lang/Object;
 *      169: checkcast     #38                 // class java/lang/StringBuilder
 *      172: astore        4
 *      174: aload_0
 *      175: getfield      #50                 // Field L$0:Ljava/lang/Object;
 *      178: checkcast     #11                 // class play/mycoroutines/Controller
 *      181: astore_3
 *      182: aload_2
 *      183: dup
 *      184: ifnull        188
 *      187: athrow
 *      188: pop
 *      189: aload_1
 *      190: pop
 *      191: aload         4
 *      193: ldc           #60                 // String Point 3
 *      195: invokevirtual #48                 // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
 *      198: pop
 *      199: aload_3
 *      200: iconst_3
 *      201: aload_0
 *      202: aload_0
 *      203: aload         4
 *      205: putfield      #50                 // Field L$0:Ljava/lang/Object;
 *      208: aload_0
 *      209: iconst_3
 *      210: putfield      #34                 // Field kotlin/coroutines/experimental/jvm/internal/CoroutineImpl.label:I
 *      213: invokevirtual #56                 // Method play/mycoroutines/Controller.sleep:(ILkotlin/coroutines/experimental/Continuation;)Ljava/lang/Object;
 *      216: dup
 *      217: aload         6
 *      219: if_acmpne     242
 *      222: aload         6
 *      224: areturn
 *      225: aload_0
 *      226: getfield      #50                 // Field L$0:Ljava/lang/Object;
 *      229: checkcast     #38                 // class java/lang/StringBuilder
 *      232: astore        4
 *      234: aload_2
 *      235: dup
 *      236: ifnull        240
 *      239: athrow
 *      240: pop
 *      241: aload_1
 *      242: pop
 *      243: aload         4
 *      245: ldc           #62                 // String Point 4
 *      247: invokevirtual #48                 // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
 *      250: pop
 *      251: aload         4
 *      253: invokevirtual #66                 // Method java/lang/StringBuilder.toString:()Ljava/lang/String;
 *      256: astore        5
 *      258: getstatic     #72                 // Field java/lang/System.out:Ljava/io/PrintStream;
 *      261: aload         5
 *      263: invokevirtual #78                 // Method java/io/PrintStream.print:(Ljava/lang/Object;)V
 *      266: getstatic     #84                 // Field kotlin/Unit.INSTANCE:Lkotlin/Unit;
 *      269: areturn
 *      270: new           #86                 // class java/lang/IllegalStateException
 *      273: dup
 *      274: ldc           #88                 // String call to 'resume' before 'invoke' with coroutine
 *      276: invokespecial #91                 // Method java/lang/IllegalStateException."<init>":(Ljava/lang/String;)V
 *      279: athrow
 *
 *
 *
 */
fun simpleRoutine(): CompletableFuture<Unit> = async {
    val result = StringBuilder()

    result.append("Point 1")

    sleep(1)

    result.append("Point 2")

    sleep(2)

    result.append("Point 3")

    sleep(3)

    result.append("Point 4")

    print(result.toString())
}

fun shouldSleep() = ThreadLocalRandom.current().nextInt(10) > 3
