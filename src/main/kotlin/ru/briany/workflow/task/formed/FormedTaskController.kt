package ru.briany.workflow.task.formed

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class FormedTaskController(
    private val formedTaskService: FormedTaskService,
) {
    @GetMapping("/api/task/{taskId}")
    fun getFormedTask(
        @PathVariable taskId: String,
    ) = formedTaskService.getFormedTask(taskId)
}
