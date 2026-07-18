🌐 Languages: [󠁧󠁢󠁥󠁮󠁧󠁿**English**](README.md) | [**Русский**](README.ru.md)

[![oosmetrics](https://api.oosmetrics.com/api/v1/badge/achievement/3187ce37-9f15-4a93-8978-670bf41a42ca.svg)](https://oosmetrics.com/repo/noVibe/DnsConf)<br>
[![Last Build](../../actions/workflows/github_action.yml/badge.svg?branch=main)](../../actions/workflows/github_action.yml)<br>

# Конфигуратор блокировок и перенаправлений DNS

**Позволяет настраивать правила перенаправления и блокировок сайтов для аккаунтов Cloudflare и NextDNS.**

**Может работать через GitHub Actions. Ничего скачивать, устанавливать не нужно!**

## Сравнение бесплатных тарифов NextDNS и Cloudflare

|                                  | NextDNS                                                | Cloudflare                                                                                            |
|----------------------------------|--------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| **Лимит DNS-запросов**           | 300 000 в месяц                                        | 100 000 в день                                                                                        |
| **Ограничения IPv4**             | DNS-запросы ограничены одним IP-адресом (можно менять) | DNS-запросы строго ограничены одним IP-адресом (определяется Cloudflare автоматически, менять нельзя) |
| **DoH / DoT / IPv6**             | Без ограничений                                        | Без ограничений                                                                                       |
| **Ограничения настройки по API** | 60 запросов в минуту                                   | Без ограничений                                                                                       |
| **Общие ограничения**            | Отсутствуют                                            | Инфраструктура блокируется РКН (Проблемы с доступностью в РФ)                                         |
| **Преимущества**                 | Готовые опции блокировки рекламы и трекеров            | Стабильность инфраструктуры                                                                           |

Резюмируя: если вы находитесь в РФ, **NextDNS** - ваш единственный выбор из-за РКН.

Если вы в другой стране - **Cloudflare** имеет более щедрые лимиты на бесплатном тарифе. Блокировку трекеров и рекламы также можно использовать, предоставив в BLOCK список доменов, например https://small.oisd.nl/domainswild2

## Простой и быстрый способ настройки

Используйте конфигуратор https://dns-conf-ui.vercel.app в режиме **Quick**. Он сам настроит ваш DNS-профиль и сделает всю необходимую работу на GitHub.

Достаточно авторизоваться на GitHub и предоставить **CLIENT_ID** и **AUTH_SECRET**. Подробнее о том, где найти эти данные: [Настройка учетных данных](#настройка-учетных-данных)

## Стандартный способ настройки

[Видео с настройкой](#видео-пошаговой-настройки-redirect-для-nextdns)

[Настройка учетных данных](#настройка-учетных-данных)

[Настройка профиля](#настройка-профиля)

[Настройка источников данных](#настройка-источников-данных)

[Настройка исключений редиректов (опционально)](#настройка-исключений-редиректов-опционально)

[Настройка сразу нескольких профилей](#настройка-сразу-нескольких-профилей)

[GitHub Actions](#настройка-github-actions)

---

## Cloudflare vs NextDNS

|                        | NextDNS                                                      | Cloudflare                              |
|------------------------|--------------------------------------------------------------|-----------------------------------------|
| **Лимит DNS-запросов** | 300 000 в месяц                                              | 100 000 в день                          |
| **Ограничения IPv4**   | DNS-запросы ограничены одним IP-адресом                      | DNS-запросы ограничены одним IP-адресом |
| **DoH / DoT / IPv6**   | Без ограничений                                              | Без ограничений                         |
| **Ограничения API**    | 60 запросов в минуту                                         | Без ограничений                         |
| **Общие ограничения**  | Отсутствуют                                                  | Инфраструктура блокируется РКН          |
| **Особенности**        | Сохранение настроек занимает больше времени из-за лимита API | Проблемы с доступностью в РФ            |

---
## Настройка учетных данных


### Настройка учетных данных NextDNS

1) Сгенерируйте **API KEY** на странице
   https://my.nextdns.io/account
   и сохраните его в **переменную окружения** `AUTH_SECRET`.

2) Перейдите на https://my.nextdns.io. На открывшейся странице скопируйте ID из секции **Endpoints**
   и сохраните его в **переменную окружения** `CLIENT_ID`.

### Настройка учетных данных Cloudflare

1) После регистрации в **Cloudflare** перейдите во вкладку _Zero Trust_ и создайте аккаунт.

- Бесплатный тариф имеет хорошие лимиты — просто выберите его
- Шаг с добавлением платёжного метода можно пропустить, нажав _Cancel and exit_ (в правом верхнем углу)
- Вернитесь во вкладку _Zero Trust_

2) Создайте токен: **Create Token → Create Custom Token**:  
   https://dash.cloudflare.com/profile/api-tokens

   С двумя разрешениями (Permissions):

   Account.Zero Trust : Edit
   Account.Account Firewall Access Rules : Edit

   Сохраните API-токен в **переменную окружения** `AUTH_SECRET`.

3) Найдите **Account ID**:  
   https://dash.cloudflare.com/?to=/:account/workers

   Сохраните **Account ID** в **переменную окружения** `CLIENT_ID`.

---

---

## Настройка профиля

Установите в **переменную окружения** `DNS` название DNS-провайдера (**Cloudflare** или **NextDNS**).

---

## Настройка источников данных

Каждый источник данных должен быть ссылкой на hosts-файл, можете воспользоваться этой:  
https://raw.githubusercontent.com/Internet-Helper/GeoHideDNS/refs/heads/main/hosts/hosts

Можно указать несколько источников, разделив их запятой:
`https://first.com/hosts,https://second.com/hosts`

---

### 1) Настройка перенаправлений (редиректы)

Укажите источники в **переменной окружения** `REDIRECT`.

Скрипт парсит источники, ингнорируя строки, начинающиеся на `0.0.0.0` и `127.0.0.1`

Например, из строк:

    0.0.0.0 domain.to.block
    1.2.3.4 domain.to.redirect
    127.0.0.1 another.to.block

будет взята только `1.2.3.4 domain.to.redirect` для дальнейшей обработки редиректов.

+ Если домен для редиректа встречается несколько раз, будет применён первый IP из первого источника.

---

### 2) Настройка блоклиста

Укажите источники в **переменной окружения** `BLOCK`.

Скрипт парсит источники, забирая только строки, начинающиеся на `0.0.0.0`, `127.0.0.1`, `::1`, а также строки, содержащие только домен.

Например, из строк:

    1.2.3.4 domain.to.redirect
    0.0.0.0 domain.to.block
    127.0.0.1 another.to.block
    ::1 ipv6.to.block
    no-ip.just.domain

будут взяты только:

    domain.to.block
    another.to.block
    no-ip.just.domain
    ipv6.to.block

для дальнейшей обработки блокировок.

+ Для **Cloudflare** можно использовать один и тот же источник для `BLOCK` и `REDIRECT`.


+ Для **NextDNS** оптимальным вариантом будет указать только `REDIRECT`, а списки для блокировки выбрать вручную во
  вкладке _Privacy_.

### 3) Исключения для NextDNS rewrites (необязательно)
Укажите JSON в **переменной окружения** `NEXTDNS_REWRITE_EXCLUSIONS`

```json
{"patterns":["*.instagram.com","*.facebook.com"]}
```

+ `patterns` исключает совпавшие домены из создания новых NextDNS rewrites.
+ Эта конфигурация применяется во время обработки NextDNS `REDIRECT`.
+ `cleanupExisting` управляет только удалением уже существующих совпавших rewrites в NextDNS.
+ Если `cleanupExisting` не указан, по умолчанию используется `false`.
+ Если `cleanupExisting=false`, новые совпавшие rewrites всё равно не создаются, но существующие совпавшие записи не удаляются.
+ Если источники `REDIRECT` не заданы, фильтрация и очистка по исключениям не выполняются.
+ Сопоставление нечувствительно к регистру и перед сравнением убирает префикс `www.`.
+ Для удобства шаблон вида `*.instagram.com` совпадает и с `instagram.com`, и с его поддоменами.
+ Если JSON некорректный, запуск завершается с понятной ошибкой конфигурации.

---

## Настройка исключений редиректов (опционально)

Добавьте домены в **переменную среды** `EXCLUDE_REDIRECT` через запятую **без пробела**, например:
`instagram.com,twitch.com`

Эти домены и все их поддомены:

- будут удалены из существующих настроек редиректов;
- не будут учитываться при добавлении новых.

---

## Настройка сразу нескольких профилей

### Ограничения

Все профили получат _одинаковые_ настройки. Другими словами, значения в `BLOCK`, `REDIRECT`, `EXCLUDE_REDIRECT` и `NEXTDNS_REWRITE_EXCLUSIONS` будут **общими**.

### Несколько профилей одного провайдера

Укажите данные профилей через запятую **без пробела** в соответствующих **переменных окружения**.
Например, если у вас два профиля NextDNS, должно получиться так:

- `AUTH_SECRET` содержит: `секрет_NextDns_1,секрет_NextDns_2`
- `CLIENT_ID` содержит `идентификатор_NextDns_1,идентификатор_NextDns_2`

### Несколько профилей разных провайдеров

В дополнение к настройке выше, укажите имя провайдера для каждого профиля через запятую **без пробела** в **переменной
среды** `DNS`. Пример:

- `DNS` содержит: `NEXTDNS,CLOUDFLARE,NEXTDNS`
- `AUTH_SECRET` содержит: `секрет_NextDns_1,секрет_Cloudflare_1,секрет_NextDns_2`
- `CLIENT_ID` содержит `идентификатор_NextDns_1,идентификатор_Cloudflare_1,идентификатор_NextDns_2`

---

## Поведение скрипта

### Cloudflare

Ранее сгенерированные данные будут удалены. Скрипт распознаёт старые данные по следующим признакам:

+ Префикс имени списка: **_Blocked websites by script_** и **_Override websites by script_**
+ Префикс имени правила: **_Rules set by script_**
+ Отличающийся **_Session id_** (Session id хранится в поле description)

После удаления старых данных будут созданы и применены новые списки и правила.

Если нужно очистить настройки блокировки/редиректа **Cloudflare**, запустите скрипт без указания источников в
соответствующих **переменных окружения**.
Например, отсутствие значения в `BLOCK` приведёт к сбросу настроек блокировки.

---

### NextDNS

Для `REDIRECT`:

+ Существующий домен будет обновлён, если IP редиректа изменился
+ Новые домены будут добавлены к существующим
+ Остальные настройки редиректов останутся без изменений
+ Домены, совпавшие с `NEXTDNS_REWRITE_EXCLUSIONS.patterns`, исключаются из создания новых rewrites

Для `BLOCK`:

+ Новые домены будут добавлены к существующим
+ Остальные настройки блокировки останутся без изменений

Для `NEXTDNS_REWRITE_EXCLUSIONS`:
+ Если `cleanupExisting=true`, существующие совпавшие rewrites удаляются через уже существующий путь API с повторными попытками и ожиданием при rate limit
+ Если `cleanupExisting=false` или это поле не указано, существующие совпавшие rewrites остаются без изменений

Ранее сгенерированные данные удаляются, если не заданы источники **ДЛЯ ОБЕИХ НАСТРОЕК** `BLOCK` и `REDIRECT`.

## Необязательный режим личного VPS-прокси

После provisioning совместимого VPS задайте Secret `REDIRECT_TARGET` с его
публичным IPv4. Тогда все новые desired NextDNS rewrites из общего снимка
`REDIRECT` указывают на VPS. Приложение скачивает и разбирает `REDIRECT` ровно
один раз, до любых мутаций stage-ит компактный root allowlist на VPS, а commit выполняет
только после успеха всех настроенных профилей.

В GitHub Environment Secrets (не Variables и не файлы репозитория) нужны:
`REDIRECT_TARGET`, `PROXY_VPS_ROOT_PASSWORD`, точная out-of-band запись
`PROXY_VPS_SSH_KNOWN_HOSTS` формата `<VPS_IP> ssh-ed25519 <BASE64_KEY>` и, при
миграции, CSV `PROXY_PREVIOUS_REDIRECT_TARGETS`. Режим не стартует без
публичного IPv4, password, совпадающего trusted host key, `REDIRECT` и хотя бы
одного профиля NextDNS. SSH всегда TCP 22, команда всегда
`/usr/local/sbin/dnsconf-proxy-allowlist`; пароль не передаётся shell-аргументом,
а проверка host key происходит до password authentication.

В proxy mode каждый hostname сворачивается до регистрируемого корня (eTLD+1)
через Public Suffix List из Guava, включая PRIVATE-правила. Сворачивание
применяется только если сам корень явно присутствует в том же snapshot. Поэтому
`api.us.elevenlabs.io` превращается в `elevenlabs.io`, а одиночный hostname общей
CDN не расширяется до корня оператора CDN. NextDNS получает один rewrite на
получившийся корень, а HAProxy contract v2 разрешает этот корень и его поддомены
по границе точки, после чего резолвит фактический SNI/Host запроса.

Root allowlist отсортирован, дедуплицирован и не публикуется artifact-ом. В proxy
mode исключать нужно весь маршрутизируемый корень, например `*.instagram.com`:
исключение одного дочернего hostname не может переопределить parent rewrite NextDNS.
Для замены VPS добавьте старый IP в `PROXY_PREVIOUS_REDIRECT_TARGETS`, выполните
один успешный полный sync и только потом удалите его из Secret. Записи с другим
content не принадлежат proxy-режиму и не удаляются.

---

## Проверка через Docker

Java и Maven на локальной машине не нужны.

Локальную проверку запускайте только через Docker:

```bash
docker compose run --rm validate
```

Команда запускает `mvn -B clean test package` внутри контейнера и проверяет минимальные тесты фильтрации, а также успешную сборку пакета.

В качестве шаблона переменных используйте `.env.example`. Если нужен локальный некоммитируемый файл, скопируйте его в `.env.local`.

## Локальный запуск через Docker с реальными изменениями в NextDNS

Если вы хотите запускать реальное применение изменений к NextDNS со своей машины, это тоже можно делать через Docker.

1. Создайте локальный некоммитируемый env-файл:

```bash
cp .env.example .env.local
```

2. Заполните `.env.local` своими боевыми значениями.
3. Для первого запуска рекомендую начать с:

```json
{"patterns":["*.instagram.com","*.facebook.com"],"cleanupExisting":false}
```

4. Запустите реальное применение:

```bash
docker compose --profile apply run --rm apply
```

Эта команда собирает jar внутри контейнера и затем запускает приложение с переменными из `.env.local`, поэтому оно реально меняет ваш live-профиль NextDNS.

На что смотреть в логах:
+ `Loaded ... NextDNS rewrite exclusion patterns. Existing cleanup: ...`
+ `Skipping ... rewrite candidates due to exclusion patterns`
+ `Removing ... excluded rewrites from NextDNS`, если `cleanupExisting=true`
+ `Saving ... new rewrites to NextDNS...`
+ `Skipped ... malformed override lines`, если во внешнем hosts-источнике встретились битые строки без домена
+ `No block sources provided; skipping denylist update`, если вы запускаете сценарий только с `REDIRECT`

Важно:
+ Dry-run режима здесь нет.
+ `docker compose run --rm validate` — безопасная проверка.
+ `docker compose --profile apply run --rm apply` — реальная команда, которая меняет состояние NextDNS.
+ Фильтрация и очистка по исключениям являются частью `REDIRECT`-ветки rewrites, поэтому они выполняются только если заданы источники `REDIRECT`.

---

## Настройка GitHub Actions

#### Видео пошаговой настройки REDIRECT для NextDNS:

https://www.youtube.com/watch?v=vbAXM_xAL5I

#### Шаги настройки

1) Сделайте Fork репозитория
2) Перейдите в _Settings_ → _Environments_
3) Создайте _New environment_ с именем `DNS`
4) Добавьте `AUTH_SECRET` и `CLIENT_ID` в **Environment secrets**
5) Добавьте `DNS`, `REDIRECT`, `BLOCK`, при необходимости `EXCLUDE_REDIRECT` и `NEXTDNS_REWRITE_EXCLUSIONS` в **Environment variables**

Для режима VPS-прокси оставьте в `REDIRECT` полный GeoHide source и добавьте в тот же Environment `DNS` следующие **Environment secrets**:

- `REDIRECT_TARGET` — текущий публичный IPv4 VPS;
- `PROXY_VPS_ROOT_PASSWORD` — root-пароль для SSH;
- `PROXY_VPS_SSH_KNOWN_HOSTS` — точная pinned-строка `<VPS_IP> ssh-ed25519 <BASE64_KEY>`;
- `PROXY_PREVIOUS_REDIRECT_TARGETS` — только на время миграции VPS, иначе оставить пустым.

VPS уже должен поддерживать contract version `2`. Runner загружает в фиксированный incoming-каталог root-owned regular allowlist-файл с правами `0600`, после чего вызывает только fixed allowlist CLI. Чтобы временно выключить proxy mode, не удаляя credentials, удалите или переименуйте только `REDIRECT_TARGET`: следующий run вернёт прежнее поведение rewrites.

VPS обязан резолвить upstream через независимые публичные resolvers, а не через переписываемый NextDNS profile — иначе получится цикл VPS → VPS. Отдельный DNS daemon не требуется. Схема не является клиентской аутентификацией; защита обеспечивается root allowlist с suffix-проверкой по границе точки, fail-closed reject, egress filtering и непубликацией VPS IP.

+ **Action** запускается ежедневно в **01:30 UTC** (04:30 по МСК).  
  Чтобы изменить время, отредактируйте cron в `.github/workflows/github_action.yml`
+ **Action** можно запустить вручную через кнопку **Run workflow**:  
  вкладка _Actions_ → workflow **DNS Block&Redirect Configurer cron task**

#### Что проверить после ручного запуска workflow
+ Job должен завершиться со статусом **Success**
+ В логах должны быть видны шаги загрузки rewrite-источника, возможный `Skipped ... malformed override lines` и сохранение через `Saving ... new rewrites to NextDNS...`
+ Для REDIRECT-only сценария проверьте строку `No block sources provided; skipping denylist update` и убедитесь, что denylist не менялся
+ Если `cleanupExisting=true`, дополнительно проверьте строку `Removing ... excluded rewrites from NextDNS`
+ После успешного run откройте профиль NextDNS и убедитесь, что нужные rewrites появились или обновились, а домены из `NEXTDNS_REWRITE_EXCLUSIONS.patterns` не были созданы заново
+ В proxy mode логи должны содержать count/SHA allowlist, `Stage proxy allowlist`, обновление DNS и `Commit proxy allowlist`, но никогда не target IP, password, known_hosts, remote path или transaction token
