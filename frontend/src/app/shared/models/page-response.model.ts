export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  page: number;
  size: number;
}