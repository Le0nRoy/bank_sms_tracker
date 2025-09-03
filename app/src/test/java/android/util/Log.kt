package android.util

// Mock `android.util.Log` during unit tests
// This class will be called instead of real `android.util.Log` during tests only
object Log {
    @JvmStatic
    fun d(tag: String?, msg: String?): Int {
        println("DEBUG: [$tag] $msg")
        return 0
    }

    @JvmStatic
    fun i(tag: String?, msg: String?): Int {
        println("INFO: [$tag] $msg")
        return 0
    }

    @JvmStatic
    fun w(tag: String?, msg: String?): Int {
        println("WARN: [$tag] $msg")
        return 0
    }

    @JvmStatic
    fun e(tag: String?, msg: String?, tr: Throwable? = null): Int {
        println("ERROR: [$tag] $msg ${tr?.message}")
        return 0
    }
}
