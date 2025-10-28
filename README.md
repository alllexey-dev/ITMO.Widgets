<h1 align="center">ITMO.Widgets</h1>

<p align="center">
  <strong>Полезные виджеты для студентов ИТМО на Android</strong>
  <br />
  <a href="https://github.com/alllexey-dev/ITMO.Widgets/releases/latest"><strong>» Скачать приложение «</strong></a>
</p>

**ITMO.Widgets** — коллекция виджетов для Android, созданная для упрощения студенческой жизни в ИТМО.<br>

<a href="https://github.com/users/alllexey-dev/projects/1"><strong>Roadmap & status </strong></a>
## О приложении 

**Виджеты**:
 - QR-пропуск в корпуса университета
 - Текущая/следующая пара (два стиля)
 - Расписание на день (два стиля)

**Особенности**:
 - Адаптивная тема приложения и виджетов
 - Интерфейс с расписанием на несколько дней
 - Кэширование расписания
 - Два способа входа - через ITMO ID или через токен MyITMO
 - Динамическое обновление виджетов
 - Скорость и лёгкость (вес apk <10 МБ)

Проект использует [my-itmo-api](https://github.com/alllexey123/my-itmo-api) для взаимодействия с личным кабинетом ИТМО.

## Установка

Собранные APK доступны в [Releases](https://github.com/alllexey-dev/ITMO.Widgets/releases), можно собрать из исходников самостоятельно.

## Использование

Для получения расписания и QR-кода необходим токен MyITMO, получить его можно двумя способами:
1. Войдя через ITMO.ID в настройках приложения (данные для входа не сохраняются, передаются только на сервера ИТМО).
2. Напрямую вставив токен в настройках приложения (инструкция далее).

### Как получить токен?

1.  Откройте [личный кабинет MyITMO](https://my.itmo.ru/) в браузере на компьютере.
2.  Откройте инструменты разработчика, нажав клавишу `F12`.
3.  Перейдите на вкладку **Application** (в Chrome) или **Storage** (в Firefox).
4.  В меню слева выберите **Cookies** и найдите сайт `my.itmo.ru`.
5.  Найдите в списке cookie с названием `auth.refresh_token.itmoId` и скопируйте его значение.
6.  Вставьте скопированный токен в соответствующее поле в приложении.

**Никому не сообщайте ваш токен!** Приложение хранит его **локально** и использует исключительно для отправки запросов на серверы MyITMO (см. исходный код).


После этого просто добавьте виджеты на экран, пользуйтесь!
## Примеры

<p>
  <img height="256" alt="image" src="https://github.com/user-attachments/assets/c8521157-dfc6-4cdf-95ea-b215e722a647" />
  <img height="256" alt="image" src="https://github.com/user-attachments/assets/99109bab-a5c1-4018-bd2e-fb36ca6e738b" />
  <img height="256" alt="image" src="https://github.com/user-attachments/assets/447de56c-d6d3-41bb-9897-c2145aa3ccc6" />
  <img height="256" alt="image" src="https://github.com/user-attachments/assets/fe290ce8-13e9-4372-8304-9845b7bfc783" />
</p>
