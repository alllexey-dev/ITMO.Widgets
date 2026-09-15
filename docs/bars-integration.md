# Зачётка: наложение БАРС

Реализовано 15 сентября 2026 года. Исходные wire-наблюдения: [API БАРС](bars-api.md).

## Что видит пользователь

- В шапке зачётки есть чип **БАРС**. Список предметов, периоды, физкультура
  и спорт всегда берутся из MyITMO. При включённом чипе предметы, найденные
  в БАРС за тот же учебный год и полугодие, показывают балл, оценку, попытку
  и дерево работ из БАРС: там они появляются раньше.
- Сопоставление идёт по нормализованному названию (регистр, пробелы, ё/е).
  Несопоставленный предмет остаётся с данными MyITMO и получает пометку
  «нет в БАРС»; у физкультуры пометки нет, её в БАРС не бывает. Одинаковые
  названия с любой стороны не сопоставляются.
- Пустой журнал БАРС (`total = 0` без единой работы) показывается как
  «без баллов», а не как 0.
- Список MyITMO показывается сразу, спиннер обновления остаётся, пока не ответит
  БАРС; журналы всех предметов запрашиваются параллельно. Пометка «нет в БАРС»
  появляется только после успешного ответа БАРС за этот период.
- Ошибка БАРС не прячется и не блокирует экран: список остаётся с данными
  MyITMO, внизу snackbar «БАРС недоступен». Если сессия ITMO.ID закончилась,
  snackbar предлагает «Войти в БАРС» и открывает `BarsLoginActivity`.
- Диалогов подтверждения нет. Смена периода в приложении меняет выбранный
  период и в веб-БАРС: это его серверная настройка, других способов чтения нет.

## Сессия

Токен БАРС живёт 30 минут, refresh-токена нет, сервер не продлевает его по
заголовку ответа (проверено live: заголовок повторяет тот же токен). Обменять
токен MyITMO на БАРС нельзя (`token-exchange` → `access_denied`, MyITMO
access token → 401).

Зато сессия ITMO.ID в WebView приложения живёт около 90 дней (`KEYCLOAK_IDENTITY`,
`KEYCLOAK_REMEMBER_ME`) и остаётся после входа в само приложение. Поэтому
`BarsWebSilentLogin` при отсутствии или истечении токена загружает официальный
OIDC-URL клиента `bars` в скрытом WebView, перехватывает только точный callback
`https://bars.itmo.ru/rest/login` с проверкой `state`, и `BarsClient` обменивает
код через `/backend/rest/login`. Никакого JavaScript-моста и чтения localStorage.
Если ITMO.ID отрисовал форму входа, тихий вход возвращает `null`, запрос
завершается `Unauthorized`, и пользователь видит кнопку входа.

`BarsClient.Account`: один `current_user` в начале (владелец и выбранный период),
`config/personal` только при отличии года/сезона, повторное чтение `current_user`
только после записи. Вызовы внутри одного блока могут идти параллельно; при 401
тихий вход выполняется один раз под mutex, остальные вызовы повторяются с новым токеном.
Токен хранится зашифрованным в `noBackupFilesDir/bars_tokens.enc` вместе с ISU
владельца; `BarsPreferenceRepositoryImpl` как `SessionDataCleaner` удаляет токен
и чип при выходе из аккаунта.

## Архитектура

```text
RecordbookFragment → RecordbookViewModel ─┬─ RecordbookRepository (MyITMO)
                                          ├─ BarsRecordbookRepository → BarsClient → BarsApi
                                          └─ BarsPreferenceRepository (DataStore)
RecordbookBarsMerge.apply(myItmo, bars)   — чистое слияние по названию
RecordbookSubjectFragment → RecordbookSubjectViewModel (ссылка на журнал БАРС в аргументах)
```

- `BarsRecordbookRepository.getSubjects(period)` переводит `RecordbookPeriod`
  в год и сезон БАРС (нечётный семестр = осень) и возвращает предметы с
  `BarsJournalReference(planId, type, identifier, yearStart, semester)`.
- `getSubject(reference)` одним запросом журнала отдаёт и сводку, и работы.
- MyITMO `est_id`/`discipline_id` в БАРС не подставляются; в слитом предмете
  остаются идентичность, преподаватель и дата экзамена MyITMO.
- Маппер: серверный `marks.total`, последнее однозначное активное подтверждение
  (словесная оценка «Удвл., E» переводится в код «3/E», зачёт остаётся словом),
  работы по `checkpoint_id`, дополнительные баллы отдельной строкой, неявка
  не становится сдачей. Планы с `has_course_project` пока отклоняются.

## Проверка

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.alllexey.itmowidgets.feature.recordbook.RecordbookVisualTest,dev.alllexey.itmowidgets.feature.recordbook.RecordbookBarsVisualTest
```

Юнит-тесты покрывают слияние, переключение чипа и его сохранение, ошибку БАРС без
подмены MyITMO, тихий вход и повтор при 401, выбор периода, маппинг журнала.
Инструментальная проверка использует синтетические данные и `RecordbookPreviewActivity`.
