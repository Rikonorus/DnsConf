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
