package mihon.kit.updates

/**
 * Comparison operator types supported by Mihon's smart library search query engine.
 */
enum class ComparisonOperator {
    /** Exact equality (`=`). */
    EQUALS,

    /** Strict greater-than (`>`). */
    GREATER_THAN,

    /** Greater-than or equal-to (`>=`). */
    GREATER_THAN_OR_EQUAL,

    /** Strict less-than (`<`). */
    LESS_THAN,

    /** Less-than or equal-to (`<=`). */
    LESS_THAN_OR_EQUAL,
}

/**
 * Parses and evaluates Mihon's smart update search filters:
 * - `fetchinterval:` or `fi:` (e.g. `fi>7`, `fi<=14`, `fetchinterval=30`)
 * - `nextupdate:` or `nu:` (e.g. `nu<3`, `nextupdate>0`) where number represents days until next expected fetch.
 */
object LibraryUpdateFilterParser {

    private val FI_REGEX = Regex("""(?:fetchinterval|fi)\s*(>=|<=|>|<|=)\s*(-?\d+)""", RegexOption.IGNORE_CASE)
    private val NU_REGEX = Regex("""(?:nextupdate|nu)\s*(>=|<=|>|<|=)\s*(-?\d+)""", RegexOption.IGNORE_CASE)

    /**
     * Checks if a manga's fetch interval matches a search query containing `fi` / `fetchinterval`.
     * Returns true if query doesn't specify fetchinterval or if it matches the condition.
     */
    fun matchesFetchInterval(query: String, intervalDays: Int): Boolean {
        val match = FI_REGEX.find(query) ?: return true
        val operator = parseOperator(match.groupValues[1])
        val targetValue = match.groupValues[2].toIntOrNull() ?: return true
        return evaluate(intervalDays, operator, targetValue)
    }

    /**
     * Checks if a manga's next update days matches a search query containing `nu` / `nextupdate`.
     * Returns true if query doesn't specify nextupdate or if it matches the condition.
     */
    fun matchesNextUpdate(query: String, nextFetchTimestamp: Long, currentTime: Long = System.currentTimeMillis()): Boolean {
        val match = NU_REGEX.find(query) ?: return true
        val operator = parseOperator(match.groupValues[1])
        val targetValue = match.groupValues[2].toIntOrNull() ?: return true

        val remainingDays = ((nextFetchTimestamp - currentTime) / 86_400_000L).toInt()
        return evaluate(remainingDays, operator, targetValue)
    }

    private fun parseOperator(symbol: String): ComparisonOperator {
        return when (symbol) {
            ">" -> ComparisonOperator.GREATER_THAN
            ">=" -> ComparisonOperator.GREATER_THAN_OR_EQUAL
            "<" -> ComparisonOperator.LESS_THAN
            "<=" -> ComparisonOperator.LESS_THAN_OR_EQUAL
            else -> ComparisonOperator.EQUALS
        }
    }

    private fun evaluate(actual: Int, op: ComparisonOperator, target: Int): Boolean {
        return when (op) {
            ComparisonOperator.EQUALS -> actual == target
            ComparisonOperator.GREATER_THAN -> actual > target
            ComparisonOperator.GREATER_THAN_OR_EQUAL -> actual >= target
            ComparisonOperator.LESS_THAN -> actual < target
            ComparisonOperator.LESS_THAN_OR_EQUAL -> actual <= target
        }
    }
}
