
package com.ucamp.app.domain.prescription.repository;

import com.ucamp.app.domain.prescription.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    interface OverlapRow {
        String getName();
        LocalDate getStartAt();
        LocalDate getEndAt();
        String getActiveNameKo();
    }

    List<Prescription> findAllByUserId(Long userId);

    Optional<Prescription> findByPrescriptionIdAndUserId(
            Long prescriptionId,
            Long userId
    );

    @Query("""
        select p.name as name,
               p.startAt as startAt,
               p.endAt as endAt,
               mi.activeNameKo as activeNameKo
        from Prescription p
        join MedicineIngredient mi on mi.medicineId = p.medicineId
        where p.userId = :userId
          and p.status = com.ucamp.app.domain.prescription.enums.PrescriptionStatus.ACTIVE
          and p.medicineId is not null
          and p.prescriptionId <> :excludeId
          and mi.activeCode in :codes
          and exists (
              select ce.eventId
              from CalendarEvent ce
              where ce.prescriptionId = p.prescriptionId
                and ce.userId = :userId
                and ce.eventType = com.ucamp.app.domain.calendar.enums.EventType.MEDICATION
                and ce.scheduledAt >= :start
                and ce.scheduledAt < :end
          )
        """)
    List<OverlapRow> findOverlapCandidates(
            @Param("userId") Long userId,
            @Param("codes") Collection<String> codes,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("excludeId") Long excludeId
    );
}
