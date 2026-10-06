<h1 align="center">ITMO.Widgets</h1>

<p align="center">
  <strong>Расписание, спорт, зачётка и QR-пропуск ИТМО — на главном экране Android</strong>
</p>

<p align="center">
  <a href="https://github.com/alllexey-dev/ITMO.Widgets/releases/latest"><img src="https://img.shields.io/github/v/release/alllexey-dev/ITMO.Widgets?style=flat-square&color=blue" alt="Latest release" /></a>
  <a href="https://github.com/alllexey-dev/ITMO.Widgets/releases"><img src="https://img.shields.io/github/downloads/alllexey-dev/ITMO.Widgets/total?style=flat-square&color=orange" alt="Downloads" /></a>
  <a href="https://github.com/alllexey-dev/ITMO.Widgets/actions/workflows/android-ci.yml?query=branch%3Amaster"><img src="https://img.shields.io/github/actions/workflow/status/alllexey-dev/ITMO.Widgets/android-ci.yml?branch=master&style=flat-square&label=CI" alt="CI" /></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+" />
  <a href="LICENSE"><img src="https://img.shields.io/github/license/alllexey-dev/ITMO.Widgets?style=flat-square" alt="MIT" /></a>
</p>

<p align="center">
  <a href="https://github.com/alllexey-dev/ITMO.Widgets/releases/latest"><strong>Скачать APK</strong></a>
  ·
  <a href="https://widgets.alllexey.dev">Сайт</a>
  ·
  <a href="https://t.me/itmowidgets">Telegram</a>
  ·
  <a href="https://widgets.alllexey.dev/privacy.html">Политика конфиденциальности</a>
</p>

> Неофициальное приложение. Не связано с Университетом ИТМО.

Приложение для студентов ИТМО. Ближайшая пара, расписание на день и QR-пропуск живут в виджетах на домашнем экране, а внутри — лента на сегодня, запись и автозапись на спорт, зачётка с БАРС и расписание друзей. Вход через ITMO.ID, пароль приложению не нужен.

<p align="center">
  <img height="420" alt="Главный экран" src="https://widgets.alllexey.dev/img/night/home.webp" />
  <img height="420" alt="Расписание" src="https://widgets.alllexey.dev/img/night/schedule.webp" />
  <img height="420" alt="Запись на спорт" src="https://widgets.alllexey.dev/img/night/sport-catalog.webp" />
  <img height="420" alt="Зачётка" src="https://widgets.alllexey.dev/img/night/recordbook.webp" />
</p>

<p align="center">
  <img height="70" alt="Виджет «Пара»" src="https://widgets.alllexey.dev/img/night/widget_single_lesson_preview.webp" />
  <img height="150" alt="Виджет «Расписание»" src="https://widgets.alllexey.dev/img/night/widget_lesson_list_preview.webp" />
  <img height="100" alt="Виджет «QR-код»" src="https://widgets.alllexey.dev/img/night/widget_qr_code_preview.webp" />
</p>

## Возможности

### Главный экран

Лента на сегодня: пары с текущей парой и прогрессом, ожидающие записи на спорт, изменения в расписании, новые оценки, баллы за семестр и заявки в друзья. Когда день закончился, лента показывает завтра. Ненужные карточки выключаются в настройках.

### Виджеты

- **Пара** — текущая или следующая пара; по желанию переключается на следующую за 15 минут до конца текущей.
- **Расписание** — все пары на день, после последней пары переключается на завтра.
- **QR-код** — пропуск в корпус. Код спрятан за спойлером и открывается по касанию; спойлер можно заменить своей картинкой.

Три размера текста, скрытие преподавателя и прошедших пар, динамические цвета Material You. Данные кэшируются и доступны без сети; виджеты обновляются сами.

Плитка «QR-пропуск» в шторке и ярлыки «QR-пропуск» и «Сегодня» на значке приложения.

### Расписание

Пары по дням с тапом в детали: тип, формат, преподаватель, аудитория и корпус с кнопкой «Открыть на карте», ссылка на видеозвонок, заметка. С «Подключением к ITMO.Widgets» — «Друзья на паре». Записи и очереди на спорт видны прямо в расписании и отменяются оттуда же.

**Изменения расписания.** Телефон сам проверяет своё расписание на неделю вперёд и присылает уведомление о переносах и отменах; изменённые пары отмечены в расписании.

**Календарь.** Синхронизация пар на 4 недели вперёд в отдельный календарь ITMO.Widgets на телефоне и выгрузка в `.ics` за выбранный период.

### Спорт и автозапись

- Календарь занятий с фильтрами по виду спорта, корпусу и дням; свободные места и записи друзей на карточке.
- Запись в один тап и отмена.
- **Очередь на место** — если занятие заполнено, приложение запишет вас, как только место освободится.
- **Очередь на будущее** — запись на занятие, которого ещё нет в расписании, за две недели вперёд (с месячным лимитом).
- Баллы за семестр, посещения и прогресс до зачёта.

Автозапись работает через сервер проекта и требует «Подключения к ITMO.Widgets» (см. ниже). Результат приходит уведомлением.

### Зачётка

Предметы семестра с баллами и контрольными точками из ИСУ. Переключатель **БАРС** накладывает баллы и работы из БАРС на тот же список. У предмета своя страница: контрольные точки, преподаватели, ближайшие пары и ссылки. Физкультура берёт баллы из «Моего спорта».

**Новые оценки.** Новые и изменённые оценки My ITMO, БАРС и Google Таблиц приходят уведомлением.

**Свои баллы из таблицы.** Если преподаватель ведёт баллы в публичной Google Таблице, приложение находит в ней вашу строку и показывает итог на странице предмета. Таблица читается прямо с телефона.

### Ссылки и отзывы

- **Ссылки к предметам** — таблица баллов, очередь, материалы, записи лекций. Видны только вам, вашему потоку или всем; ссылки для всех проходят проверку, за полезные голосуют.
- **Отзывы о преподавателях** — в профиле преподавателя. Свой отзыв по умолчанию анонимный и публикуется после проверки, за полезные голосуют.

Ссылки для потока и для всех и отзывы работают с «Подключением к ITMO.Widgets».

### Друзья

Заявки в друзья, профили, поиск людей по имени или ИСУ. Расписание и спорт друга рядом со своим. Кто видит ваше расписание, спорт и список друзей, решаете вы: все, друзья или никто.

Ссылки на профиль и занятие по спорту открываются в приложении: кнопка «Поделиться» в профиле и в карточке занятия.

**Вход на сайт.** Веб-версия на сайте открывается без пароля: вход подтверждается в приложении («Профиль → Вход на сайт») по QR-коду или коду.

## Установка

1. Скачайте `itmo-widgets-v*.apk` из [последнего релиза](https://github.com/alllexey-dev/ITMO.Widgets/releases/latest).
2. Разрешите установку из этого источника и откройте файл.
3. При первом запуске приложение проведёт по виджетам, «Подключению к ITMO.Widgets» и уведомлениям.

Нужен Android 8.0 и новее. О новых версиях приложение сообщает само. Публикация в Google Play готовится.

## Вход

**ITMO.ID.** Кнопка «Войти через ITMO.ID» открывает официальную страницу университета. Логин и пароль вводятся только там; приложение получает токен и хранит его на устройстве.

**Другой способ входа.** Если страница входа не работает, токен можно ввести вручную:

1. Откройте [my.itmo.ru](https://my.itmo.ru/) в браузере на компьютере.
2. `F12` → вкладка **Application** → **Cookies** → `https://my.itmo.ru`.
3. Скопируйте значение `auth.refresh_token.itmoId` и вставьте его в диалог «Другой способ входа».

## Данные и приватность

Расписание, зачётка, оценки и QR-пропуск загружаются с My ITMO и БАРС прямо на телефон; токены и кэш хранятся на устройстве. Проверки изменений расписания и новых оценок, таблицы баллов и календарь тоже работают на телефоне.

Друзья, очереди и автозапись на спорт, ссылки к предметам, отзывы, вход на сайт и push-уведомления работают через сервер проекта и включаются отдельным переключателем **Подключение к ITMO.Widgets**. По умолчанию он выключен. Сервер хранит:

- ИСУ, имя и фото из ITMO.ID, учебные группы;
- друзей, заявки и настройки приватности;
- ваши пары за открытые в приложении дни — для друзей, ссылок потока и проверки отзывов;
- записи и очереди на спорт;
- ссылки к предметам, отзывы, голоса и жалобы;
- токен уведомлений и модель телефона, сессии входа на сайт.

Токен ITMO.ID сервер проверяет и не сохраняет, запросы к My ITMO от вашего имени не делает. Рекламы и аналитики нет. Подробности — в [политике конфиденциальности](https://widgets.alllexey.dev/privacy.html), удаление аккаунта — по [запросу](https://widgets.alllexey.dev/delete-account) («Настройки → Подключение к ITMO.Widgets → Удалить аккаунт ITMO.Widgets»). Исходный код сервера открыт.

## Экосистема

| Репозиторий | Что делает |
|---|---|
| [ITMO.Widgets](https://github.com/alllexey-dev/ITMO.Widgets) | Android-приложение (этот репозиторий) |
| [itmo-widgets-backend](https://github.com/alllexey-dev/itmo-widgets-backend) | Сервер: друзья, приватность, очереди на спорт, уведомления |
| [itmo-widgets-core](https://github.com/alllexey-dev/itmo-widgets-core) | Типизированный контракт и клиент сервера |
| [my-itmo-api](https://github.com/alllexey-dev/my-itmo-api) | Java-клиент MyITMO и БАРС |

## Разработчикам

Gradle запускается одной командой `scripts/verify.sh`. Она берёт слот сборки `scripts/slot.sh` (на macOS машина делится между параллельными сборками; на Linux и в CI команда идёт сразу), подставляет JDK, Android SDK и MyItmoApi и последней строкой печатает `VERIFY A <режим> PASS|FAIL <секунды>s <коммит>`.

```bash
scripts/verify.sh quick                       # check-docs, тесты всех модулей, Konsist, lintGithubDebug, debug-сборки github и play
scripts/verify.sh full                        # quick, lintPlayDebug и iOS klibs
scripts/verify.sh klibs [<module>]            # klibs iosSimulatorArm64 всех shared-модулей или одного, без Xcode
scripts/verify.sh shots <module>|app|all      # скриншот-тесты Roborazzi на JVM; --record перезаписывает эталоны
scripts/verify.sh ui <Class>[,<Class>...]|all # инструментальные тесты :app на эмуляторе пула
scripts/verify.sh ui @platform                # платформенные тесты под Android Test Orchestrator
scripts/verify.sh ship                        # проверка перед релизом через scripts/ship-check.sh
scripts/verify.sh run -- <аргументы Gradle>   # отдельные задачи Gradle
```

- **JDK.** Для запуска Gradle нужен JDK 17+. На macOS `verify.sh` сам берёт JDK 21 и `~/Library/Android/sdk`, если `JAVA_HOME` и `ANDROID_HOME` не заданы. Демон Gradle работает на JetBrains JDK 21 из `gradle/gradle-daemon-jvm.properties` и скачивает его сам.
- **CI.** [android-ci](.github/workflows/android-ci.yml) на каждом PR и push в `v2.3/next` и `master` запускает `verify.sh quick`, `verify.sh shots all` и iOS klibs; значок CI вверху страницы показывает `master`. Ночной [android-nightly](.github/workflows/android-nightly.yml) гоняет платформенные тесты на Gradle Managed Device.
- **MyItmoApi.** Приложение собирается с MyItmoApi 2.x из исходников, коммит закреплён в `gradle/myitmoapi.ref`. Склонируйте [my-itmo-api](https://github.com/alllexey-dev/my-itmo-api), переключитесь на этот коммит и передайте путь: `MYITMOAPI_DIR=<checkout> scripts/verify.sh quick` или `scripts/verify.sh run -- -PmyItmoApiDir=<checkout> <задачи>`. Так же делает CI. `~/.m2` и `publishToMavenLocal` не нужны. Релизные сборки берут опубликованный артефакт с `-PmyItmoApiFromCentral=true`, когда MyItmoApi 2.x выйдет в Maven Central.
- **Скриншоты.** `scripts/verify.sh shots <module>` сравнивает экраны с эталонами в `screenshots/` модуля, `shots all` проверяет все модули, как CI. После намеренного изменения эталоны перезаписывает `--record`; подробности в [docs/design.md](docs/design.md#running-the-visual-tests).
- **Инструментальные тесты** запускаются только на эмуляторе пула, не на телефоне: `scripts/emulator.sh up --api 35` (или `--api 30`) печатает `ANDROID_SERIAL=emulator-<port>`, затем `ANDROID_SERIAL=emulator-<port> scripts/verify.sh ui <Class>`, в конце `scripts/emulator.sh down`. `verify.sh ui` отказывается работать с чем угодно, кроме эмулятора. AVD пула создаются один раз командой `scripts/emulator.sh init`. Список платформенных тестов для `ui @platform` лежит в `app/src/androidTest/platform-tests.txt`.

Debug-сборка ходит на dev-сервер, release — на боевой. Сборка `github` выходит APK на GitHub, `play` — AAB для Google Play. Описание архитектуры, настроек и каждой фичи — в [docs/README.md](docs/README.md), правила для контрибьюторов и агентов — в [AGENTS.md](AGENTS.md), история изменений — в [CHANGELOG.md](CHANGELOG.md).

## Обратная связь

Ошибки и идеи — в [Issues](https://github.com/alllexey-dev/ITMO.Widgets/issues) или в канале [@itmowidgets](https://t.me/itmowidgets). В приложении есть «Журнал ошибок» (Настройки → Обслуживание): его содержимое без токенов и личных данных можно приложить к отчёту.

## Лицензия

[MIT](LICENSE). Приложение не связано с Университетом ИТМО.

## Star History

<a href="https://www.star-history.com/?repos=alllexey-dev%2Fitmo.widgets&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=alllexey-dev/itmo.widgets&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=alllexey-dev/itmo.widgets&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=alllexey-dev/itmo.widgets&type=date&legend=top-left" />
 </picture>
</a>
