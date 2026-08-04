import axios from 'axios';

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim();

export const apiClient = axios.create({
  baseURL: configuredBaseUrl || 'http://localhost:8080',
  timeout: 5_000,
  headers: {
    Accept: 'application/json',
  },
});
