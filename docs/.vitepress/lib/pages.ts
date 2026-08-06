import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, sep } from 'node:path'

/** Каталоги внутри `docs`, которые не являются страницами сайта. */
const IGNORED_DIRS = new Set(['.vitepress', 'node_modules', 'assets', 'javadoc', 'bench', 'public'])

function walk(root: string, dir = root, acc: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (!IGNORED_DIRS.has(entry)) {
        walk(root, full, acc)
      }
    } else if (entry.endsWith('.md')) {
      acc.push(relative(root, full).split(sep).join('/'))
    }
  }
  return acc
}

/**
 * Ссылки на документацию диагностик зашиты в самом языковом сервере
 * (`DiagnosticInfo.computeCodeDescriptionHref`) и в сообщениях диагностик, а также
 * разошлись по интернету в виде `.../diagnostics/ИмяДиагностики`. MkDocs отдавал такие
 * адреса каталогами (`ИмяДиагностики/index.html`), поэтому переносим ровно ту же схему
 * URL: каждая страница, кроме `index.md`, превращается в каталог с `index.html` внутри.
 */
export function directoryUrlRewrites(docsRoot: string): Record<string, string> {
  const rewrites: Record<string, string> = {}

  for (const file of walk(docsRoot)) {
    if (file.endsWith('/index.md') || file === 'index.md') {
      continue
    }
    rewrites[file] = file.replace(/\.md$/, '/index.md')
  }

  return rewrites
}

export interface DiagnosticPage {
  /** Ключ диагностики, он же имя файла: `DeprecatedMessage`. */
  key: string
  /** Заголовок страницы без ключа в скобках. */
  title: string
  link: string
}

/** Список страниц диагностик для указанной локали (`''` — русская, `en` — английская). */
export function diagnosticPages(docsRoot: string, locale: '' | 'en'): DiagnosticPage[] {
  const prefix = locale ? `${locale}/` : ''
  const dir = join(docsRoot, ...(locale ? [locale] : []), 'diagnostics')

  return readdirSync(dir)
    .filter((file) => file.endsWith('.md') && file !== 'index.md')
    .map((file) => {
      const key = file.replace(/\.md$/, '')
      const content = readFileSync(join(dir, file), 'utf-8')
      const heading = /^#\s+(.+)$/m.exec(content)?.[1]?.trim() ?? key

      return {
        key,
        title: heading.replace(/\s*\([^)]*\)\s*$/, '').trim() || key,
        link: `/${prefix}diagnostics/${key}/`,
      }
    })
    .sort((a, b) => a.key.localeCompare(b.key))
}
