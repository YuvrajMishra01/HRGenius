/** Unified backend envelope: { success, message, data } */
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

/** Backend error shape from GlobalExceptionHandler */
export interface ApiError {
  timestamp: string;
  status: number;
  message: string;
  path: string;
  errors?: Record<string, string>;
}

export interface HealthInfo {
  application: string;
  status: 'UP' | 'DOWN';
  database: string;
  timestamp: string;
}
