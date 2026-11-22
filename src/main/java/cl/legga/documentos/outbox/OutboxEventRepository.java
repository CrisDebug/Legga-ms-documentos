package cl.legga.documentos.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
            SELECT *
            FROM documentos.outbox_event
            WHERE status = 'PENDING'
               OR (
                    status = 'PROCESSING'
                    AND processing_started_at < NOW() - INTERVAL '10 minutes'
               )
            ORDER BY occurred_at ASC
            LIMIT :limite
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> reclamarPendientes(
            @Param("limite") int limite);
}

