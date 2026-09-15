# БАРС: каталог методов публичного веб-клиента

Приложение к [описанию API](bars-api.md), исследовано 15 сентября 2026 года.
Источник — [сервисный модуль веб-клиента](https://bars.itmo.ru/static/js/main.f1f7c69c.chunk.js).

Это полный каталог 81 метода объединённого service object этого JS-файла, **не
спецификация всех серверных контроллеров**. Загрузка других разделов сайта может
выявить дополнительные методы. Наличие метода в bundle не доказывает, что сервер
его поддерживает или что он доступен обучающемуся.

## Как читать таблицы

- REST base: `https://bars.itmo.ru/backend/rest/`; пути таблиц относительны к нему.
- **H** — этот метод встречается в HAR, его детали и wire-схема описаны отдельно.
- **J** — только найден в клиенте. Никаких live-вызовов этих дополнительных методов
  не выполнялось. DTO, статусы и permissions неизвестны, если явно не сказано иное.
- `{p1}`, `{p2}` и т. д. — позиционные аргументы JS-метода, когда их серверная
  семантика не установлена. Это намеренно не переименовано в выдуманный studentId.
- `body` означает, что JS передаёт аргумент в Axios data, **не** подтверждённую
  JSON-схему. «Без body» означает, что wrapper не передаёт Axios data.
- Разделение на чтение/изменение определяется назначением, а не только HTTP-глаголом.
  **Нельзя автоматически воспроизводить все GET из каталога.** `set_me_to`,
  импорт RPD, импорт Google Sheet и операции токенов могут менять состояние.
- Фильтры, если не оговорено иное, условно включаются клиентом при непустом значении.
  Реальные правила обязательности, default и лимиты сервера не установлены.

## 1. Авторизация и конфигурация

| JS-метод | HTTP и путь | Аргументы / результат клиента | Evidence |
|---|---|---|---|
| `userLogin` | POST `login` | body из аргумента; альтернативный/legacy login, не использовать для сбора ITMO-пароля | J |
| `userLogout` | GET внешнего OIDC logout URL | URL из runtime-config, не `rest/logout`; побочный эффект выхода | J |
| `getCurrentUser` | GET `users/current_user/` | Текущий пользователь | H/L |
| `getUserLogin` | GET `login` | Query `code`, `customRedirectUri`; access token в response header | H |
| `setUser` | GET `set_me_to/{p1}` | **Изменяет контекст пользователя**, не read-only | J |
| `setConfig` | POST `config/multiple` | body из аргумента, общая конфигурация | J |
| `getTermConfig` | GET `config/` | Массив настроек | H |
| `setConfigValue` | POST `config` | body из аргумента, общая конфигурация | J |
| `setPersonalConfig` | POST `config/personal` | `{name, value}`; изменение персонального периода | H |
| `getPersonalConfig` | GET `config/personal` | DTO не изучен; также есть поле в current_user | J |
| `deletePersonalConfig` | DELETE `config/personal/{p1}` | Удаление выбранной настройки | J |

Веб-клиент берёт/обновляет полный Bearer header через общий interceptor.
Вводные auth details и проверка MyITMO token находятся в основном документе.
POST login, смена пользователя, logout и изменение config не воспроизводились.

## 2. Типы контрольных работ

| JS-метод | HTTP и путь | Аргументы | Evidence |
|---|---|---|---|
| `getCheckpointTypes` | GET `checkpoint_types` | Query `name`, `type`; H знает `regular` и `final` | H |
| `createCheckpointType` | POST `checkpoint_types/{p2}` | body `{name: p1}` | J |

## 3. Дисциплины

| JS-метод | HTTP и путь | Query / body | Evidence |
|---|---|---|---|
| `getDisciplineAccess` | GET `discipline/{p1}/access` | Права на дисциплину, DTO неизвестен | J |
| `setDisciplineAccess` | POST `discipline/{p1}/access` | body p2 | J |
| `getUserDisciplines` | GET `journal/disciplines` | `withCheckpointPlansOnly`, `distinctByCheckpointPlans`, `name`, `identifier`, `type`, `size` | H |
| `getUserDisciplinesOld` | GET `disciplines` | Те же query; legacy wrapper | J |
| `getAllDisciplines` | GET `disciplines/all` | `filter` из name, `type`, `identifier`, `excludedIds` через запятую | J |
| `getDisciplineById` | GET `disciplines/{p1}` | Без query | J |
| `getDisciplinesWithRealizer` | GET `disciplines/realizer` | `name` из searchTerm, `type`, `identifier`, `excludedIds`, `realizerPeopleId`; cancelToken локальный, не query | J |
| `getDisciplines` | GET `disciplines_for_plan/` | `flowId` | J |

У `getUserDisciplines` новый поиск отменяет предыдущий. В generic query helper
массивы объединяются запятыми, false/0/пустые значения опускаются. Это описание J,
не рекомендация воспроизводить его отсутствие корректного URL encoding.
В нативном клиенте применять безопасные `@Query`/URL builder и не конкатенировать
пользовательский ввод вручную.

## 4. Потоки, группы и образовательные программы

| JS-метод | HTTP и путь | Query / body | Evidence |
|---|---|---|---|
| `getFlowLinks` | GET `flows/plans` | `name` | J |
| `getFlows` | GET `flows` | `name`, `all` | J |
| `saveFlowTableLink` | POST `flows/{p1}/plans/{p2}` | Без body | J |
| `deleteFlowTableLink` | DELETE `flows/{p1}/plans/{p2}` | Связь потока и плана | J |
| `getGroups` | GET `groups` | Без query | J |
| `getGroupsWithDisciplineId` | GET `disciplines/{disciplineId}/group` | Без query | J |
| `getGroupsAndFlowsNames` | GET `checkpoint_plans/{planId}/group_and_flow_names` | Без query | J |
| `getGroupsAndFlows` | GET `journal/groups-and-flows` | `disciplineId`, `checkpointPlanId`, `name` | H |
| `getEducationProgram` | GET `educational_programs/{p1}` | `term`; массив через запятую, при отсутствии p2 клиент передаёт буквальный `term=null` | J |

Типы ответов кроме `getGroupsAndFlows` в этой сессии не наблюдались. Наличие
endpoint-а всех групп не означает право получать чужие оценки.

## 5. Журналы, баллы, подтверждения, история

### Чтение

| JS-метод | HTTP и путь | Query / условия | Evidence |
|---|---|---|---|
| `getJournal` | GET `marks/{planId}/{type}/{identifier}` | При заданном planId; **не студент-ограниченный вариант** | J |
| `getJournal` (ветка без плана) | GET `marks/{disciplineId}/{type}/{identifier}/auto` | disciplineId — четвёртый аргумент wrapper | J |
| `getJournalForStudent` | GET `marks/{planId}/{type}/{identifier}/student` | Без studentId, текущий обучающийся | H/L |
| `getJournalMarks` | GET `marks/{p1}/{p2}/{p3}/student/{p4}` | Явно адресованный студент; не вызывать с чужими ID | J |
| `getMarks` | GET `marks/{p1}/` | Семантика p1 и DTO не установлены | J |
| `getMarkHistory` | GET `marks/{p1}/{p2}/{p3}/student/{p4}/history` | Query `checkpointId` из p5 | J |
| `getApproveHistory` | GET `marks/{p1}/{p2}/approval/{p3}/history` | История подтверждения, DTO не изучен | J |
| `getCheckpointPlans` | GET `journal/checkpoint-plans` | wrapper берёт name, но добавляет как сырой query-фрагмент без `name=`; корректный серверный контракт не установлен | J |

Нельзя заменить `/student` общим журналом и затем отфильтровать других студентов
в Android: приватность должна быть обеспечена сервером. Дополнительные endpoints
истории указаны для полноты, не как подтверждённый источник всех попыток.

### Изменение — вне задачи приложения

| JS-метод | HTTP и путь | Query / body | Evidence |
|---|---|---|---|
| `setAdditionalPoints` | POST `marks/additional/{p1}/{p2}` | body p3 | J |
| `setPersonPlanMark` | POST `marks/{p1}/{p2}/{p3}/{p4}` | body p5 | J |
| `setPersonFinalMark` | POST `marks/final/{p1}/{p2}` | body p3 | J |
| `confirmMarks` | POST `marks/{p1}/{p2}/{p3}/{p4}/approval/{p5}` | Опционально course из p6, без body; в J перед `?course=true` есть пробел | J |
| `setAgreement` | POST `marks/{p1}/student/agreement` | Без body, согласие/намерение повышения результата | J |
| `approveAll` | POST `marks/{p1}/{p2}/{p3}/approval/{p4}` | `studentIds` (p5) через запятую; `course` (p6); без body | J |
| `clearJournalCache` | DELETE `journal/cache` | Query `term` из p2, `year` из p1 | J |

У `approveAll` J добавляет `&course=` после необязательного `?studentIds=`.
Без studentIds это потенциально некорректная сборка URL; нельзя считать её
проверенной серверной спецификацией. Права, nullable и форматы body всех этих
операций не исследовались. Ни одна операция не вызывалась.

## 6. Планы контрольных мероприятий и персональные планы

| JS-метод | HTTP и путь | Query / body | Evidence |
|---|---|---|---|
| `getPlanById` | GET `checkpoint_plans/{planId}` | Полный план | H |
| `getPlansByDisciplineAndPrograms` | GET `checkpoint_plans/discipline/{p1}` | `programIds` из p2, только если p2 — массив, через запятую | J |
| `createPlan` | POST `checkpoint_plans` | body p1 | J |
| `updatePlan` | PUT `checkpoint_plans/{p1.id}` | body p1 | J |
| `deletePlan` | DELETE `checkpoint_plans/{p1}` | Без body | J |
| `searchPlans` | GET `checkpoint_plans` | `name`, `page`, `size`, `sort`, `start_date`, `end_date` | J |
| `importRpd` | GET `checkpoint_plans/rpd/constructor/import` | Query `ids` из p1; **импорт, не read-only** | J |
| `getPersonalPlan` | GET `personal_plans/{p1}` | DTO неизвестен | J |
| `getPersonalPlanByDisciplineAndGroup` | GET `personal_plans/{p1}/{p2}` | Дисциплина и группа по имени wrapper; wire-формат ID не проверен | J |
| `savePersonalPlan` | POST `personal_plans` | body p1 | J |

Только `getPlanById` имеет наблюдённый ответ. Query helper `searchPlans` переводит
start/end через Moment `valueOf()` в epoch milliseconds. Номер первой страницы,
default size, формат sort и включительность дат сервером не подтверждены;
наличие JS-метода не заменяет HAR поискового сценария. Поля ответа, например
пустые `programs` и `components`, нельзя типизировать по имени этих методов.

## 7. Сроки и сессия

| JS-метод | HTTP и путь | Query / body | Evidence |
|---|---|---|---|
| `getDeadlines` | GET `deadline/{type}/{identifier}/{planId}` | Ответ в H только `[]` | H |
| `getUserDeadlines` | GET `deadline` | Ответ в H только `[]` | H |
| `postSingleDeadline` | POST `deadline/single/{p1}/{p2}` | body p3 | J |
| `postDeadlines` | POST `deadline/{p1}/{p2}` | body p3 | J |
| `getStudentSession` | GET `session/{p1}/{p2}/{p3}` | Query `course=true`, только если p4 равен строке `course` | J |
| `setCustomScheduleDate` | POST `session/{p1}` | body p2 | J |
| `setCustomScheduleForAll` | POST `session/{p1}/{p2}/{p3}` | body p4 | J |

Назначение session-операций следует из wrapper-имён; форматы дат и связь с
расписанием MyITMO не проверены. Время работы/редактирования оценки не заменяет
расписание сессии.

## 8. Пользователи, роли и представители

| JS-метод | HTTP и путь | Query / body | Evidence |
|---|---|---|---|
| `getAllUsers` | GET `users` | `filter` | J |
| `getStudents` | GET `users/students` | `filter` | J |
| `getParents` | GET `users/{p1}/parent` | Доступ и DTO не проверены | J |
| `addParent` | POST `users/{p1}/parent` | body p2 | J |
| `getUserRoles` | GET `users/{p1}/roles` | Роли пользователя | J |
| `saveUserRoles` | POST `users/{p1}/roles` | body p2 | J |
| `setUserRole` | POST `users/set_selected_role` | Объект выбранной уже доступной роли | H |
| `getUsersByRole` | GET `users/by_role/{p1}` | `filter` из p2 | J |
| `getGroupsAndFlowsByName` | GET `users/white_list/{p1}` | `filter` из p2, `realizerPeopleId` из p3 | J |

Не вызывались списки посторонних пользователей, просмотр/изменение чужих ролей
или представителей. Для отображения собственной зачётки они не нужны.

## 9. Отчёты, тесты и регистрационные токены

| JS-метод | HTTP и путь | Query / body | Evidence |
|---|---|---|---|
| `getReport` | POST `report/{type}` | default type `current_control`, default body `{}` | J |
| `getReportFile` | POST `report/{type}/export` | Те же default; клиент запрашивает `responseType: arraybuffer`, формат файла неизвестен | J |
| `getReportTypes` | GET `report/type` | Клиент явно задаёт JSON Content-Type | J |
| `addTest` | POST `tests` | body p1 | J |
| `getTests` | GET `tests` | `year` из p1 | J |
| `createNewToken` | POST `registration/token` | Без body; создание секрета | J |
| `deleteUserToken` | DELETE `registration/token/{p1}` | Удаление секрета | J |
| `getUserTokens` | GET `registration/token` | Потенциально чувствительный ответ | J |
| `registerUserWithToken` | POST `registration/{p1}` | body p2; регистрационная операция | J |

Токены registration не являются подтверждённой альтернативой OIDC login.
Их не запрашивали, не создавали и не удаляли. POST report может генерировать
состояние на сервере; отсутствие очевидного изменения оценки не делает его
автоматически разрешённым в read-only исследовании.

## 10. Google Sheets

| JS-метод | HTTP и путь | Query | Evidence |
|---|---|---|---|
| `importJournal` | GET `google/import/sheet` | `spreadSheetUrlWithSheetGid`, опционально `validateOnly=true` | J |
| `exportJournal` | GET `google/export/sheet` | Все truthy свойства переданного объекта; имена зависят от вызывающего кода | J |

Импорт J кодирует переданный URL и заменяет `#` на `%23`, затем дописывает
validateOnly. Это серверный импорт БАРС и **не** пользовательская локальная
интеграция публичных Google Sheets из roadmap ITMO.Widgets. Endpoint-ы не вызывали:
неизвестны permissions, побочные эффекты, проверка URL, формат результата и
то, создаёт ли экспорт внешний документ. Не переносить такую схему на Backend
ITMO.Widgets и не отправлять сюда произвольные пользовательские URL.

## 11. Граница применимости каталога

Для собственной зачётки подтверждены только auth, current_user, настройки из H,
каталоги journal, student journal, checkpoint plan/types и пустые deadlines.
Остальная поверхность приведена для полноты исследования JS, а не для добавления
в приложение. Реализация новых методов требует разрешённого сценария, отдельного
наблюдения wire-контракта и тестов авторизации. Нельзя заявлять полное покрытие
Backend БАРС, опираясь только на этот bundle.
