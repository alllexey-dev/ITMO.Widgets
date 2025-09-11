<h1 align="center">ITMO.Widgets</h1>

<p align="center">
  <strong>Полезные виджеты для студентов ИТМО на Android</strong>
</p>

**ITMO.Widgets** — коллекция виджетов для Android, созданная для упрощения студенческой жизни в ИТМО.

*Проект находится в активной разработке.*

## Функционал

**Виджеты** (на данный момент):
 - QR-пропуск в корпуса университета
 - Текущая/следующая пара (два варианта)

*(надеюсь, в будущем будут ещё)* <br>

**Особенности**:
 - Кэширование запросов
 - Динамическое обновление виджетов
 - Скорость и лёгкость (вес <8 МБ)

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

**Осторожно:** **Никому не сообщайте ваш токен!** Это ключ к вашему личному кабинету. Приложение использует этот токен исключительно для отправки запросов на серверы MyITMO (см. исходный код).


После этого просто добавьте виджет на экран, пользуйтесь!
## Примеры

### QR-виджет

<img width="128" alt="qr-prev-cropped" src="https://github.com/user-attachments/assets/626f4ac5-7b6f-4025-8f11-e29331dcc40c" />

### Текущая/следующая пара (первый вариант)

<p>
  <img width="256" alt="image" src="https://github.com/user-attachments/assets/63f5165b-55db-4f69-944a-8186dc6654bc" />
  <img width="256" alt="image" src="https://github.com/user-attachments/assets/a31dd0de-6875-4bdb-bee3-319ceb579dad" />
  <img width="256" alt="image" src="https://github.com/user-attachments/assets/7bb8129e-1b3e-4b6e-a74b-d7b5e791110b" />
</p>

### Текущая/следующая пара (второй вариант)

<p>
  <img height="86" alt="image" src="https://github.com/user-attachments/assets/9c009be0-a3a2-4bd9-898a-dafad43a5c33" />
  <img height="86" alt="image" src="https://github.com/user-attachments/assets/60eec137-3d12-4498-aca4-87343da095ed" />
</p>
