import { apiClient } from './client';

export interface HealthResponse {
  status: string;
}

export async function getBackendHealth(): Promise<HealthResponse> {
  const response = await apiClient.get<HealthResponse>('/actuator/health');
  return response.data;
}
