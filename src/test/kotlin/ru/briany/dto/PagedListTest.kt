package ru.briany.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import ru.briany.common.api.dtos.PagedList

class PagedListTest {
    private data class TestPage(
        override val data: List<String>,
        override val page: Int,
        override val pageSize: Int,
        override val totalElements: Int,
        override val totalPages: Int,
        override val hasNext: Boolean,
        override val hasPrevious: Boolean,
    ) : PagedList<String>

    @Test
    fun `buildPage - single page`() {
        val result = PagedList.buildPage(listOf("a", "b"), 2L, PageRequest.of(0, 10), ::TestPage)

        assertEquals(listOf("a", "b"), result.data)
        assertEquals(0, result.page)
        assertEquals(10, result.pageSize)
        assertEquals(2, result.totalElements)
        assertEquals(1, result.totalPages)
        assertFalse(result.hasNext)
        assertFalse(result.hasPrevious)
    }

    @Test
    fun `buildPage - first page of many`() {
        val result = PagedList.buildPage(listOf("a"), 25L, PageRequest.of(0, 10), ::TestPage)

        assertEquals(0, result.page)
        assertEquals(3, result.totalPages)
        assertTrue(result.hasNext)
        assertFalse(result.hasPrevious)
    }

    @Test
    fun `buildPage - middle page`() {
        val result = PagedList.buildPage(listOf("a"), 25L, PageRequest.of(1, 10), ::TestPage)

        assertEquals(1, result.page)
        assertTrue(result.hasNext)
        assertTrue(result.hasPrevious)
    }

    @Test
    fun `buildPage - last page`() {
        val result = PagedList.buildPage(listOf("a"), 25L, PageRequest.of(2, 10), ::TestPage)

        assertEquals(2, result.page)
        assertFalse(result.hasNext)
        assertTrue(result.hasPrevious)
    }

    @Test
    fun `buildPage - zero total`() {
        val result = PagedList.buildPage(emptyList(), 0L, PageRequest.of(0, 10), ::TestPage)

        assertEquals(0, result.totalElements)
        assertEquals(0, result.totalPages)
        assertFalse(result.hasNext)
        assertFalse(result.hasPrevious)
    }

    @Test
    fun `buildPage - total exactly divisible by page size`() {
        val result = PagedList.buildPage(listOf("a"), 20L, PageRequest.of(0, 10), ::TestPage)

        assertEquals(2, result.totalPages)
    }

    @Test
    fun `emptyPage - returns empty data with correct metadata`() {
        val result = PagedList.emptyPage<String, TestPage>(PageRequest.of(0, 10), ::TestPage)

        assertTrue(result.data.isEmpty())
        assertEquals(0, result.totalElements)
        assertEquals(0, result.totalPages)
        assertEquals(0, result.page)
        assertEquals(10, result.pageSize)
        assertFalse(result.hasNext)
        assertFalse(result.hasPrevious)
    }

    @Test
    fun `buildPage - pageSize zero yields totalPages 1`() {
        val result = PagedList.buildPage(emptyList(), 5L, PageRequest.of(0, 1), ::TestPage)
        // With pageSize=1 and total=5, we get 5 pages
        assertEquals(5, result.totalPages)
    }
}
