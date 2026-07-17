# Контекст проекта DnsConf

## Операционные заметки

- Не хранить реальные `AUTH_SECRET`, `CLIENT_ID`, API keys и другие секреты в репозитории.
- Для безопасной проверки без изменения NextDNS использовать unit-тесты, `docker compose run --rm validate` или GitHub Actions workflow `Validate build and tests`.
- Workflow `DNS Block&Redirect Configurer cron task` запускает приложение и может менять live-настройки DNS-профиля. Его нельзя запускать для реальной проверки без явного подтверждения владельца.
- Для экономии GitHub Actions storage validation/build workflows не должны публиковать artifacts и не должны включать dependency cache без необходимости.

## Текущий baseline

- Источник GeoHideDNS для `BLOCK`/`REDIRECT`: `https://raw.githubusercontent.com/Internet-Helper/GeoHideDNS/refs/heads/main/hosts/hosts`.
- NextDNS rewrite exclusions по умолчанию не удаляют уже существующие rewrites: `cleanupExisting=false`.
- Широкие patterns `*` и `*.*` запрещены при `cleanupExisting=true`, чтобы не удалить почти все rewrites ошибочной конфигурацией.

## Personal VPS proxy mode

- Proxy mode включается только Secret `REDIRECT_TARGET`; он требует также Secrets `PROXY_VPS_ROOT_PASSWORD` и `PROXY_VPS_SSH_KNOWN_HOSTS`. Пустой/отсутствующий target сохраняет прежнее поведение.
- `REDIRECT` загружается и разбирается ровно один раз за процесс. Тот же snapshot формирует NextDNS rewrites и sorted exact allowlist.
- VPS-контракт фиксирован: SSH TCP 22 с pinned known_hosts до password authentication; SFTP отправляет root-owned regular файл `0600` только как `/var/lib/dnsconf-proxy/incoming/allowlist.<hex>`; затем применяются только fixed `version`, `stage`, `renew`, `commit`, `abort` команды.
- Порядок proxy run: validate → snapshot → `version` → `stage` → все DNS profiles → `commit`; после stage при любом сбое выполняется best-effort `abort`. Token, remote path, password, known_hosts и current/previous target не логируются.
- `PROXY_PREVIOUS_REDIRECT_TARGETS` используется только на время миграции. Старый адрес удаляется из Secret после полного успешного reconcile.
