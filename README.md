# Jira to ActionableAgile data converter
Конверирует данные выбранной доски Atlassian Jira в формат 
для загрузки в сервис ActionableAgile

## Использование
java -jar j2aa.jar [options]

где [options]:

--profile=<your-default-profile-file.xml> - загружать заданный профиль подключения

## Файл конфигурации
Расположение -домашняя директория пользователя,
файл .j2aa

${user.home}/.j2aa


## Переменные файла конфигурации (ключ=значение)
### Переменные по-умолчанию
**username** = имя пользователя

**password** = пароль пользователя

**jira-fields** = список полей через запятую для выгрузки. 
Допустимые значения: issuetype, labels, epic, priority, components, project, assignee, reporter, projectkey, fixVersions

**board-url** = адрес доски

**sub-filter** = дополнительный jql фильтр

**output-file** = файл для экспорта

### Глобальные переменные

**use-max-column** = метод расчета lead time в случае "обратных" движений по доске. Допустимые значения false (по-умолчанию) или true