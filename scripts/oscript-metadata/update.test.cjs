const assert = require('node:assert/strict');
const {test} = require('node:test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {execFileSync} = require('node:child_process');

function regenerate(types, globals, runtime, verify) {
  const temporary = path.resolve(os.tmpdir());
  const directory = fs.mkdtempSync(path.join(temporary, 'bsl-oscript-metadata-'));
  const typesFile = path.join(directory, 'builtin-oscript-platform-types.json');
  const globalsFile = path.join(directory, 'builtin-oscript-globals.json');
  const input = path.join(directory, 'runtime.json');
  const basic = path.join(directory, 'basic.json');
  try {
    for (const [file, value] of [[typesFile, types], [globalsFile, globals], [input, runtime], [basic, {}]]) {
      fs.writeFileSync(file, JSON.stringify(value));
    }
    const run = () => execFileSync(process.execPath,
      [path.join(__dirname, 'update.cjs'), input, directory, basic], {stdio: 'pipe'});
    run();
    verify(JSON.parse(fs.readFileSync(typesFile)), JSON.parse(fs.readFileSync(globalsFile)));
    const first = [fs.readFileSync(typesFile, 'utf8'), fs.readFileSync(globalsFile, 'utf8')];
    run();
    assert.deepEqual([fs.readFileSync(typesFile, 'utf8'), fs.readFileSync(globalsFile, 'utf8')], first);
  } finally {
    assert.equal(path.dirname(path.resolve(directory)), temporary);
    assert.ok(path.basename(directory).startsWith('bsl-oscript-metadata-'));
    fs.rmSync(directory, {recursive: true, force: true});
  }
}

const globals = () => ({functions: [], variables: [], classes: [], keywords: []});
const context = (ru, en, extra = {}) => ({name: {Ru: ru, En: en}, global: false,
  enumeration: false, methods: [], properties: [], constructors: [], values: [], ...extra});
const method = (ru, en, parameters = [], extra = {}) => ({name: {Ru: ru, En: en},
  parameters, returnType: 'Строка', description: '', ...extra});

test('regeneration retains curated enum descriptions with corrected bilingual pairs', () => {
  const old = [{kind: 'ENUM', name: 'Выбор', members: [
    {name: 'Все', nameRu: 'Все', nameEn: 'Choice', kind: 'PROPERTY',
      description: 'Все элементы без требования порядка', descriptionEn: 'Ошибочное описание соседнего элемента'},
    {name: 'Один', nameRu: 'Один', nameEn: 'Sequence', kind: 'PROPERTY', description: 'Один элемент'}
  ]}];
  regenerate(old, globals(), [context('Выбор', 'ChoiceKind', {enumeration: true,
    values: [{Ru: 'Все', En: 'All'}, {Ru: 'Один', En: 'Choice'}]})], types => {
    const [all, choice] = types[0].members;
    assert.equal(all.nameEn, 'All');
    assert.equal(all.description, 'Все элементы без требования порядка');
    assert.equal(choice.nameEn, 'Choice');
    assert.equal(choice.description, 'Один элемент');
    assert.equal(all.descriptionEn, undefined);
  });
});

test('deprecated runtime aliases remain separate from the active method and global function', () => {
  const runtime = [context('Объект', 'Object', {methods: [method('Получить', 'Get', [],
    {deprecatedNames: ['OldGet']})]}), context('Глобальный', 'Global', {global: true,
    methods: [method('Функция', 'Function', [], {deprecatedNames: ['OldFunction']})]})];
  regenerate([], globals(), runtime, (types, result) => {
    const active = types[0].members.find(member => member.name === 'Получить');
    const alias = types[0].members.find(member => member.name === 'OldGet');
    assert.equal(active.deprecatedSinceVersion, undefined);
    assert.equal(alias.deprecatedSinceVersion, '*');
    assert.deepEqual(alias.recommendedReplacements, ['Получить']);
    assert.deepEqual(alias.signatures, active.signatures);
    const globalAlias = result.functions.find(member => member.name === 'OldFunction');
    assert.equal(globalAlias.deprecatedSinceVersion, '*');
    assert.deepEqual(globalAlias.recommendedReplacements, ['Функция']);
  });
});

test('new bilingual enum labels retain annotations from English-only legacy entries', () => {
  regenerate([{kind: 'ENUM', name: 'Кодировка', members: [
    {name: 'UTF8БезBOM', nameRu: 'UTF8БезBOM', nameEn: 'UTF8NoBOM', kind: 'PROPERTY'},
    {name: 'UTF8NoBOM', nameEn: 'UTF8NoBOM', kind: 'PROPERTY', description: 'UTF-8 без BOM'}
  ]}], globals(), [context('Кодировка', 'Encoding', {enumeration: true,
    values: [{Ru: 'UTF8БезBOM', En: 'UTF8NoBOM'}]})], types => {
      assert.equal(types[0].members[0].nameRu, 'UTF8БезBOM');
      assert.equal(types[0].members[0].description, 'UTF-8 без BOM');
    });
});

test('same-arity constructors retain annotations for their own parameter names', () => {
  const old = [{kind: 'TYPE', name: 'Объект', members: [], constructors: [
    {parameters: [{name: 'copy', type: 'Объект', description: 'Копируемый объект'}], description: 'Копия'},
    {parameters: [{name: 'size', type: 'Число', description: 'Начальный размер'}], description: 'Размер'}
  ]}];
  regenerate(old, globals(), [context('Объект', 'Object', {constructors: [
    method('Constructor', '', [{name: 'size', type: 'Число', optional: false}]),
    method('Constructor', '', [{name: 'copy', type: 'Объект', optional: false}])
  ]})], types => {
    assert.equal(types[0].constructors[0].description, 'Размер');
    assert.equal(types[0].constructors[0].parameters[0].description, 'Начальный размер');
    assert.equal(types[0].constructors[1].description, 'Копия');
  });
});

test('updated upstream documentation keeps the curated global-console usage rule', () => {
  regenerate([{kind: 'TYPE', name: 'Консоль', members: [], constructors: []}], globals(),
    [context('Консоль', 'Console', {description: 'Обработка Ctrl+C.'})], types => {
      assert.match(types[0].description, /Ctrl\+C/);
      assert.match(types[0].description, /Используйте глобальный объект Консоль/);
      assert.equal(types[0].constructors, undefined);
    });
});

test('return documentation retains result semantics for methods and global functions', () => {
  const find = method('Найти', 'Find', [], {description: 'Ищет подстроку.',
    returnDescription: 'Возвращает 0, если подстрока не найдена.'});
  regenerate([], globals(), [context('Объект', 'Object', {methods: [find]}),
    context('Глобальный', 'Global', {global: true, methods: [find]})], (types, result) => {
      for (const member of [types[0].members[0], result.functions[0]]) {
        assert.equal(member.description,
          'Ищет подстроку.\n\nВозвращаемое значение: Возвращает 0, если подстрока не найдена.');
      }
    });
});

test('current source documentation refines an untyped enum argument without narrowing arbitrary objects', () => {
  const runtime = [context('Направление', 'Direction', {enumeration: true}),
    context('Объект', 'Object', {methods: [method('Проверить', 'Check', [
      {name: 'direction', type: 'Произвольный', description: 'Направление (необязательный) — порядок.', optional: true},
      {name: 'target', type: 'Произвольный', description: 'Объект, который нужно проверить.', optional: false}
    ])]})];
  regenerate([], globals(), runtime, types => {
    const parameters = types.find(type => type.name === 'Объект').members[0].signatures[0].parameters;
    assert.equal(parameters[0].type, 'Направление');
    assert.equal(parameters[1].type, 'Произвольный');
  });
});

test('curated enum parameters survive generic CLR signatures while arbitrary object annotations do not', () => {
  const old = [{kind: 'ENUM', name: 'Направление', members: []},
    {kind: 'TYPE', name: 'Объект', members: [{name: 'Проверить', kind: 'METHOD', signatures: [
      {parameters: [{name: 'direction', type: 'Направление'}, {name: 'target', type: 'ИнформацияОСценарии'}]}
    ]}]}];
  const runtime = [context('Направление', 'Direction', {enumeration: true}),
    context('Объект', 'Object', {methods: [method('Проверить', 'Check', [
      {name: 'direction', type: 'Произвольный', description: 'Порядок обхода.', optional: false},
      {name: 'target', type: 'Произвольный', description: 'Проверяемый объект.', optional: false}
    ])]})];
  regenerate(old, globals(), runtime, types => {
    const parameters = types.find(type => type.name === 'Объект').members[0].signatures[0].parameters;
    assert.equal(parameters[0].type, 'Направление');
    assert.equal(parameters[1].type, 'Произвольный');
  });
});
