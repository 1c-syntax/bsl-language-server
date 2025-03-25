async procedure тест(val парам1) export
  var тест;
  
  if условие and УсловиеДва or not Условие3 then
  elsif Условие4 then
  else
  endif;
  
  while условие do
    break;
  enddo;
  
  for each строка in таблица do
    continue;
  enddo;
  
  try
    объект = new Массив;
  except
    raise "исключение";
  endtry
  
  goto ~Метка1;
  ~Метка1: сообщить("привет");
  
  addhandler Накладная.ПриЗаписи, Обработка.ПриЗаписиДокумента;
  removehandler Накладная.ПриЗаписи, Обработка.ПриЗаписиДокумента;
  
  await call();
endprocedure

function тест2()
endfunction