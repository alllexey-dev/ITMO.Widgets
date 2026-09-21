<h1 align="center">ITMO.Widgets</h1>

<p align="center">
  <strong>Расписание, спорт, зачётка и QR-пропуск ИТМО — на главном экране Android</strong>
</p>

<p align="center">
  <a href="https://github.com/alllexey-dev/ITMO.Widgets/releases/latest"><img src="https://img.shields.io/github/v/release/alllexey-dev/ITMO.Widgets?style=flat-square&color=blue" alt="Latest release" /></a>
  <a href="https://github.com/alllexey-dev/ITMO.Widgets/releases"><img src="https://img.shields.io/github/downloads/alllexey-dev/ITMO.Widgets/total?style=flat-square&color=orange" alt="Downloads" /></a>
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

Неофициальное приложение для студентов ИТМО. Ближайшая пара, расписание на день и QR-пропуск живут в виджетах на домашнем экране, а внутри — лента на сегодня, запись и автозапись на спорт, зачётка с БАРС и расписание друзей. Вход через ITMO.ID, пароль приложению не нужен.

<p align="center">
  <img height="420" alt="Главный экран" src="https://widgets.alllexey.dev/img/night/home.webp" />
  <img height="420" alt="Расписание" src="https://widgets.alllexey.dev/img/night/schedule.webp" />
  <img height="420" alt="Детали пары" src="https://widgets.alllexey.dev/img/night/lesson.webp" />
  <img height="420" alt="Запись на спорт" src="https://widgets.alllexey.dev/img/night/sport-catalog.webp" />
  <img height="420" alt="Зачётка" src="https://widgets.alllexey.dev/img/night/recordbook.webp" />
</p>

<p align="center">
  <img height="150" alt="Виджет «Пара»" src="https://widgets.alllexey.dev/img/night/widget_single_lesson_preview.webp" />
  <img height="150" alt="Виджет «Расписание»" src="https://widgets.alllexey.dev/img/night/widget_lesson_list_preview.webp" />
  <img height="150" alt="Виджет «QR-код»" src="https://widgets.alllexey.dev/img/night/widget_qr_code_preview.webp" />
</p>

## Возможности

### Главный экран

Лента на сегодня: пары с текущей парой и прогрессом, ожидающие записи на спорт, баллы за семестр и заявки в друзья. Когда день закончился, лента показывает завтра. Ненужные карточки выключаются в настройках.

### Виджеты

- **Пара** — текущая или следующая пара; по желанию переключается на следующую за 15 минут до конца текущей.
- **Расписание** — все пары на день, после последней пары переключается на завтра.
- **QR-код** — пропуск в корпус. Код спрятан за спойлером и открывается по касанию; спойлер можно заменить своей картинкой.

Три размера текста, скрытие преподавателя и прошедших пар, динамические цвета Material You. Данные кэшируются и доступны без сети; виджеты обновляются сами.

### Расписание

Пары по дням с тапом в детали: тип, формат, преподаватель, аудитория и корпус с кнопкой «Открыть на карте», ссылка на видеозвонок, заметка. Если сервисы подключены — «Друзья на паре». Записи и очереди на спорт видны прямо в расписании и отменяются оттуда же.

### Спорт и автозапись

- Календарь занятий с фильтрами по виду спорта, корпусу и дням; свободные места и записи друзей на карточке.
- Запись в один тап и отмена.
- **Очередь на место** — если занятие заполнено, приложение запишет вас, как только место освободится.
- **Очередь на будущее** — запись на занятие, которого ещё нет в расписании, за две недели вперёд (с месячным лимитом).
- Баллы за семестр, посещения и прогресс до зачёта.

Автозапись работает через сервер проекта и требует подключения сервисов (см. ниже). Результат приходит уведомлением.

### Зачётка

Предметы семестра с баллами и контрольными точками из ИСУ. Переключатель **БАРС** накладывает баллы и работы из БАРС на тот же список. У предмета своя страница: контрольные точки, преподаватели и его ближайшие пары. Физкультура берёт баллы из «Моего спорта».

### Друзья

Заявки в друзья, профили, поиск людей по имени или ИСУ. Расписание и спорт друга рядом со своим. Кто видит ваше расписание, спорт и список друзей, решаете вы: все, друзья или никто.

## Установка

1. Скачайте `itmo-widgets-v*.apk` из [последнего релиза](https://github.com/alllexey-dev/ITMO.Widgets/releases/latest).
2. Разрешите установку из этого источника и откройте файл.
3. При первом запуске приложение проведёт по виджетам, подключению сервисов и уведомлениям.

Нужен Android 8.0 и новее. О новых версиях приложение сообщает само. Публикация в Google Play готовится.

## Вход

**ITMO.ID.** Кнопка «Войти через ITMO.ID» открывает официальную страницу университета. Логин и пароль вводятся только там; приложение получает токен и хранит его на устройстве.

**Другой способ входа.** Если страница входа не работает, токен можно ввести вручную:

1. Откройте [my.itmo.ru](https://my.itmo.ru/) в браузере на компьютере.
2. `F12` → вкладка **Application** → **Cookies** → `https://my.itmo.ru`.
3. Скопируйте значение `auth.refresh_token.itmoId` и вставьте его в диалог «Другой способ входа».

## Данные и приватность

Без подключения сервисов приложение работает только с MyITMO и БАРС напрямую; токен и кэш хранятся на устройстве.

Друзья, автозапись на спорт и уведомления работают через сервер проекта и включаются отдельным переключателем **Подключение к ITMO.Widgets**. Сервер хранит:

- ИСУ, имя и фото из ITMO.ID, учебную группу;
- расписание на ближайшие недели — чтобы его видели друзья;
- записи и очереди на спорт;
- токен уведомлений устройства.

Токен ITMO.ID сервер проверяет и не сохраняет, запросы к ИТМО от вашего имени не делает. Всё удаляется вместе с аккаунтом. Подробности — в [политике конфиденциальности](https://widgets.alllexey.dev/privacy.html). Исходный код сервера открыт.

## Экосистема

| Репозиторий | Что делает |
|---|---|
| [ITMO.Widgets](https://github.com/alllexey-dev/ITMO.Widgets) | Android-приложение (этот репозиторий) |
| [itmo-widgets-backend](https://github.com/alllexey-dev/itmo-widgets-backend) | Сервер: друзья, приватность, очереди на спорт, уведомления |
| [itmo-widgets-core](https://github.com/alllexey-dev/itmo-widgets-core) | Типизированный контракт и клиент сервера |
| [my-itmo-api](https://github.com/alllexey-dev/my-itmo-api) | Java-клиент MyITMO и БАРС |

## Разработчикам

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Нужен JDK 17+. Debug-сборка ходит на dev-сервер, release — на боевой. Описание архитектуры, настроек и каждой фичи — в [docs/README.md](docs/README.md), правила для контрибьюторов и агентов — в [AGENTS.md](AGENTS.md), история изменений — в [CHANGELOG.md](CHANGELOG.md).

## Обратная связь

Ошибки и идеи — в [Issues](https://github.com/alllexey-dev/ITMO.Widgets/issues) или в канале [@itmowidgets](https://t.me/itmowidgets). В приложении есть «Журнал ошибок» (Настройки → Обслуживание): его содержимое без токенов и личных данных можно приложить к отчёту.

## Лицензия

[MIT](LICENSE). Приложение не связано с Университетом ИТМО.

<p align="center">
  <a href="https://starchart.cc/alllexey-dev/ITMO.Widgets"><img src="https://starchart.cc/alllexey-dev/ITMO.Widgets.svg?variant=adaptive" alt="Stargazers over time" width="600" /></a>
</p>
