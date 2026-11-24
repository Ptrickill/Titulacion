package ec.rehabelite.assignments.web;

import jakarta.validation.constraints.NotNull;

/** Body of PUT /api/operations/treatments/{id}/responsible (MEJ-04): the account of the new responsible. */
record ReassignRequest(@NotNull Long physiotherapistId) {
}
