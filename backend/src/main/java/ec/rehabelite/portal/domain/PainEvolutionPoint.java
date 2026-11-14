package ec.rehabelite.portal.domain;

import java.time.LocalDate;

/**
 * A point of the pain evolution a patient sees in the portal (HU-12).
 *
 * @param label          Spanish label: "Evaluación inicial" or "Sesión N"
 * @param date           day of the initial evaluation (start of the treatment) or of the session
 * @param painScore      EVA, 0 to 10
 * @param sequenceNumber sequence number of the session; null for the initial evaluation
 */
public record PainEvolutionPoint(String label, LocalDate date, int painScore, Integer sequenceNumber) {
}
