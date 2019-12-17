package play

/**
 * @author alexey.kutuzov
 */

fun line(name: String, fn: () -> Unit) {
    println()
    val title = "============================ $name ====================================================="

    println(title)
    fn()
    println((1..title.length).map { "=" }.joinToString(separator = ""))
    println()
}