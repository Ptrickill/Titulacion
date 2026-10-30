/** One page of results, as the backend's PageResponse (shared.web). */
export interface Page<T> {
  content: T[];
  /** Zero-based. */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
