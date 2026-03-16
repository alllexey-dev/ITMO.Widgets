<h1 align="center">ITMO.Widgets</h1>

<p align="center">
  <strong>Ультимативный набор инструментов для студентов ИТМО на Android</strong>
  <br />
  <a href="https://github.com/alllexey-dev/ITMO.Widgets/releases/latest"><strong>» Скачать последнюю версию «</strong></a>
</p>

<p align="center">
  <img src="https://img.shields.io/github/v/release/alllexey-dev/ITMO.Widgets?style=flat-square&color=blue" alt="Latest Release" />
  <img src="https://img.shields.io/github/downloads/alllexey-dev/ITMO.Widgets/total?style=flat-square&color=orange" alt="Downloads" />
</p>

**ITMO.Widgets** — это больше не просто виджеты. Это набор утилит, который автоматизирует рутину, позволяет записываться на спорт "в один клик" (или вообще без кликов) и держит расписание под рукой.

## ✨ Возможности

### 📅 Виджеты расписания
*   **Два вида:**
    *   *Single:* Показывает текущую или следующую пару.
    *   *List:* Список пар на весь день (сегодня или завтра).
*   **Кастомизация:** Два стиля, опции скрытия преподавателей или прошедших пар.
*   **Умное обновление:** Данные кешируются и доступны офлайн.

### 🔐 Умный QR-код
*   **Быстрый доступ:** Пропуск в университет всегда на главном экране.
*   **Спойлер:** Скрывает QR-код за "цифровым шумом" (анимация по клику), чтобы случайно не скомпрометировать ваш пропуск.
*   **Динамические цвета:** Подстраивается под обои вашего телефона (Material You).
*   **Умное обновление:** Данные кешируются и доступны офлайн.

### 🏃‍♂️ Спорт и Автозапись
*   **Удобная запись:** Календарь занятий с фильтрами по времени, корпусу, преподавателю и виду спорта.
*   **Автозапись:**
    *   *Очередь на освободившееся место:* Если занятие забито, можно встать в очередь. Как только место освободится - приложение запишет вас.
    *   *Очередь на будущее:* Можно заранее встать в очередь на занятие, которое еще не появилось в расписании (на 2 недели вперед) (лимитированно).
    *   *Важно: ваше устройство должно быть онлайн в момент срабатывания автозаписи*
*   **Статистика:** Просмотр баллов, посещений и прогресса до зачета.

### 🔔 Сервисы
*   **My ITMO Web:** Встроенный браузер для доступа к полному функционалу личного кабинета без повторного ввода пароля. <br>*Замечание: пока работает не на всех устройствах.*

## 📸 Скриншоты приложения

<p>
  <img height="256" alt="image" src="https://github.com/user-attachments/assets/45ffd030-f6af-4dc1-b0eb-677d2a641bba" />
  <img height="256" alt="image" src="https://github.com/user-attachments/assets/4147ea57-cbb1-4724-b1b6-d94da2773d1c" />
  <img height="256" alt="image" src="https://github.com/user-attachments/assets/d8e9ae81-7960-4805-9925-58353623d080" />
  <img height="256" alt="image" src="https://github.com/user-attachments/assets/ae1c9b1d-3ea2-4f9a-bdc5-b612085e05d4" />
  <img height="256" alt="image" src="https://github.com/user-attachments/assets/7610f55c-0e57-48fb-81e6-4a2b34d5d5d4" />
</p>

## 📸 Скриншоты виджетов

<p>
  <img height="256" alt="image" src="https://github.com/user-attachments/assets/c8521157-dfc6-4cdf-95ea-b215e722a647" />
  <img height="256" alt="image" src="https://github.com/user-attachments/assets/99109bab-a5c1-4018-bd2e-fb36ca6e738b" />
  <img height="256" alt="image" src="https://github.com/user-attachments/assets/447de56c-d6d3-41bb-9897-c2145aa3ccc6" />
  <img height="256" alt="image" src="https://github.com/user-attachments/assets/fe290ce8-13e9-4372-8304-9845b7bfc783" />
</p>

## 🚀 Установка

Скачайте актуальный `.apk` файл из раздела [Releases](https://github.com/alllexey-dev/ITMO.Widgets/releases).

## 🔑 Авторизация

Для работы приложения необходим доступ к API MyITMO.

**Способ 1: Вход через ITMO.ID (Рекомендуется)**
Просто нажмите кнопку "Войти" на экране приветствия или в настройках. Приложение откроет страницу авторизации ИТМО. Ваши логин и пароль вводятся на официальном сайте, приложение получает только токен доступа.

**Способ 2: Ручной ввод токена**
Если автоматический вход не работает, можно ввести `refresh_token` вручную в настройках:
1. Откройте [my.itmo.ru](https://my.itmo.ru/) на ПК.
2. Нажмите `F12` -> вкладка **Application** (Storage).
3. Раздел **Cookies** -> `https://my.itmo.ru`.
4. Скопируйте значение `auth.refresh_token.itmoId`.

## 🛡️ Приватность

Мы серьезно относимся к безопасности ваших данных:
*   Приложение имеет **открытый исходный код**.
*   Ваш токен сохраняется **только локально** на вашем устройстве.
*   Если вы включаете "Пользовательские сервисы" (для автозаписи), токен передается на наш сервер исключительно для авторизации. На сервере токен **не сохраняется** и **не используется для отправки запросов к ITMO** (хранится только номер ИСУ студента). Исходный код сервисов тоже открыт: [itmo-widgets-backend](https://github.com/alllexey-dev/itmo-widgets-backend)

# ⭐
[![Stargazers over time](https://starchart.cc/alllexey-dev/ITMO.Widgets.svg?variant=adaptive)](https://starchart.cc/alllexey-dev/ITMO.Widgets)

---

Проект использует [my-itmo-api](https://github.com/alllexey-dev/my-itmo-api) для взаимодействия с API университета и [itmo-widgets-core](https://github.com/alllexey-dev/itmo-widgets-core) для взаимодействия с сервисами.
                    
