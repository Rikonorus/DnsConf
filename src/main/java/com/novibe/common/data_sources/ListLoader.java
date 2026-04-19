package com.novibe.common.data_sources;

import com.novibe.common.util.DataParser;
import com.novibe.common.util.DataParser.HostsLine;
import com.novibe.common.util.Log;
import lombok.Cleanup;
import lombok.Setter;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Setter(onMethod_ = @Autowired)
public abstract class ListLoader<T> {

    private HttpClient client;

    protected abstract T toObject(HostsLine line);

    protected abstract String listType();

    protected abstract Predicate<HostsLine> filterRelatedLines();

    @SneakyThrows
    @SuppressWarnings("preview")
    public List<T> fetchWebsites(List<String> urls) {
        @Cleanup var scope = StructuredTaskScope.open();
        List<StructuredTaskScope.Subtask<String>> requests = new ArrayList<>();
        urls.stream()
                .map(url -> scope.fork(() -> fetchList(url)))
                .forEach(requests::add);
        scope.join();

        List<HostsLine> parsedLines = new ArrayList<>();
        int malformedCount = 0;
        LinkedHashSet<String> malformedExamples = new LinkedHashSet<>();

        for (StructuredTaskScope.Subtask<String> request : requests) {
            List<String> lines = DataParser.splitByEol(request.get())
                    .filter(line -> !line.isBlank())
                    .map(String::toLowerCase)
                    .toList();

            for (String line : lines) {
                Optional<HostsLine> hostsLine = DataParser.parseHostsLine(line);
                if (hostsLine.isPresent()) {
                    parsedLines.add(hostsLine.get());
                    continue;
                }

                if (DataParser.hasMeaningfulContent(line)) {
                    malformedCount++;
                    if (malformedExamples.size() < 3) {
                        malformedExamples.add(DataParser.summarizeForLog(line));
                    }
                }
            }
        }

        logMalformedLines(malformedCount, malformedExamples);

        return parsedLines.parallelStream()
                .filter(filterRelatedLines())
                .distinct()
                .map(this::toObject)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @SneakyThrows
    private String fetchList(String url) {
        Log.io("Loading %s list from url: %s".formatted(listType(), url));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
    }

    private void logMalformedLines(int malformedCount, LinkedHashSet<String> malformedExamples) {
        if (malformedCount > 0) {
            String examples = malformedExamples.stream()
                    .map(example -> "`%s`".formatted(example))
                    .collect(Collectors.joining(", "));

            Log.io("Skipped %s malformed %s lines. Examples: %s"
                    .formatted(malformedCount, listType().toLowerCase(), examples));
        }
    }

}
