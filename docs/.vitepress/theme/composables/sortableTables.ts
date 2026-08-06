/**
 * Сортировка таблиц по клику на заголовок — замена плагина tablesort, который
 * использовался на MkDocs-версии сайта.
 */

const COLLATOR = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' })

function textOf(row: HTMLTableRowElement, index: number): string {
  return row.cells[index]?.textContent?.trim() ?? ''
}

function sort(table: HTMLTableElement, header: HTMLTableCellElement, index: number): void {
  const body = table.tBodies[0]
  if (!body) {
    return
  }

  const descending = header.classList.contains('bsl-asc')

  for (const other of Array.from(table.querySelectorAll('th'))) {
    other.classList.remove('bsl-asc', 'bsl-desc')
  }
  header.classList.add(descending ? 'bsl-desc' : 'bsl-asc')

  const rows = Array.from(body.rows)
  rows.sort((a, b) => {
    const result = COLLATOR.compare(textOf(a, index), textOf(b, index))
    return descending ? -result : result
  })

  for (const row of rows) {
    body.appendChild(row)
  }
}

export function enableSortableTables(container: ParentNode = document): void {
  for (const table of Array.from(container.querySelectorAll<HTMLTableElement>('.vp-doc table'))) {
    const body = table.tBodies[0]
    if (!body || body.rows.length < 3) {
      continue
    }

    const headers = Array.from(table.querySelectorAll<HTMLTableCellElement>('thead th'))
    headers.forEach((header, index) => {
      if (header.classList.contains('bsl-sortable')) {
        return
      }
      header.classList.add('bsl-sortable')
      header.setAttribute('role', 'button')
      header.setAttribute('tabindex', '0')
      header.addEventListener('click', () => sort(table, header, index))
      header.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          sort(table, header, index)
        }
      })
    })
  }
}
