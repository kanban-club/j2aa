# Jira to ActionableAgile data converter
Конверирует данные выбранной доски Atlassian Jira в формат 
для загрузки в сервис ActionableAgile

## Использование
java -jar j2aa.jar [options]

где [options]:

--profile=<your-default-profile-file.xml> -загружать заданный профиль подключения

## Файл конфигурации
Расположение -домашняя директория пользователя,
файл .j2aa

${user.home}/.j2aa


### Переменные файла конфигурации (ключ=значение)
**default.username** - имя пользователя jira по-умолчанию

**default.password** - пароль пользователя jira по-умолчанию

**converter.use-max-column**=false/true - метод расчета lead time в случае "обратных" движений по доске (по-умолчанию false)

**converter.jira-fields** - список полей для выгрузки. 
Допустимые значения: issuetype, labels, epic, priority, components, project, assignee, reporter, projectkey, fixVersions, summary
Значения по-умолчанию: issuetype, labels, epic