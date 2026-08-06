import type MarkdownIt from 'markdown-it'

/**
 * Совместимость с разметкой, унаследованной от MkDocs Material (pymdown-extensions).
 *
 * Исходные `.md` файлы диагностик и разделов пишутся в едином формате, часть из них
 * попадает в jar и показывается в hover прямо в IDE, поэтому переписывать их под
 * синтаксис VitePress нельзя. Вместо этого конвертируем разметку на лету.
 */

const ADMONITION = /^(!!!|\?\?\?\+?)\s+([A-Za-zА-Яа-яЁё0-9_.-]+)?\s*(?:"([^"]*)")?\s*$/

/** Типы pymdownx -> контейнеры VitePress. */
const TYPES: Record<string, string> = {
  note: 'info',
  info: 'info',
  abstract: 'info',
  summary: 'info',
  todo: 'info',
  question: 'tip',
  tip: 'tip',
  hint: 'tip',
  success: 'tip',
  check: 'tip',
  example: 'tip',
  warning: 'warning',
  caution: 'warning',
  attention: 'warning',
  danger: 'danger',
  error: 'danger',
  bug: 'danger',
  failure: 'danger',
}

/** Схемы, якоря и абсолютные пути, которые трогать нельзя. */
const EXTERNAL = /^(?:[a-z][a-z0-9+.-]*:|\/\/|#|\/)/i

const MARKDOWN_LINK = /(\]\()([^)\s]+)((?:\s+"[^"]*")?\))/g

/**
 * Итератор по строкам, пропускающий содержимое блоков кода: внутри них разметку
 * трогать нельзя.
 */
function mapOutsideFences(source: string, transform: (line: string) => string): string {
  let fence: string | null = null

  return source
    .split('\n')
    .map((line) => {
      const fenceMatch = /^\s*(```+|~~~+)/.exec(line)
      if (fenceMatch) {
        if (fence && line.trimStart().startsWith(fence)) {
          fence = null
        } else if (!fence) {
          fence = fenceMatch[1]
        }
        return line
      }
      return fence ? line : transform(line)
    })
    .join('\n')
}

/**
 * MkDocs отдавал страницы каталогами, поэтому в тексте прижились ссылки без расширения:
 * `[Переименование](./rename)`. VitePress разрешает такие ссылки буквально и считает их
 * битыми, а статический хостинг может ответить 404 без завершающего слэша. Приводим их
 * к каноничному виду `./rename/`, сохраняя якорь.
 */
function normalizeLink(target: string): string | null {
  if (!target || EXTERNAL.test(target)) {
    return null
  }

  const hashAt = target.indexOf('#')
  const hash = hashAt === -1 ? '' : target.slice(hashAt)
  let path = hashAt === -1 ? target : target.slice(0, hashAt)

  if (!path) {
    return null
  }

  if (path.endsWith('/')) {
    return target
  }

  const last = path.slice(path.lastIndexOf('/') + 1)
  if (last.endsWith('.md')) {
    path = path.slice(0, -'.md'.length)
  } else if (last.includes('.')) {
    // Картинки, схемы, javadoc и прочие файлы с расширением — не страницы сайта.
    return null
  }

  if (path === 'index' || path === './index') {
    path = './'
  } else if (path.endsWith('/index')) {
    path = path.slice(0, -'index'.length)
  } else {
    path = `${path}/`
  }

  return path + hash
}

/**
 * Страницы отдаются каталогами (`rewrites` в конфиге), то есть `features/McpMode.md`
 * превращается в `features/McpMode/index.md` и оказывается на уровень глубже исходника.
 * Относительные ссылки в тексте написаны от исходного файла, поэтому на таких страницах
 * им нужен дополнительный `../`.
 */
function deepen(target: string): string {
  const segments = `../${target}`.split('/')
  const result: string[] = []

  for (const segment of segments) {
    if (segment === '.') {
      continue
    }
    if (segment === '..' && result.length > 0 && result[result.length - 1] !== '..') {
      result.pop()
      continue
    }
    result.push(segment)
  }

  return result.join('/')
}

export function normalizeExtensionlessLinks(source: string, nested = false): string {
  if (!source.includes('](')) {
    return source
  }

  return mapOutsideFences(source, (line) =>
    line.replace(MARKDOWN_LINK, (match, open, target: string, tail) => {
      const normalized = normalizeLink(target)
      if (normalized === null) {
        return match
      }
      return open + (nested ? deepen(normalized) : normalized) + tail
    }),
  )
}

function dedent(lines: string[]): string[] {
  const indent = lines
    .filter((line) => line.trim().length > 0)
    .reduce((min, line) => Math.min(min, line.length - line.trimStart().length), Infinity)

  if (!Number.isFinite(indent) || indent === 0) {
    return lines
  }

  return lines.map((line) => (line.trim().length === 0 ? line : line.slice(indent)))
}

/**
 * ```text
 * !!! warning "Заголовок"     ->  ::: warning Заголовок
 *     текст                        текст
 *                                 :::
 *
 * ??? textDocument            ->  ::: details textDocument
 *     текст                        текст
 *                                 :::
 * ```
 */
export function convertAdmonitions(source: string): string {
  if (!source.includes('!!!') && !source.includes('???')) {
    return source
  }

  const lines = source.split('\n')
  const result: string[] = []
  let fence: string | null = null

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]

    const fenceMatch = /^\s*(```+|~~~+)/.exec(line)
    if (fenceMatch) {
      if (fence && line.trimStart().startsWith(fence)) {
        fence = null
      } else if (!fence) {
        fence = fenceMatch[1]
      }
      result.push(line)
      continue
    }
    if (fence) {
      result.push(line)
      continue
    }

    const match = ADMONITION.exec(line)
    if (!match) {
      result.push(line)
      continue
    }

    const [, marker, keyword, quoted] = match
    const collapsible = marker.startsWith('???')

    // Тело блока — все последующие строки с отступом (или пустые).
    const body: string[] = []
    let j = i + 1
    while (j < lines.length && (lines[j].trim() === '' || /^\s{4}/.test(lines[j]))) {
      body.push(lines[j])
      j++
    }
    while (body.length > 0 && body[body.length - 1].trim() === '') {
      body.pop()
    }

    if (body.length === 0) {
      result.push(line)
      continue
    }

    const known = TYPES[(keyword ?? '').toLowerCase()]
    const type = collapsible ? 'details' : (known ?? 'info')
    // У `!!!` первое слово — это тип блока; у `???` и у неизвестных типов — заголовок.
    const title = quoted ?? (collapsible || !known ? (keyword ?? '') : '')

    result.push(`::: ${type}${title ? ' ' + title : ''}`)
    result.push(...dedent(body))
    result.push(':::')
    result.push('')

    i = j - 1
  }

  return result.join('\n')
}

/**
 * @param nestedPages пути (относительно `docs`) страниц, отдаваемых каталогами —
 *        значения карты `rewrites` из конфига.
 */
export function mkdocsCompat(md: MarkdownIt, nestedPages: ReadonlySet<string>): void {
  md.core.ruler.before('normalize', 'mkdocs_compat', (state) => {
    const relativePath: string = state.env?.relativePath ?? ''
    state.src = normalizeExtensionlessLinks(convertAdmonitions(state.src), nestedPages.has(relativePath))
    return false
  })
}
