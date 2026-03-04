import client from './client'

/**
 * API для скачивания файлов экспорта.
 *
 * Возвращает Blob — браузер сам определяет MIME тип из заголовков ответа.
 * Файл скачивается через временный <a href=blob:...>.
 */
export const exportApi = {
  /**
   * Скачать файл экспорта.
   *
   * @param entity   'customers' | 'orders'
   * @param format   'xlsx' | 'csv'
   * @param params   опциональные фильтры: { managerId?, statusCode? }
   */
  download(
    entity: 'customers' | 'orders',
    format: 'xlsx' | 'csv',
    params: Record<string, string> = {}
  ): Promise<Blob> {
    return client
      .get(`/admin/export/${entity}.${format}`, {
        params,
        responseType: 'blob',
        // Увеличиваем таймаут для больших выгрузок
        timeout: 60_000,
      })
      .then(r => r.data as Blob)
  },
}
