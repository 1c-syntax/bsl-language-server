const fs = require('node:fs');
const path = require('node:path');

const [input, resourceDirectory, basicMethodsFile] = process.argv.slice(2);
if (!input || !resourceDirectory || !basicMethodsFile) {
  throw Error('Usage: node update.cjs <runtime.json> <resource directory> <OneScript BasicMethods.json>');
}
const raw = JSON.parse(fs.readFileSync(input, 'utf8'));
const basicMethods = JSON.parse(fs.readFileSync(basicMethodsFile, 'utf8'));
const aliases = JSON.parse(fs.readFileSync(path.join(__dirname, 'parameter-aliases.json'), 'utf8'));
const typesFile = path.join(resourceDirectory, 'builtin-oscript-platform-types.json');
const globalsFile = path.join(resourceDirectory, 'builtin-oscript-globals.json');
const oldTypes = JSON.parse(fs.readFileSync(typesFile, 'utf8'));
const oldGlobals = JSON.parse(fs.readFileSync(globalsFile, 'utf8'));
const lower = value => (value || '').toLocaleLowerCase('en-US');
const intrinsicNamesSet = new Set([
  ...Object.values(basicMethods).flatMap(group => Object.keys(group.methods || {})),
  ...raw.flatMap(context => context.intrinsicNames || [])
].map(lower));
const compare = (left, right) => left.name < right.name ? -1 : left.name > right.name ? 1 : 0;
const named = value => ({name: value.Ru, nameRu: value.Ru, nameEn: value.En || ''});
const matches = (value, name) => [value.name, value.nameRu, value.nameEn, ...(value.aliases || [])]
  .some(candidate => candidate && lower(candidate) === lower(name));
const find = (values, name) => values.find(value => matches(value, name));
const documentedEnums = new Map([
  ...oldTypes.filter(type => type.kind === 'ENUM').flatMap(type => [type.name, type.nameEn, ...(type.aliases || [])].filter(Boolean)
    .map(name => [lower(name), type.name])),
  ...raw.filter(context => context.enumeration).flatMap(context => [context.name.Ru, context.name.En].filter(Boolean)
    .map(name => [lower(name), context.name.Ru]))
]);
const typeFields = (value, field = 'type') => !value ? {} : value.includes('|')
  ? {[field === 'type' ? 'types' : 'returnTypes']: value.split('|').map(name => name.trim())} : {[field]: value};
const typeNotes = {
  'Консоль': 'Используйте глобальный объект Консоль: конструктор этого типа не предоставляется.'
};
const base64BufferConversions = new Set([
  'GetBinaryDataBufferFromBase64BinaryDataBuffer', 'GetBinaryDataBufferFromBase64String'
]);

function typeDescription(context, previous) {
  const description = context.description || previous.description || '';
  const note = typeNotes[context.name.Ru];
  return note && !description.includes(note) ? `${description} ${note}`.trim() : description;
}

function parameterType(value, previous) {
  if (value.type !== 'Произвольный' || value.variadic) return value.type;
  const first = (value.description || '').replace(/^Тип:\s*/i, '').match(/^([^\s.,;(]+)/)?.[1];
  const documented = documentedEnums.get(lower(first));
  if (documented) return documented;
  const sameParameter = lower(previous.nameEn || previous.name) === lower(value.name);
  return sameParameter ? documentedEnums.get(lower(previous.type)) || value.type : value.type;
}

function methodDescription(method, previous) {
  const description = method.description || previous.description || '';
  // OneScript 2.2.0 documents BinaryData instead of BinaryDataBuffer for these conversions.
  const returns = base64BufferConversions.has(method.name.En) && method.returnType === 'БуферДвоичныхДанных'
    ? method.returnDescription?.replace(/^(Тип:\s*)?ДвоичныеДанные(?=[\s.,;:]|$)/, '$1БуферДвоичныхДанных')
    : method.returnDescription;
  const normalize = value => value.replace(/\s+/g, ' ').trim();
  return returns && !normalize(description).includes(normalize(returns))
    ? `${description}\n\nВозвращаемое значение: ${returns}`.trim() : description;
}

function previousSignature(method, previous) {
  const candidates = (previous.signatures || [])
    .filter(value => value.parameters?.length === method.parameters.length);
  const named = candidates.filter(value => value.parameters.every((parameter, index) =>
    lower(parameter.nameEn || parameter.name) === lower(method.parameters[index].name)));
  return named.length === 1 ? named[0] : candidates.length === 1 ? candidates[0] : undefined;
}

let parameterCount = 0, russianCount = 0, variadicCount = 0;
const missing = new Set();
function parameter(value, previous = {}) {
  parameterCount++;
  let ru = value.nameRu || aliases[lower(value.name)];
  if (!ru && /^(p|param|number)\d+$/i.test(value.name)) {
    ru = value.name.replace(/^(param|p)/i, 'Параметр').replace(/^number/i, 'Число');
  }
  if (!ru && /[А-Яа-яЁё]/.test(value.name)) ru = value.name;
  if (!ru && previous.nameRu) ru = previous.nameRu;
  if (ru) russianCount++; else missing.add(value.name);
  if (value.variadic) variadicCount++;
  return {
    name: ru || value.name, nameRu: ru || '', nameEn: value.name,
    ...typeFields(parameterType(value, previous)), optional: value.optional, description: value.description || previous.description || '',
    ...(value.defaultValue != null ? {defaultValue: value.defaultValue} : {}),
    ...(value.variadic ? {variadic: true} : {})
  };
}

function signature(method, previous = {}, constructorType) {
  const old = previousSignature(method, previous);
  const returns = constructorType || (method.returnType === 'Произвольный' && previous.returnType
    ? previous.returnType : method.returnType);
  return {
    parameters: method.parameters.map((value, index) => parameter(constructorParameter(value, constructorType),
      old?.parameters.find(candidate => lower(candidate.nameEn || candidate.name) === lower(value.name))
      || old?.parameters[index])),
    ...typeFields(returns, 'returnType'),
    ...(method.description || old?.description ? {description: method.description || old.description} : {})
  };
}

function constructorParameter(value, type) {
  if (value.name !== 'param1' || !['Структура', 'ФиксированнаяСтруктура'].includes(type)) return value;
  return {...value, type: `Строка|${type === 'Структура' ? 'ФиксированнаяСтруктура' : 'Структура'}`,
    nameRu: 'КлючиИлиСтруктура'};
}

function members(context, old) {
  const result = [];
  const deprecated = new Map();
  for (const property of context.properties) {
    const previous = find(old, property.name.Ru) || {};
    result.push({...named(property.name), kind: 'PROPERTY',
      ...typeFields(property.type === 'Произвольный' && previous.returnType ? previous.returnType : property.type,
        'returnType'),
      description: property.description || previous.description || '',
      accessMode: property.canRead && property.canWrite ? 'READ_WRITE' : property.canWrite ? 'WRITE' : 'READ'});
  }
  const methods = new Map();
  for (const method of context.methods) {
    const previous = find(old, method.name.Ru) || {};
    const key = lower(method.name.Ru);
    let member = methods.get(key);
    if (!member) {
      member = {...named(method.name), kind: 'METHOD', description: methodDescription(method, previous),
        ...(previous.deprecatedSinceVersion ? {deprecatedSinceVersion: previous.deprecatedSinceVersion} : {}),
        ...(previous.recommendedReplacements ? {recommendedReplacements: previous.recommendedReplacements} : {}),
        ...(previous.async ? {async: true} : {}),
        ...typeFields(method.returnType === 'Произвольный' && previous.returnType
          ? previous.returnType : method.returnType, 'returnType'), signatures: []};
      methods.set(key, member);
    }
    member.signatures.push(signature(method, previous));
    for (const alias of method.deprecatedNames || []) deprecated.set(alias, method.name.Ru);
  }
  result.push(...methods.values());
  for (const [alias, primaryName] of deprecated) {
    if (find(result, alias)) continue;
    const primary = methods.get(lower(primaryName));
    const previous = find(old, alias) || {};
    result.push({...primary, name: alias, nameRu: '', nameEn: alias,
      description: previous.description || primary.description,
      deprecatedSinceVersion: '*',
      recommendedReplacements: previous.recommendedReplacements || [primaryName]});
  }
  for (const value of context.values) {
    const previous = find(old, value.Ru);
    const legacyEnglish = old.find(member =>
      !/[А-Яа-яЁё]/.test(member.nameRu || member.name || '') && matches(member, value.En));
    const description = previous?.description || legacyEnglish?.description;
    result.push({...named(value), kind: 'PROPERTY', returnType: context.name.Ru,
      ...(description ? {description} : {})});
  }
  return result.sort(compare);
}

const types = raw.filter(context => !context.global).map(context => {
  const previous = find(oldTypes, context.name.Ru) || {};
  const constructors = context.constructors.map(method => signature(method,
    {signatures: previous.constructors || []}, context.name.Ru));
  return {
    kind: context.enumeration ? 'ENUM' : 'TYPE', ...named(context.name),
    description: typeDescription(context, previous),
    ...(previous.supportsForEach ? {supportsForEach: true} : {}),
    ...(previous.supportsIndexAccess ? {supportsIndexAccess: true} : {}),
    ...(previous.elementTypes ? {elementTypes: previous.elementTypes} : {}),
    ...(previous.forEachDescription ? {forEachDescription: previous.forEachDescription} : {}),
    ...(previous.indexAccessDescription ? {indexAccessDescription: previous.indexAccessDescription} : {}),
    ...(previous.typeParameters ? {typeParameters: previous.typeParameters} : {}),
    ...(constructors.length ? {constructors} : {}),
    members: members(context, previous.members || [])
  };
}).sort(compare);
for (const previous of oldTypes.filter(type => type.kind === 'PRIMITIVE_TYPE')) {
  if (!find(types, previous.name)) types.push(previous);
}
types.sort(compare);

const functions = new Map();
const variables = new Map();
for (const context of raw.filter(value => value.global)) {
  for (const member of members(context, [...oldGlobals.functions, ...oldGlobals.variables])) {
    const value = {...member, aliases: member.nameEn ? [member.nameEn] : []};
    if (member.kind === 'METHOD') functions.set(lower(member.name), value);
    else variables.set(lower(member.name), {...value, type: member.returnType});
  }
}
// Intrinsic language functions are supplied by the compiler, without ContextMethod attributes.
const intrinsicNames = {
  'Строка': 'String', 'Значение': 'Value', 'Число': 'Number', 'Дата': 'Date', 'НачальныйНомер': 'StartPosition',
  'ЧислоСимволов': 'NumberOfCharacters', 'НомерСимвола': 'Position', 'ПодстрокаПоиска': 'SearchString',
  'ПодстрокаЗамены': 'Replacement', 'НомерСтроки': 'LineNumber', 'ДобавляемыеМесяцы': 'Months',
  'Разрядность': 'Digits', 'РежимОкругления': 'RoundingMode', 'ФорматнаяСтрока': 'FormatString',
  'ИмяТипа': 'TypeName'
};
for (const value of oldGlobals.functions) {
  if ([...functions.values()].some(member => matches(member, value.name))) continue;
  if (!intrinsicNamesSet.has(lower(value.name))) continue;
  const entry = {...value, nameRu: value.nameRu || value.name,
    nameEn: value.nameEn || value.aliases?.[0] || ''};
  if (entry.returnType?.includes('|')) {
    delete entry.returnType;
    Object.assign(entry, typeFields(value.returnType, 'returnType'));
  }
  entry.signatures = (value.signatures || []).map(sig => {
    const tail = sig.parameters?.some(parameter => parameter.name === '...');
    const parameters = (sig.parameters || []).filter(parameter => parameter.name !== '...').map((parameter, index, all) => {
      const variadic = tail && index === all.length - 1;
      const ru = variadic ? 'Значения' : parameter.nameRu || parameter.name;
      const en = parameter.nameEn || (variadic ? 'Values' : intrinsicNames[ru]
        || (/^Значение\d+$/.test(ru) ? ru.replace('Значение', 'Value') : ru));
      const updated = {...parameter, name: ru, nameRu: ru, nameEn: en, ...typeFields(parameter.type)};
      if (parameter.type?.includes('|')) delete updated.type;
      if (variadic) Object.assign(updated, {variadic: true, optional: true});
      return updated;
    });
    const updated = {...sig, parameters, ...typeFields(sig.returnType, 'returnType')};
    if (sig.returnType?.includes('|')) delete updated.returnType;
    return updated;
  });
  functions.set(lower(value.name), entry);
}
for (const context of raw.filter(value => value.enumeration)) variables.set(lower(context.name.Ru),
  {...named(context.name), aliases: context.name.En ? [context.name.En] : [], type: context.name.Ru,
    description: context.description || ''});
const globals = {functions: [...functions.values()].sort(compare), variables: [...variables.values()].sort(compare),
  classes: [...new Set(types.filter(type => type.kind !== 'ENUM').map(type => type.name))].sort(),
  keywords: oldGlobals.keywords || []};
fs.writeFileSync(typesFile, JSON.stringify(types, null, 2) + '\n');
fs.writeFileSync(globalsFile, JSON.stringify(globals, null, 2) + '\n');
console.log(JSON.stringify({types: types.length, functions: functions.size, variables: variables.size,
  parameterCount, russianCount, variadicCount, missingRussianAliases: [...missing].sort()}, null, 2));
