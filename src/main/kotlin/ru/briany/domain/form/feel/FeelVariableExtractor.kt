package ru.briany.domain.form.feel

import org.camunda.feel.impl.parser.FeelParser
import org.camunda.feel.syntaxtree.Addition
import org.camunda.feel.syntaxtree.ArithmeticNegation
import org.camunda.feel.syntaxtree.AtLeastOne
import org.camunda.feel.syntaxtree.Conjunction
import org.camunda.feel.syntaxtree.ConstBool
import org.camunda.feel.syntaxtree.ConstContext
import org.camunda.feel.syntaxtree.ConstDate
import org.camunda.feel.syntaxtree.ConstDateTime
import org.camunda.feel.syntaxtree.ConstDayTimeDuration
import org.camunda.feel.syntaxtree.ConstList
import org.camunda.feel.syntaxtree.ConstLocalDateTime
import org.camunda.feel.syntaxtree.ConstLocalTime
import org.camunda.feel.syntaxtree.ConstNumber
import org.camunda.feel.syntaxtree.ConstRange
import org.camunda.feel.syntaxtree.ConstString
import org.camunda.feel.syntaxtree.ConstTime
import org.camunda.feel.syntaxtree.ConstYearMonthDuration
import org.camunda.feel.syntaxtree.Disjunction
import org.camunda.feel.syntaxtree.Division
import org.camunda.feel.syntaxtree.Equal
import org.camunda.feel.syntaxtree.EveryItem
import org.camunda.feel.syntaxtree.Exp
import org.camunda.feel.syntaxtree.Exponentiation
import org.camunda.feel.syntaxtree.Filter
import org.camunda.feel.syntaxtree.For
import org.camunda.feel.syntaxtree.FunctionInvocation
import org.camunda.feel.syntaxtree.GreaterOrEqual
import org.camunda.feel.syntaxtree.GreaterThan
import org.camunda.feel.syntaxtree.If
import org.camunda.feel.syntaxtree.In
import org.camunda.feel.syntaxtree.InputEqualTo
import org.camunda.feel.syntaxtree.InputGreaterOrEqual
import org.camunda.feel.syntaxtree.InputGreaterThan
import org.camunda.feel.syntaxtree.InputInRange
import org.camunda.feel.syntaxtree.InputLessOrEqual
import org.camunda.feel.syntaxtree.InputLessThan
import org.camunda.feel.syntaxtree.InstanceOf
import org.camunda.feel.syntaxtree.LessOrEqual
import org.camunda.feel.syntaxtree.LessThan
import org.camunda.feel.syntaxtree.Multiplication
import org.camunda.feel.syntaxtree.NamedFunctionParameters
import org.camunda.feel.syntaxtree.Not
import org.camunda.feel.syntaxtree.PathExpression
import org.camunda.feel.syntaxtree.PositionalFunctionParameters
import org.camunda.feel.syntaxtree.Ref
import org.camunda.feel.syntaxtree.SomeItem
import org.camunda.feel.syntaxtree.Subtraction
import org.camunda.feel.syntaxtree.UnaryTestExpression
import scala.Tuple2

/**
 * Extracts external variable names from a FEEL expression by walking the AST produced
 * by feel-scala's internal parser.
 *
 * Supports variables, comparisons, boolean ops, path expressions (root only),
 * if-then-else, function calls, for/some/every loops, escaped names, `in`, and unary
 * tests. Local names are excluded: loop iterators, function params, and context
 * entries already defined in the same context.
 */
object FeelVariableExtractor {
    /**
     * Extracts external variable names from a FEEL expression.
     *
     * @param expression FEEL expression
     * @return variable names, locals excluded
     * @throws IllegalArgumentException if the expression is invalid
     */
    fun extractVariables(expression: String): Set<String> {
        if (expression.isBlank()) {
            return emptySet()
        }

        val parseResult = FeelParser.parseExpression(expression)
        require(parseResult.isSuccess) { "Failed to parse FEEL expression: $expression" }

        val ast = parseResult.get().value()
        return collectVariables(ast, emptySet())
    }

    /**
     * Extracts external variable names from a FEEL unary test expression.
     *
     * @param expression FEEL unary test expression, e.g. "< threshold", "[1..10]"
     * @return variable names, locals excluded
     * @throws IllegalArgumentException if the expression is invalid
     */
    fun extractUnaryTestVariables(expression: String): Set<String> {
        if (expression.isBlank()) {
            return emptySet()
        }

        val parseResult = FeelParser.parseUnaryTests(expression)
        require(parseResult.isSuccess) { "Failed to parse FEEL unary test: $expression" }

        val ast = parseResult.get().value()
        return collectVariables(ast, emptySet())
    }
}

// Top-level convenience wrapper, avoids referencing the object directly
fun extractVariables(expression: String): Set<String> = FeelVariableExtractor.extractVariables(expression)

// Top-level convenience wrapper for unary tests
fun extractUnaryTestVariables(expression: String): Set<String> = FeelVariableExtractor.extractUnaryTestVariables(expression)

/**
 * Recursively walks the AST, tracking local variables in scope.
 *
 * Split into groups to keep each `when` within complexity limits; a node type matches
 * at most one group, and unmatched groups fall through via `?:`.
 *
 * @param exp current AST node
 * @param localVars local variables visible at this point
 * @return external variable names found
 */
private fun collectVariables(
    exp: Exp,
    localVars: Set<String>,
): Set<String> =
    collectFromStructural(exp, localVars)
        ?: collectFromArithmetic(exp, localVars)
        ?: collectFromComparisonsAndBooleans(exp, localVars)
        ?: collectFromUnaryTestNodes(exp, localVars)
        ?: collectFromCollectionsAndCalls(exp, localVars)
        ?: emptySet()

private fun collectFromStructural(
    exp: Exp,
    localVars: Set<String>,
): Set<String>? =
    when (exp) {
        // Variable reference; built-ins arrive as FunctionInvocation instead
        is Ref -> {
            val rootName = scalaListToKotlin(exp.names()).firstOrNull()
            if (rootName != null && rootName !in localVars) {
                setOf(rootName)
            } else {
                emptySet()
            }
        }

        // a.b.c - keep only the root
        is PathExpression -> {
            collectVariables(exp.path(), localVars)
        }

        // Loop iterator becomes local to the body
        is For -> {
            collectFromIterators(scalaListToKotlin(exp.iterators()), exp.exp(), localVars)
        }

        is SomeItem -> {
            collectFromIterators(scalaListToKotlin(exp.iterators()), exp.condition(), localVars)
        }

        is EveryItem -> {
            collectFromIterators(scalaListToKotlin(exp.iterators()), exp.condition(), localVars)
        }

        is If -> {
            collectVariables(exp.condition(), localVars) +
                collectVariables(exp.statement(), localVars) +
                collectVariables(exp.elseStatement(), localVars)
        }

        // items[status = "active"] or items[1]; `item` is implicitly local inside the filter
        is Filter -> {
            collectVariables(exp.list(), localVars) +
                collectVariables(exp.filter(), localVars + "item")
        }

        // { a: 1, b: x + 1 } - each entry becomes local for the ones after it
        is ConstContext -> {
            collectFromContextEntries(scalaListToKotlin(exp.entries()), localVars)
        }

        is InstanceOf -> {
            collectVariables(exp.x(), localVars)
        }

        else -> {
            null
        }
    }

private fun collectFromArithmetic(
    exp: Exp,
    localVars: Set<String>,
): Set<String>? =
    when (exp) {
        is Addition -> collectBinary(exp.x(), exp.y(), localVars)
        is Subtraction -> collectBinary(exp.x(), exp.y(), localVars)
        is Multiplication -> collectBinary(exp.x(), exp.y(), localVars)
        is Division -> collectBinary(exp.x(), exp.y(), localVars)
        is Exponentiation -> collectBinary(exp.x(), exp.y(), localVars)
        is ArithmeticNegation -> collectVariables(exp.x(), localVars)
        else -> null
    }

private fun collectFromComparisonsAndBooleans(
    exp: Exp,
    localVars: Set<String>,
): Set<String>? =
    when (exp) {
        is Equal -> collectBinary(exp.x(), exp.y(), localVars)

        is LessThan -> collectBinary(exp.x(), exp.y(), localVars)

        is LessOrEqual -> collectBinary(exp.x(), exp.y(), localVars)

        is GreaterThan -> collectBinary(exp.x(), exp.y(), localVars)

        is GreaterOrEqual -> collectBinary(exp.x(), exp.y(), localVars)

        is Conjunction -> collectBinary(exp.x(), exp.y(), localVars)

        is Disjunction -> collectBinary(exp.x(), exp.y(), localVars)

        is Not -> collectVariables(exp.x(), localVars)

        // `in`: status in (allowedA, allowedB)
        is In -> collectBinary(exp.x(), exp.test(), localVars)

        else -> null
    }

private fun collectFromUnaryTestNodes(
    exp: Exp,
    localVars: Set<String>,
): Set<String>? =
    when (exp) {
        is UnaryTestExpression -> collectVariables(exp.exp(), localVars)

        // List of unary tests, e.g. "A", "B", "C"
        is AtLeastOne -> scalaListToKotlin(exp.xs()).flatMap { collectVariables(it, localVars) }.toSet()

        // Input comparisons (unary tests)
        is InputLessThan -> collectVariables(exp.x(), localVars)

        is InputLessOrEqual -> collectVariables(exp.x(), localVars)

        is InputGreaterThan -> collectVariables(exp.x(), localVars)

        is InputGreaterOrEqual -> collectVariables(exp.x(), localVars)

        is InputEqualTo -> collectVariables(exp.x(), localVars)

        is InputInRange -> collectBinary(exp.range().start().value(), exp.range().end().value(), localVars)

        else -> null
    }

private fun collectFromCollectionsAndCalls(
    exp: Exp,
    localVars: Set<String>,
): Set<String>? =
    when (exp) {
        is FunctionInvocation -> collectFunctionParams(exp.params(), localVars)

        // [a, b, c]
        is ConstList -> scalaListToKotlin(exp.items()).flatMap { collectVariables(it, localVars) }.toSet()

        // [a..b]
        is ConstRange -> collectBinary(exp.start().value(), exp.end().value(), localVars)

        // Constants contain no variables
        is ConstNumber, is ConstBool, is ConstString, is ConstDate, is ConstTime,
        is ConstLocalTime, is ConstDateTime, is ConstLocalDateTime,
        is ConstYearMonthDuration, is ConstDayTimeDuration,
        -> emptySet()

        // Unknown node, or a singleton constant such as ConstNull / ConstInputValue
        else -> emptySet()
    }

private fun collectBinary(
    x: Exp,
    y: Exp,
    localVars: Set<String>,
): Set<String> = collectVariables(x, localVars) + collectVariables(y, localVars)

// Iterators accumulate local vars sequentially; the body sees all of them
private fun collectFromIterators(
    iterators: List<Tuple2<String, Exp>>,
    body: Exp,
    localVars: Set<String>,
): Set<String> {
    var result = emptySet<String>()
    var currentLocalVars = localVars
    for (iterator in iterators) {
        result = result + collectVariables(iterator._2(), currentLocalVars)
        currentLocalVars = currentLocalVars + iterator._1()
    }
    return result + collectVariables(body, currentLocalVars)
}

// Each context entry becomes local for the following ones
private fun collectFromContextEntries(
    entries: List<Tuple2<String, Exp>>,
    localVars: Set<String>,
): Set<String> {
    var result = emptySet<String>()
    var currentLocalVars = localVars
    for (entry in entries) {
        result = result + collectVariables(entry._2(), currentLocalVars)
        currentLocalVars = currentLocalVars + entry._1()
    }
    return result
}

private fun collectFunctionParams(
    params: Any?,
    localVars: Set<String>,
): Set<String> =
    when (params) {
        is PositionalFunctionParameters -> {
            scalaListToKotlin(params.params()).flatMap { collectVariables(it, localVars) }.toSet()
        }

        is NamedFunctionParameters -> {
            scalaMapToKotlin(params.params()).values.flatMap { collectVariables(it, localVars) }.toSet()
        }

        else -> {
            emptySet()
        }
    }

// Converts a Scala List to a Kotlin List
@Suppress("UNCHECKED_CAST")
private fun <T> scalaListToKotlin(scalaList: scala.collection.immutable.List<T>): List<T> {
    val result = mutableListOf<T>()
    var current: scala.collection.immutable.List<T> = scalaList
    while (!current.isEmpty) {
        result.add(current.head())
        current = current.tail() as scala.collection.immutable.List<T>
    }
    return result
}

// Converts a Scala Map to a Kotlin Map
@Suppress("UNCHECKED_CAST")
private fun <K, V> scalaMapToKotlin(scalaMap: scala.collection.immutable.Map<K, V>): Map<K, V> {
    val result = mutableMapOf<K, V>()
    val iterator = scalaMap.iterator()
    while (iterator.hasNext()) {
        val tuple = iterator.next()
        result[tuple._1()] = tuple._2()
    }
    return result
}
