package ec.rehabelite.auth;

/**
 * Indicators of the people registry (MEJ-02): accounts with status ACTIVE (every role), and the accounts of each role
 * whatever their status, as "Gestión de Usuarios" counted them.
 */
public record AccountStatistics(long activeAccounts, long physiotherapists, long portalPatients,
        long administrators) {
}
