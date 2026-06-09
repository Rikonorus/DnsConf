🌐 Languages: [󠁧󠁢󠁥󠁮󠁧󠁿**English**](README.md) | [**Русский**](README.ru.md)

[![oosmetrics](https://api.oosmetrics.com/api/v1/badge/achievement/3187ce37-9f15-4a93-8978-670bf41a42ca.svg)](https://oosmetrics.com/repo/noVibe/DnsConf)<br>
[![Last Build](../../actions/workflows/github_action.yml/badge.svg?branch=main)](../../actions/workflows/github_action.yml)<br>

# DNS Block&Redirect Configurer

**Allows to set Redirect and Block rules to your Cloudflare and NextDNS accounts.**

**Ready-to-run via GitHub Actions.** [Video guide](https://www.youtube.com/watch?v=vbAXM_xAL5I)

## Comparison of Free Plans: NextDNS vs Cloudflare

|                         | NextDNS                                                         | Cloudflare                                                                                                           |
|-------------------------|-----------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| **DNS Query Limit**     | 300,000 per month                                               | 100,000 per day                                                                                                      |
| **IPv4 Restrictions**   | DNS queries are limited to a single IP address (can be changed) | DNS queries are strictly limited to a single IP address (automatically assigned by Cloudflare and cannot be changed) |
| **DoH / DoT / IPv6**    | Unlimited                                                       | Unlimited                                                                                                            |
| **Setup API Limits**    | 60 requests per minute                                          | Unlimited                                                                                                            |
| **General Limitations** | None                                                            | Infrastructure is blocked by Roskomnadzor (availability issues in Russia)                                            |
| **Advantages**          | Built-in ad and tracker blocking options                        | More reliable and fast infrastructure                                                                                |

In summary: if you are located in Russia, **NextDNS** is your only viable option due to Roskomnadzor restrictions.

If you are in another country, **Cloudflare** offers more generous limits on the free plan. Tracker and ad blocking can also
be enabled by providing a domain blocklist in `BLOCK`, for example: https://small.oisd.nl/domainswild2

## Easy Setup

Use the configurator https://dns-conf-ui.vercel.app in **Quick** mode. It will automatically configure your DNS profile
and perform all required GitHub setup steps.

You only need to sign in with GitHub and provide **CLIENT_ID** and **AUTH_SECRET**. More details on where to find these
values here: [Setup credentials](#setup-credentials)

## Standard Setup

[Setup credentials](#setup-credentials)

[Setup profile](#setup-profile)

[Setup data sources](#setup-data-sources)

[Setup exclude redirects (optional)](#setup-exclude-redirects-optional)

[Multiple profiles setup](#multiple-profiles-setup)

[GitHub Actions](#github-actions-setup)

---
## Setup credentials

### NextDNS credentials setup

1) Generate **API KEY**, from https://my.nextdns.io/account and set as **environment variable** `AUTH_SECRET`

2) Click on **NextDNS** logo. On the opened page, copy ID from Endpoints section.
   Set it as **environment variable** `CLIENT_ID`


### Cloudflare credentials setup

1) After signing up into a **Cloudflare**, navigate to _Zero Trust_ tab and create an account.

- Free Plan has decent limits, so just choose it.
- Skip providing payment method step by choosing _Cancel and exit_ (top right corner)
- Go back to _Zero Trust_ tab

2) Create a **Cloudflare API token**, from https://dash.cloudflare.com/profile/api-tokens

with 2 permissions:

    Account.Zero Trust : Edit

    Account.Account Firewall Access Rules : Edit

Set API token to **environment variable** `AUTH_SECRET`

3) Get your **Account ID** from : https://dash.cloudflare.com/?to=/:account/workers

Set **Account ID** to **environment variable** `CLIENT_ID`

---

## Setup profile

Set **environment variable** `DNS` with DNS provider name (**Cloudflare** or **NextDNS**)

---

## Setup data sources

Each data source must be a link to a hosts file,
e.g. https://raw.githubusercontent.com/Internet-Helper/GeoHideDNS/refs/heads/main/hosts/hosts

You can provide multiple sources split by coma:
https://first.com/hosts,https://second.com/hosts

### 1) Setup Redirects

Set sources to **environment variable** `REDIRECT`

Script will parse sources, filtering out redirects to `0.0.0.0` and `127.0.0.1`

Thus, parsing lines:

    0.0.0.0 domain.to.block
    1.2.3.4 domain.to.redirect
    127.0.0.1 another.to.block

will keep only `1.2.3.4 domain.to.redirect` for the further redirect processing.

+ Redirect priority follows sources order. If domain appears more than one time, the first only IP will be applied.

### 2) Setup Blocklist

Set sources to **environment variable** `BLOCK`

Script will parse sources, keeping only redirects to `0.0.0.0`, `127.0.0.1`, `::1`, and also lines containing domain only.

Thus, parsing lines

    1.2.3.4 domain.to.redirect
    0.0.0.0 domain.to.block
    127.0.0.1 another.to.block
    ::1 ipv6.to.block
    no-ip.just.domain

will keep only

    domain.to.block
    another.to.block
    no-ip.just.domain
    ipv6.to.block
for the further block processing.

+ You may want to provide the same source for both `BLOCK` and `REDIRECT` for **Cloudflare**.
+ For **NextDNS**, the best option might be to set `REDIRECT` only, and then manually choose any blocklists at the
  _Privacy_ tab.

### 3) Setup NextDNS rewrite exclusions (optional)
Set JSON config to **environment variable** `NEXTDNS_REWRITE_EXCLUSIONS`

```json
{"patterns":["*.instagram.com","*.facebook.com"]}
```

- `patterns` filters matching domains out of new NextDNS rewrite creation.
- This config is evaluated during the NextDNS `REDIRECT` rewrite flow.
- `cleanupExisting` controls only deletion of already existing matching rewrites in NextDNS.
- If `cleanupExisting` is omitted, it defaults to `false`.
- If `cleanupExisting=false`, new matching rewrites are still filtered out, but existing matching rewrites are left untouched.
- If no `REDIRECT` sources are provided, exclusion filtering and exclusion cleanup do not run.
- Matching is case-insensitive and strips leading `www.` before comparison.
- For convenience, a leading wildcard like `*.instagram.com` matches both `instagram.com` and its subdomains.
- If the JSON is invalid, the run fails with a clear configuration error.

---

## Setup exclude redirects (optional)

Put domains to **environment variable** `EXCLUDE_REDIRECT` separated by coma, e.g. `instagram.com,twitch.com`

These domains and their subdomains:

- will be removed from existing redirect rules;
- won't be added with new ones.

---

## Multiple profiles setup

### Restrictions
All profiles get _similar_ settings. That means `BLOCK`, `REDIRECT`, `EXCLUDE_REDIRECT` and `NEXTDNS_REWRITE_EXCLUSIONS` are **shared**.

### Multiple profiles of single provider

Put your profiles separated by coma **without whitespace** into related **environment variables**.
E.g., two NextDNS profiles must be set as shown:

- `AUTH_SECRET` has: `secret_NextDns_1,secret_NextDns_2`
- `CLIENT_ID` has `client_id_NextDns_1,client_id_NextDns_2`

### Multiple profiles of different providers

In addition to setting above, list provider for each profile in **environment variable** `DNS`. For example:

- `DNS` has: `NEXTDNS,CLOUDFLARE,NEXTDNS`
- `AUTH_SECRET` has: `secret_NextDns_1,secret_Cloudflare_1,secret_NextDns_2`
- `CLIENT_ID` has `client_id_NextDns_1,client_id_Cloudflare_1,client_id_NextDns_2`

---

## Script Behaviour

### Cloudflare

Previously generated data will be removed. Script recognizes old data by marks:

+ Name prefix for List: **_Blocked websites by script_** and **_Override websites by script_**
+ Name prefix for Rule: **_Rules set by script_**
+ Different **_Session id_**. **_Session id_** is stored in a description field.

After removing old data, new lists and rules will be generated and applied.

If you want to clear **Cloudflare** block/redirect settings, launch the script without providing sources in related
**environment variables**. E.g. providing no value for **environment variable** `BLOCK` will cause removing old related
data: lists and rules used to setup blocks.

### NextDNS

For `REDIRECT`:

+ Existing domain will be updated if redirect IP has changed
+ If new domains are provided, they will be added
+ The rest redirect settings are kept untouched
+ Domains matching `NEXTDNS_REWRITE_EXCLUSIONS.patterns` are skipped from new rewrite creation

For `BLOCK`:

+ If new domains are provided, they will be added
+ The rest block settings are kept untouched

For `NEXTDNS_REWRITE_EXCLUSIONS`:
+ If `cleanupExisting=true`, existing matching rewrites are removed through the existing rate-limited API path
+ If `cleanupExisting=false` or the field is omitted, existing matching rewrites stay as-is

Previously generated data is removed **ONLY** when both `BLOCK` and `REDIRECT` sources were not provided.

## Docker-based validation

You do not need Java or Maven on the host machine.

Run local validation only through Docker:

```bash
docker compose run --rm validate
```

This runs `mvn -B clean test package` inside the container and verifies the minimal exclusion-filtering tests plus the packaged build.

Use `.env.example` as the placeholder for values. If you want a local non-committed copy, duplicate it to `.env.local`.

## Local apply through Docker

If you want to apply real changes to NextDNS from your own machine, you can run the application through Docker as well.

1. Create a local non-committed env file:

```bash
cp .env.example .env.local
```

2. Fill `.env.local` with your real values.
3. I recommend the first run with:

```json
{"patterns":["*.instagram.com","*.facebook.com"],"cleanupExisting":false}
```

4. Run the real apply flow:

```bash
docker compose --profile apply run --rm apply
```

This command builds the jar inside the container and then runs it with your `.env.local` values, so it can modify your live NextDNS profile.

What to watch in the logs:
- `Loaded ... NextDNS rewrite exclusion patterns. Existing cleanup: ...`
- `Skipping ... rewrite candidates due to exclusion patterns`
- `Removing ... excluded rewrites from NextDNS` when `cleanupExisting=true`
- `Saving ... new rewrites to NextDNS...`
- `Skipped ... malformed override lines` if the upstream hosts source contains broken rows without a domain
- `No block sources provided; skipping denylist update` when you intentionally run a REDIRECT-only setup

Important:
- There is no dry-run mode here.
- `docker compose run --rm validate` is the safe check.
- `docker compose --profile apply run --rm apply` is the real mutation command.
- Exclusion filtering and exclusion cleanup are part of the `REDIRECT` rewrite flow, so they only run when `REDIRECT` sources are configured.

---

## GitHub Actions setup

#### Step-by-step video guide: [REDIRECT for NextDNS](https://www.youtube.com/watch?v=vbAXM_xAL5I)

#### Steps

1) Fork repository
2) Go _Settings_ => _Environments_
3) Create _New environment_ with name `DNS`
4) Provide `AUTH_SECRET` and `CLIENT_ID` to **Environment secrets**
5) Provide `DNS`, `REDIRECT`, `BLOCK`, optional `EXCLUDE_REDIRECT` and optional `NEXTDNS_REWRITE_EXCLUSIONS` to **Environment variables**

+ The action will be launched every day at **01:30 UTC**. To set another time, change cron at `.github/workflows/github_action.yml`
+ You can run the action manually via `Run workflow` button: switch to _Actions_ tab and choose workflow named **DNS Block&Redirect Configurer cron task**

#### What to verify after a manual workflow run
- The job should finish with **Success**
- The logs should show rewrite source loading, any `Skipped ... malformed override lines` summary, and the final `Saving ... new rewrites to NextDNS...` step
- For a REDIRECT-only setup, verify the `No block sources provided; skipping denylist update` log entry and confirm that the denylist was left untouched
- If `cleanupExisting=true`, also verify the `Removing ... excluded rewrites from NextDNS` log entry
- After a successful run, open the target NextDNS profile and confirm that the expected rewrites were created or updated, while domains matched by `NEXTDNS_REWRITE_EXCLUSIONS.patterns` were not recreated
