package ru.briany.domain.form.feel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("FEEL Variable Extractor Tests")
class FeelVariableExtractorTest {
    // === Basic tests ===

    @Test
    fun `simple variable`() {
        val vars = extractVariables("salary")
        assertEquals(setOf("salary"), vars)
    }

    @Test
    fun `comparison with literal`() {
        val vars = extractVariables("salary > 50000")
        assertEquals(setOf("salary"), vars)
    }

    @Test
    fun `multiple variables with and`() {
        val vars = extractVariables("salary > 50000 and department = \"IT\"")
        assertEquals(setOf("salary", "department"), vars)
    }

    @Test
    fun `nested path`() {
        val vars = extractVariables("customer.address.city = \"Moscow\"")
        assertEquals(setOf("customer"), vars)
    }

    @Test
    fun `if then else`() {
        val vars = extractVariables("if isVIP then premiumRate else standardRate")
        assertEquals(setOf("isVIP", "premiumRate", "standardRate"), vars)
    }

    @Test
    fun `function call with variables`() {
        val vars = extractVariables("contains(name, \"test\")")
        assertEquals(setOf("name"), vars)
    }

    @Test
    fun `for loop`() {
        val vars = extractVariables("for x in items return x.price")
        assertEquals(setOf("items"), vars)
    }

    @Test
    fun `escaped variable name`() {
        val vars = extractVariables("`customer name` = \"John\"")
        assertEquals(setOf("customer name"), vars)
    }

    // === Arithmetic and operators ===

    @Test
    fun arithmetic() {
        val vars = extractVariables("a + b * c")
        assertEquals(setOf("a", "b", "c"), vars)
    }

    @Test
    fun `not operator`() {
        // FEEL's `not` requires parentheses: not(expression)
        val vars = extractVariables("not(active)")
        assertEquals(setOf("active"), vars)
    }

    @Test
    fun between() {
        // "a between minVal and maxVal" parses as (a >= minVal) and (a <= maxVal)
        val vars = extractVariables("a between minVal and maxVal")
        assertEquals(setOf("a", "minVal", "maxVal"), vars)
    }

    @Test
    fun `in list`() {
        val vars = extractVariables("status in (allowedA, allowedB)")
        assertEquals(setOf("status", "allowedA", "allowedB"), vars)
    }

    @Test
    fun `in literal list`() {
        val vars = extractVariables("""status in ("a", "b", "c")""")
        assertEquals(setOf("status"), vars)
    }

    @Test
    fun `or condition`() {
        val vars = extractVariables("status = \"active\" or status = \"pending\"")
        assertEquals(setOf("status"), vars)
    }

    @Test
    fun negation() {
        val vars = extractVariables("not(isBlocked)")
        assertEquals(setOf("isBlocked"), vars)
    }

    @Test
    fun division() {
        val vars = extractVariables("total / divisor")
        assertEquals(setOf("total", "divisor"), vars)
    }

    @Test
    fun exponentiation() {
        val vars = extractVariables("base ** exponent")
        assertEquals(setOf("base", "exponent"), vars)
    }

    @Test
    fun `unary minus`() {
        val vars = extractVariables("-amount + offset")
        assertEquals(setOf("amount", "offset"), vars)
    }

    @Test
    fun `comparison operators`() {
        val vars = extractVariables("a < b and c <= d and e >= f")
        assertEquals(setOf("a", "b", "c", "d", "e", "f"), vars)
    }

    // === Lists and filters ===

    @Test
    fun `index access`() {
        val vars = extractVariables("items[1]")
        assertEquals(setOf("items"), vars)
    }

    @Test
    fun `index access with variable`() {
        val vars = extractVariables("items[idx]")
        assertEquals(setOf("items", "idx"), vars)
    }

    @Test
    fun `filter expression with property`() {
        // In items[price > minPrice], `price` is really the current item's property and
        // `minPrice` an external variable, but without types we can't tell them apart,
        // so both get extracted.
        val vars = extractVariables("items[price > minPrice]")
        assertEquals(setOf("items", "price", "minPrice"), vars)
    }

    @Test
    fun `filter with item prefix`() {
        // `item` is implicitly local within the filter
        val vars = extractVariables("""orders[item.status = "pending"]""")
        assertEquals(setOf("orders"), vars)
    }

    @Test
    fun `list literal with variables`() {
        val vars = extractVariables("[a, b, c]")
        assertEquals(setOf("a", "b", "c"), vars)
    }

    // === Functions ===

    @Test
    fun `count function`() {
        val vars = extractVariables("count(items) > threshold")
        assertEquals(setOf("items", "threshold"), vars)
    }

    @Test
    fun `sum with path`() {
        val vars = extractVariables("sum(orders.amount)")
        assertEquals(setOf("orders"), vars)
    }

    @Test
    fun `function with multiple variable arguments`() {
        val vars = extractVariables("substring(fullName, startPos, length)")
        assertEquals(setOf("fullName", "startPos", "length"), vars)
    }

    @Test
    fun `starts with`() {
        val vars = extractVariables("starts with(name, prefix)")
        assertEquals(setOf("name", "prefix"), vars)
    }

    @Test
    fun `substring comparison`() {
        val vars = extractVariables("substring(code, 1, 3) = region")
        assertEquals(setOf("code", "region"), vars)
    }

    @Test
    fun `lower case`() {
        val vars = extractVariables("""lower case(status) = "active"""")
        assertEquals(setOf("status"), vars)
    }

    // === Null handling ===

    @Test
    fun `null check`() {
        val vars = extractVariables("x = null")
        assertEquals(setOf("x"), vars)
    }

    @Test
    fun `not null with fallback`() {
        val vars = extractVariables("if x != null then x.name else defaultName")
        assertEquals(setOf("x", "defaultName"), vars)
    }

    // === Date/time ===

    @Test
    fun `date comparison`() {
        val vars = extractVariables("today() > deadline")
        assertEquals(setOf("deadline"), vars)
    }

    @Test
    fun `date arithmetic`() {
        val vars = extractVariables("""date(birthDate) + duration("P18Y") < today()""")
        assertEquals(setOf("birthDate"), vars)
    }

    // === Quantifiers ===

    @Test
    fun every() {
        val vars = extractVariables("every x in scores satisfies x >= minScore")
        assertEquals(setOf("scores", "minScore"), vars)
    }

    @Test
    fun some() {
        val vars = extractVariables("some order in orders satisfies order.total > limit")
        assertEquals(setOf("orders", "limit"), vars)
    }

    @Test
    fun `multiple iterators in for`() {
        val vars = extractVariables("for x in items, y in categories return x.name + y.label")
        assertEquals(setOf("items", "categories"), vars)
    }

    // === Context ===

    @Test
    fun `context creation`() {
        // `total` is defined in the context, becoming local
        val vars = extractVariables("{total: price * quantity, discounted: total * discount}")
        assertEquals(setOf("price", "quantity", "discount"), vars)
    }

    @Test
    fun `nested context`() {
        // `a` is defined in the context, becoming local
        val vars = extractVariables("{a: x, b: {c: a + y}}.b.c")
        assertEquals(setOf("x", "y"), vars)
    }

    @Test
    fun `context literal with variables`() {
        val vars = extractVariables("{ total: price * qty, name: productName }")
        assertEquals(setOf("price", "qty", "productName"), vars)
    }

    // === instance of ===

    @Test
    fun `instance of`() {
        val vars = extractVariables("x instance of number")
        assertEquals(setOf("x"), vars)
    }

    // === Unary tests ===

    @Test
    fun `unary less than`() {
        val vars = extractUnaryTestVariables("< threshold")
        assertEquals(setOf("threshold"), vars)
    }

    @Test
    fun `unary range`() {
        val vars = extractUnaryTestVariables("[minVal..maxVal]")
        assertEquals(setOf("minVal", "maxVal"), vars)
    }

    @Test
    fun `unary range literals`() {
        val vars = extractUnaryTestVariables("[10..50]")
        assertEquals(emptySet<String>(), vars)
    }

    @Test
    fun `unary in list`() {
        val vars = extractUnaryTestVariables(""""A", "B", "C"""")
        assertEquals(emptySet<String>(), vars)
    }

    @Test
    fun `unary not`() {
        val vars = extractUnaryTestVariables("not(excludedStatus)")
        assertEquals(setOf("excludedStatus"), vars)
    }

    // === Variables named like built-in functions (P2: Ref vs FunctionInvocation) ===

    @Test
    fun `variable named count is not suppressed`() {
        val vars = extractVariables("count > 0")
        assertEquals(setOf("count"), vars)
    }

    @Test
    fun `variable named date is not suppressed`() {
        val vars = extractVariables("date != null")
        assertEquals(setOf("date"), vars)
    }

    @Test
    fun `variable named number is not suppressed`() {
        val vars = extractVariables("number + 1")
        assertEquals(setOf("number"), vars)
    }

    @Test
    fun `variable named string is not suppressed`() {
        val vars = extractVariables("string = \"hello\"")
        assertEquals(setOf("string"), vars)
    }

    @Test
    fun `count as function still works`() {
        val vars = extractVariables("count(items) > threshold")
        assertEquals(setOf("items", "threshold"), vars)
    }

    @Test
    fun `date as function still works`() {
        val vars = extractVariables("""date(birthDate) + duration("P18Y") < today()""")
        assertEquals(setOf("birthDate"), vars)
    }

    // === Edge cases ===

    @Test
    fun `empty expression`() {
        val vars = extractVariables("")
        assertEquals(emptySet<String>(), vars)
    }

    @Test
    fun `only literal`() {
        val vars = extractVariables("42")
        assertEquals(emptySet<String>(), vars)
    }

    @Test
    fun `string literal`() {
        val vars = extractVariables(""""hello"""")
        assertEquals(emptySet<String>(), vars)
    }

    @Test
    fun `boolean constant`() {
        val vars = extractVariables("true and false")
        assertEquals(emptySet<String>(), vars)
    }

    @Test
    fun `multiple escaped names`() {
        val vars = extractVariables("`first name` + \" \" + `last name`")
        assertEquals(setOf("first name", "last name"), vars)
    }

    @Test
    fun `deeply nested`() {
        val vars = extractVariables("if a then (if b then c else d) else e")
        assertEquals(setOf("a", "b", "c", "d", "e"), vars)
    }

    @Test
    fun `deep path expression`() {
        val vars = extractVariables("order.customer.address.city.name")
        assertEquals(setOf("order"), vars)
    }

    @Test
    fun `constants only - no variables`() {
        val vars = extractVariables("1 + 2 * 3")
        assertEquals(emptySet<String>(), vars)
    }

    @Test
    fun `everything, everywhere, all at once`() {
        val vars = extractVariables(extremelyLargeFeel)
        assertEquals(setOf("ticketInfo", "taggingResults", "tagMap"), vars)
    }

    val extremelyLargeFeel: String = """
        {
          // Check if the original message contains keywords indicating a confirmation request
          isConfirmationRequest: (
            matches(ticketInfo.original_message, ".*\\bconfirm\\b.*", "i") or
            matches(ticketInfo.original_message, ".*\\backnowledge\\b.*", "i")
          ),

          // Categorize all tags, converting "Pass Information" to "Request Information" if confirmation is requested
          foundCategories: (
            for tag in taggingResults.tags
            return
              if get value(tagMap, tag) = "Pass Information" and isConfirmationRequest then
                "Request Information"
              else
                get value(tagMap, tag)
          )[item != null],

          // Create a list of tags that are categorized as a request
          requestTagsOnly: (
            for tag in taggingResults.tags
            return
              if (get value(tagMap, tag) = "Request Information") or (get value(tagMap, tag) = "Pass Information" and isConfirmationRequest) then
                tag
              else
                null
          )[item != null],

          passInfoTagsOnly: (
            for tag in taggingResults.tags
            return
              if get value(tagMap, tag) = "Pass Information" and not(isConfirmationRequest) then
                tag
              else
                null
          )[item != null],

          // Build the final result object with all boolean flags and lists of tags
          result: {
            hasEtgTag: list contains(foundCategories, "ETG question"),
            hasRequestTag: list contains(foundCategories, "Request Information"),
            hasPassInfoTag: list contains(foundCategories, "Pass Information"),
            hasBookingConfirmationTag: list contains(foundCategories, "Special Request"),
            categoryGroups: foundCategories,
            requestTags: requestTagsOnly,
            passInfoTags: passInfoTagsOnly // The new variable for pass info tags
          }
        }.result
    """
}
