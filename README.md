# Jira to ActionableAgile data converter

Конверирует данные выбранной доски Atlassian Jira в формат для загрузки в сервис ActionableAgile

## Использование

java -jar j2aa.jar [options]

где [options]:

--profile=<your-default-profile-file.xml> - загружать заданный профиль подключения

## Файл конфигурации

Расположение -домашняя директория пользователя, файл .j2aa

${user.home}/.j2aa

## Переменные файла конфигурации (ключ=значение)

### Переменные по-умолчанию

**username** = имя пользователя

**password** = пароль пользователя

**jira-fields** = список полей через запятую для выгрузки. Допустимые значения: issuetype, labels, epic, priority, components, project, assignee, reporter, projectkey, fixVersions, summary

**board-url** = адрес доски

**sub-filter** = дополнительный jql фильтр

**output-file** = файл для экспорта

**export-blockers-calendar** = выгружать календарь блокировок

### Глобальные переменные

**use-max-column** = метод расчета lead time в случае "обратных" движений по доске. Допустимые значения false (
по-умолчанию) или true

**url-path-prefix** = префикс к стандартному адресу jira REST API ("<url-path-prefix>/rest/agile/1.0"). 
По умолчанию пустая строка

**javax.net.ssl.trustStore** = путь к jqk файлу с доверенными сертификатами. Символы \ следуе заменить на двойной (\\)
или обратный (/)

**javax.net.ssl.trustStorePassword** = пароль к файлу с доверенными сертификатами

**javax.net.ssl.trustStoreType** = (optional) тип хранилища доверенных сертификатов (например Windows-ROOT, JKS etc)