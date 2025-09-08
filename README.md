<h1 align="center">ITMO.Widgets</h1>

<p align="center">
  <strong>Полезные виджеты для студентов ИТМО на Android</strong>
</p>

**ITMO.Widgets** — коллекция виджетов для Android, созданная для упрощения студенческой жизни в ИТМО.

*Проект находится в активной разработке.*

## Функционал

На данный момент присутствуют два виджета:
 - QR-пропуск в корпуса университета (БЕТА)
 - Текущая/следующая пара


*(надеюсь, в будущем будут ещё)* <br>
Проект использует [my-itmo-api](https://github.com/alllexey123/my-itmo-api) для взаимодействия с личным кабинетом ИТМО.

## Установка

Собранные APK доступны в [Releases](https://github.com/alllexey123/ITMO.Widgets/releases), можно собрать из исходников самостоятельно.

## Использование

Для работы виджетов требуется ваш персональный токен из личного кабинета MyITMO.

### Как получить токен?

1.  Откройте [личный кабинет MyITMO](https://my.itmo.ru/) в браузере на компьютере.
2.  Откройте инструменты разработчика, нажав клавишу `F12`.
3.  Перейдите на вкладку **Application** (в Chrome) или **Storage** (в Firefox).
4.  В меню слева выберите **Cookies** и найдите сайт `my.itmo.ru`.
5.  Найдите в списке cookie с названием `auth.refresh_token.itmoId` и скопируйте его значение.
6.  Вставьте скопированный токен в соответствующее поле в приложении.

### Важно
**Никому не сообщайте ваш токен!** Это ключ к вашему личному кабинету. Приложение использует этот токен исключительно для отправки запросов на серверы MyITMO (см. исходный код).

## Скриншоты

<p align="center">
  <img width="500" alt="image" src="https://github.com/user-attachments/assets/67a6293b-0701-4659-bb52-223deb7af7dc" />
  <img width="500" alt="image" src="https://github.com/user-attachments/assets/6d232adf-5286-40ec-8424-674df37c6dbd" />
  <img width="500" alt="image" src="https://github.com/user-attachments/assets/22cc8754-1ed3-4a84-9a55-0a7a798347e2" />
  <img width="500" alt="image" src="https://github.com/user-attachments/assets/42aa26eb-8612-495d-8ac1-0e0ff40f100c" />
</p>
