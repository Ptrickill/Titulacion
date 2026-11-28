package ec.rehabelite.patients.domain;

import ec.rehabelite.shared.domain.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A request of the data subject (MEJ-06), registered by the reception: its type, the day it was received, its status
 * and the response. It is received, may go in progress and is closed once, resolved or rejected, with a response.
 * Never deleted.
 */
@Entity
@Table(name = "data_subject_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DataSubjectRequest {

    public static final int MAX_RESPONSE_LENGTH = 2000;
    public static final String ALREADY_CLOSED = "La solicitud ya está cerrada.";
    public static final String RESPONSE_REQUIRED = "Escriba la respuesta al titular para cerrar la solicitud.";
    public static final String TRANSITION_NOT_ALLOWED = "Cambio de estado de la solicitud no permitido.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 20)
    private DataRequestType type;

    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private DataRequestStatus status;

    @Column(length = MAX_RESPONSE_LENGTH)
    private String response;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public static DataSubjectRequest register(Long patientId, DataRequestType type, LocalDate receivedOn,
            Long createdBy, Instant now) {
        DataSubjectRequest request = new DataSubjectRequest();
        request.patientId = patientId;
        request.type = type;
        request.receivedOn = receivedOn;
        request.status = DataRequestStatus.RECEIVED;
        request.createdBy = createdBy;
        request.createdAt = now;
        return request;
    }

    public boolean isOpen() {
        return status == DataRequestStatus.RECEIVED || status == DataRequestStatus.IN_PROGRESS;
    }

    /**
     * Moves the request to a new status: a received request goes in progress, and an open one is resolved or rejected
     * with a response. Asking for the status it already has changes nothing.
     *
     * @return whether the status changed
     * @throws BusinessRuleException if the request is closed, the transition is not allowed or a closing has no
     *         response
     */
    public boolean changeStatus(DataRequestStatus newStatus, String response, Long changedBy, Instant now) {
        if (!isOpen()) {
            throw new BusinessRuleException(ALREADY_CLOSED);
        }
        if (newStatus == status) {
            return false;
        }
        switch (newStatus) {
            case IN_PROGRESS -> {
                // Only RECEIVED gets here: IN_PROGRESS to IN_PROGRESS returned above
            }
            case RESOLVED, REJECTED -> {
                if (response == null || response.isBlank()) {
                    throw new BusinessRuleException(RESPONSE_REQUIRED);
                }
                this.response = response.strip();
                this.resolvedBy = changedBy;
                this.resolvedAt = now;
            }
            default -> throw new BusinessRuleException(TRANSITION_NOT_ALLOWED);
        }
        this.status = newStatus;
        this.updatedBy = changedBy;
        this.updatedAt = now;
        return true;
    }

}
