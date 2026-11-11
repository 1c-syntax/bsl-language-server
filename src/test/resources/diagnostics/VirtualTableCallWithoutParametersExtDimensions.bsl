Функция ТекстЗапроса()

    Возврат "ВЫБРАТЬ
    |    Аналитика.Регистратор,
    |    Аналитика.НомерСтроки,
    |    Аналитика.Вид,
    |    Аналитика.Значение
    |ИЗ
    |    РегистрБухгалтерии.ТестовыйРегистр.Субконто КАК Аналитика";

КонецФункции

Function QueryText()

    Return "SELECT
    |    Analytics.Recorder,
    |    Analytics.LineNumber,
    |    Analytics.Kind,
    |    Analytics.Value
    |FROM
    |    AccountingRegister.TestRegister.ExtDimensions AS Analytics";

EndFunction
