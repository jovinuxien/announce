package org.linlinjava.announce.gatewayapi.storage;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Pictures of the announces, like litemall's {@code /wx/storage}: a member uploads an image
 * ({@code POST /api/storage/upload}, multipart field {@code file}), the edge stores it under
 * {@code announce.storage.dir}, records it in {@code announce_storage} and answers the URL to put in the gallery;
 * {@code GET /api/storage/fetch/{key}} serves it to everyone. Only images, at most {@code announce.storage.max-size}.
 */
@RestController
@RequestMapping("/api/storage")
public class StorageController {

    public static final String FETCH_PATH = "/api/storage/fetch/";
    static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/avif", "image/svg+xml");
    private static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", "jpg", "image/png", "png", "image/gif", "gif",
            "image/webp", "webp", "image/avif", "avif", "image/svg+xml", "svg");
    private static final Pattern KEY = Pattern.compile("[a-f0-9]{32}\\.[a-z0-9]{3,4}");

    private final StorageProperties props;
    private final StorageRecords records;

    public StorageController(StorageProperties props, StorageRecords records) throws IOException {
        this.props = props;
        this.records = records;
        Files.createDirectories(props.dir());
    }

    /** {@code {key, url, name, type, size}}; the SPA appends {@code url} to the gallery. */
    public record UploadResponse(String key, String url, String name, String type, long size) {
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<UploadResponse>> upload(@RequestPart("file") FilePart file) {
        MediaType contentType = file.headers().getContentType();
        String type = contentType == null ? "" : (contentType.getType() + "/" + contentType.getSubtype()).toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(type)) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "only images are accepted: " + IMAGE_TYPES));
        }
        String key = UUID.randomUUID().toString().replace("-", "") + "." + EXTENSIONS.get(type);
        Path target = props.dir().resolve(key);
        long max = props.maxSize().toBytes();
        return file.transferTo(target)
                .then(Mono.fromCallable(() -> Files.size(target)))
                .flatMap(size -> {
                    if (size > max) {
                        return Mono.fromRunnable(() -> delete(target))
                                .then(Mono.error(new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "at most " + props.maxSize())));
                    }
                    StoredFile stored = new StoredFile(key, safeName(file.filename()), type, size, props.publicBase() + FETCH_PATH + key);
                    return records.save(stored).thenReturn(stored);
                })
                .map(s -> ResponseEntity.status(HttpStatus.CREATED).body(new UploadResponse(s.key(), s.url(), s.name(), s.type(), s.size())));
    }

    @GetMapping("/fetch/{key}")
    public Mono<ResponseEntity<Resource>> fetch(@PathVariable String key) {
        if (!KEY.matcher(key).matches()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        Path file = props.dir().resolve(key);
        if (!Files.isRegularFile(file)) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return records.findByKey(key)
                .map(r -> MediaType.parseMediaType(r.type()))
                .defaultIfEmpty(MediaType.APPLICATION_OCTET_STREAM)
                .map(type -> ResponseEntity.ok()
                        .contentType(type)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                        .body((Resource) new FileSystemResource(file)));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail status(ResponseStatusException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(e.getStatusCode(), e.getReason());
        p.setTitle(HttpStatus.valueOf(e.getStatusCode().value()).getReasonPhrase());
        return p;
    }

    private static String safeName(String name) {
        String n = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return n.length() > 255 ? n.substring(n.length() - 255) : n;
    }

    private static void delete(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
