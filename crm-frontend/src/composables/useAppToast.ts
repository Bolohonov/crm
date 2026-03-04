import { useToast } from 'primevue/usetoast'

export function useAppToast() {
  const toast = useToast()
  return {
    success: (message: string, title = 'Успешно') =>
      toast.add({ severity: 'success', summary: title, detail: message, life: 3500 }),
    error: (message: string, title = 'Ошибка') =>
      toast.add({ severity: 'error', summary: title, detail: message, life: 5000 }),
    warn: (message: string, title = 'Внимание') =>
      toast.add({ severity: 'warn', summary: title, detail: message, life: 4000 }),
  }
}
