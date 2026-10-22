# Parking Management System

Веб-приложение для платной парковки: водитель выбирает место и время, бронирует парковку, въезжает через симулятор шлагбаума и после выезда получает счёт. Backend и Frontend — отдельные приложения, взаимодействующие по REST API и WebSocket.

## Технологии

- **Backend:** Java 21, Spring Boot, Gradle, Spring Security, JWT.
- **Frontend:** React 19, TypeScript, Vite, Tailwind CSS.
- **База данных:** PostgreSQL, миграции Flyway.
- **Обновления в реальном времени:** WebSocket/STOMP.
- **Проверки:** JUnit, интеграционные тесты с PostgreSQL, Vitest, GitHub Actions.

## Возможности

- Регистрация, вход и профиль водителя; добавление автомобилей.
- Схема из **20 мест (A1–A10, B1–B10)** со статусами: свободно, удерживается, забронировано, занято, недоступно.
- Выбор автомобиля и временного интервала; **удержание места до 3 минут**, подтверждение и отмена бронирования.
- Защита от одновременных пересекающихся броней на уровне транзакций и ограничений PostgreSQL.
- Автоматическое снятие брони, если водитель не въехал в течение **15 минут** после начала (или до конца более короткого интервала).
- Симулятор въезда/выезда по номеру автомобиля, в том числе **въезд без брони** с назначением свободного места.
- Дневной и ночной тарифы, расчёт по фактическому времени и сохранение итогового счёта при выезде.
- История посещений и счетов.
- Обновление схемы парковки **у всех подключённых пользователей без перезагрузки** через WebSocket.
- Однократное Email-напоминание за 10 минут до конца брони для водителя, который уже въехал; отправка требует настройки SMTP.

## Быстрый запуск

### Требования

Java 21, Node.js 22, npm и PostgreSQL (проверялись версии 14 и 17). Docker для локального запуска не обязателен.

### 1. Подготовить PostgreSQL

Создайте пользователя и две отдельные базы — рабочую и тестовую:

```sql
CREATE USER parking_user WITH PASSWORD 'your_local_password';
CREATE DATABASE parking_db OWNER parking_user;
CREATE DATABASE parking_test_db OWNER parking_user;
```

Flyway автоматически применит миграции **V1–V7**, подключит расширение `btree_gist` (если доступно пользователю-владельцу БД) и добавит 20 мест. Если установить расширение нельзя, попросите администратора БД выполнить `CREATE EXTENSION IF NOT EXISTS btree_gist;` в рабочей базе.

### 2. Настроить Backend

Экспортируйте переменные окружения в терминале, где будете запускать Spring Boot:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/parking_db'
export DB_USERNAME='parking_user'
export DB_PASSWORD='your_local_password'
export JWT_SECRET_BASE64="$(openssl rand -base64 32)"

# Только для полного прогона Backend-тестов:
export TEST_DB_URL='jdbc:postgresql://localhost:5432/parking_test_db'
export TEST_DB_USERNAME='parking_user'
export TEST_DB_PASSWORD='your_local_password'
```

`DB_*` и `JWT_SECRET_BASE64` обязательны. **Не используйте рабочую базу для тестов.** Файл `.env` не загружается Spring Boot автоматически; примеры дополнительных настроек приведены в `.env.example`.

```bash
cd parking-backend
./gradlew bootRun
```

### 3. Запустить Frontend

В другом терминале:

```bash
cd parking-frontend
cp .env.example .env.local
npm ci
npm run dev
```

Локальные адреса по умолчанию:

| Сервис | Адрес |
|---|---|
| Frontend | http://localhost:5173 |
| Backend REST | http://localhost:8080 |
| WebSocket | ws://localhost:8080/ws/parking |
| PostgreSQL | localhost:5432 |

Настройки `VITE_API_BASE_URL` и `VITE_WS_URL` находятся в `parking-frontend/.env.local`. Для другого адреса Frontend настройте `PARKING_REST_ALLOWED_ORIGINS` и `PARKING_WEBSOCKET_ALLOWED_ORIGINS` на Backend.

## Проверка работы

1. Откройте Frontend, зарегистрируйтесь и добавьте автомобиль.
2. На странице парковки выберите дату, время и свободное место; подтвердите удержание в течение 3 минут.
3. Откройте вторую независимую сессию браузера с другим пользователем — изменения мест должны появляться без перезагрузки.
4. Проверьте въезд и выезд в симуляторе шлагбаума (для въезда по брони действует разрешённое окно).
5. Откройте историю визитов и выставленный счёт.

**Тарифы по умолчанию:** с 08:00 до 20:00 — **2 KGS/мин**, с 20:00 до 08:00 — **1 KGS/мин**, часовой пояс `Asia/Bishkek`. Переход через границу тарифов учитывается; сумма округляется один раз до двух знаков. Выставленный счёт хранит использованные ставки и больше не пересчитывается по новым тарифам.

## Тесты

**Backend** (нужна отдельная тестовая PostgreSQL и `TEST_DB_*`):

```bash
cd parking-backend
./gradlew clean build
./gradlew smokeTest
```

**Frontend:**

```bash
cd parking-frontend
npm ci
npm run lint
npm run typecheck
npm run test:run
npm run build
```

GitHub Actions отдельно проверяет Backend на PostgreSQL и Frontend. Smoke-тесты покрывают ключевые сценарии, но не заменяют полный регрессионный и браузерный прогон.

## Развёртывание на Render

Файл `render.yaml` описывает единый production-стенд:

- Frontend — Render Static Site;
- Backend — Render Web Service, собранный из `parking-backend/Dockerfile`;
- PostgreSQL 17 — Render Postgres с доступом только по приватной сети.

Публичные адреса стенда:

| Сервис | Адрес |
|---|---|
| Frontend | https://parking-management-frontend-erlan2332.onrender.com |
| Backend | https://parking-management-backend-erlan2332.onrender.com |
| Health check | https://parking-management-backend-erlan2332.onrender.com/actuator/health |

Оба сервиса используют ветку `dev-parking` и `autoDeployTrigger: checksPass`. После push GitHub Actions выполняет Backend smoke-тесты, сборку production Docker-образа, Frontend lint/typecheck/tests/build. Render начинает новый деплой только когда все проверки коммита завершились успешно.

Для первого запуска импортируйте репозиторий в Render как Blueprint. JWT-секрет и реквизиты базы генерируются и передаются сервису самим Render; секреты не хранятся в Git.

Blueprint использует бесплатные планы, поэтому Backend может засыпать при отсутствии трафика, а бесплатная PostgreSQL истекает через 30 дней. Для постоянного production-стенда переключите Backend и PostgreSQL на платные планы в Render.

## Архитектура и безопасность

Backend организован по модулям (`auth`, `reservation`, `barrier`, `invoice` и др.). Контроллеры обрабатывают HTTP, сервисы — бизнес-операции и транзакции, репозитории — БД, мапперы — преобразование в DTO. Lombok устраняет шаблонный код там, где это безопасно для JPA.

REST API защищён JWT (кроме регистрации и входа). Токен действует **60 минут**. WebSocket использует JWT в STOMP `CONNECT` и тему `/topic/parking/changes`. Источник истины для статусов — PostgreSQL; после WebSocket-события клиент обновляет доступность через REST.

## Ограничения

- **Онлайн-оплата не реализована:** система выставляет счета, но не принимает деньги.
- Нет PDF-счетов, административной панели и refresh token.
- SMTP выключен по умолчанию (`MAIL_ENABLED=false`). Для проверки Email можно использовать локальный Mailpit; реальные письма не требуются для работы парковки.
- Встроенный STOMP-брокер рассчитан на один экземпляр Backend и не хранит события для отключённых клиентов.
- Симулятор шлагбаума не управляет физическим оборудованием; занятость выводится из активных визитов.

Проект предназначен для локальной демонстрации полного пользовательского сценария. Точные результаты тестов и известные ограничения следует сверять с актуальными GitHub Actions и журналом разработки.
