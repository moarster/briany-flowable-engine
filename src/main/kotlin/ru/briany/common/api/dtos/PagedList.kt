package ru.briany.common.api.dtos

import org.springframework.data.domain.Pageable

interface PagedList<T> {
    val data: List<T>
    val page: Int
    val pageSize: Int
    val totalElements: Int
    val totalPages: Int
    val hasNext: Boolean
    val hasPrevious: Boolean

    companion object {
        fun <T, P : PagedList<T>> buildPage(
            data: List<T>,
            total: Long,
            pageable: Pageable,
            factory: (List<T>, Int, Int, Int, Int, Boolean, Boolean) -> P,
        ): P {
            val totalPages =
                when (pageable.pageSize) {
                    0 -> {
                        1
                    }

                    else -> {
                        ((total + pageable.pageSize - 1) / pageable.pageSize).toInt()
                    }
                }
            return factory(
                data,
                pageable.pageNumber,
                pageable.pageSize,
                total.toInt(),
                totalPages,
                pageable.pageNumber < totalPages - 1,
                pageable.pageNumber > 0,
            )
        }

        fun <T, P : PagedList<T>> emptyPage(
            pageable: Pageable,
            factory: (List<T>, Int, Int, Int, Int, Boolean, Boolean) -> P,
        ): P = buildPage(emptyList(), 0L, pageable, factory)
    }
}
