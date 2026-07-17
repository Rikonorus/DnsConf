package com.novibe.common.data_sources;

import com.novibe.common.exception.ProcessException;
import com.novibe.common.util.DataParser;
import com.novibe.common.util.EnvParser;
import com.novibe.common.util.Log;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Caches exactly one REDIRECT download/parse result for the whole application
 * context. Both DNS runners and proxy mode consume this same object.
 */
@Service
public class RedirectSourceSnapshotProvider {

    private final HttpClient httpClient;
    private RedirectSourceSnapshot snapshot;

    public RedirectSourceSnapshotProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public synchronized RedirectSourceSnapshot load() {
        if (snapshot != null) return snapshot;

        List<RedirectSourceRecord> records = new ArrayList<>();
        int malformed = 0;
        List<String> sources = EnvParser.parse(System.getenv("REDIRECT"));
        for (String source : sources) {
            String body = fetch(source);
            for (String line : DataParser.splitByEol(body).toList()) {
                if (line.isBlank() || !DataParser.hasMeaningfulContent(line)) continue;
                ParsedRecord parsed = parseRedirectableRecord(line);
                if (parsed.malformed()) {
                    malformed++;
                }
                if (parsed.record() != null) {
                    records.add(parsed.record());
                }
            }
        }
        snapshot = new RedirectSourceSnapshot(List.copyOf(records), malformed);
        Log.common("Loaded %s redirect source records%s".formatted(
                records.size(), malformed == 0 ? "" : "; skipped %s malformed records".formatted(malformed)
        ));
        return snapshot;
    }

    private String fetch(String source) {
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(source)).timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                throw new ProcessException("Unable to load redirect source (HTTP %s)".formatted(response.statusCode()));
            }
            return response.body();
        } catch (ProcessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ProcessException("Unable to load redirect source", exception);
        }
    }

    private ParsedRecord parseRedirectableRecord(String line) {
        DataParser.HostsPair pair = DataParser.parseHostsPair(line);
        if (pair == null) return new ParsedRecord(null, true);
        if (HostsBlockListsLoader.isBlockIp(pair.ip())) return new ParsedRecord(null, false);
        String sourceHostname = DataParser.normalizeHostname(pair.hostname());
        if (sourceHostname == null) return new ParsedRecord(null, true);
        return new ParsedRecord(new RedirectSourceRecord(pair.ip(), sourceHostname, DataParser.removeWWW(sourceHostname)), false);
    }

    private record ParsedRecord(RedirectSourceRecord record, boolean malformed) {
    }
}
