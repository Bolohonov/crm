package com.crm.task.repository;

import com.crm.task.entity.TaskComment;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskCommentRepository extends CrudRepository<TaskComment, UUID> {

    @Query("SELECT * FROM task_comments WHERE task_id = :taskId ORDER BY created_at ASC")
    List<TaskComment> findByTaskId(UUID taskId);

    @Modifying
    @Query("DELETE FROM task_comments WHERE id = :id AND author_id = :authorId")
    void deleteByIdAndAuthorId(UUID id, UUID authorId);
}
