package org.linlinjava.announce.listing.infrastructure.integration;

import org.linlinjava.announce.listing.application.ImportReport;
import org.linlinjava.announce.listing.application.ListingImportService;
import org.linlinjava.announce.listing.domain.ListingSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.file.dsl.Files;
import org.springframework.integration.file.support.FileExistsMode;
import org.springframework.integration.file.FileWritingMessageHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.io.File;
import java.time.Duration;

/**
 * Spring Integration flow that feeds the importer from the crawler's output directory
 * ({@code announce.listing.import.dir}): a {@code FileReadingMessageSource} polled every
 * {@code announce.listing.import.poll} picks up one {@code dba_*.json[l]} or {@code tradera_*.json[l]} file per poll
 * (accept-once, so a file is imported a single time per process), the handler runs
 * {@link ListingImportService#importFile} for the source named by the file prefix, and a file outbound adapter moves
 * the file to {@code done/} (or {@code failed/} when the import threw, so a broken file is not retried every poll).
 * The importer itself knows nothing about files or directories; a Kafka source can replace this flow later.
 */
@Configuration
@ConditionalOnProperty(prefix = "announce.listing.import", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ListingImportFlow {

    private static final Logger log = LoggerFactory.getLogger(ListingImportFlow.class);

    public static final String FEED_FILE_PATTERN = "^(dba|tradera)_.*\\.jsonl?$";
    public static final String DONE_DIR = "done";
    public static final String FAILED_DIR = "failed";
    static final String OUTCOME_HEADER = "listing_import_outcome";
    static final String REPORT_HEADER = "listing_import_report";

    private final ListingImportService importer;
    private final File dir;
    private final Duration poll;

    public ListingImportFlow(ListingImportService importer,
                             @Value("${announce.listing.import.dir:/home/bimeni/announce_crawled_data}") String dir,
                             @Value("${announce.listing.import.poll:PT1M}") Duration poll) {
        this.importer = importer;
        this.dir = new File(dir);
        this.poll = poll;
    }

    @Bean
    public IntegrationFlow listingFeedFiles() {
        return IntegrationFlow
                .from(Files.inboundAdapter(dir).autoCreateDirectory(true).regexFilter(FEED_FILE_PATTERN).ignoreHidden(true),
                        e -> e.poller(Pollers.fixedDelay(poll).maxMessagesPerPoll(1)))
                .handle(File.class, (file, headers) -> importFile(file, headers))
                .route(Message.class, m -> m.getHeaders().get(OUTCOME_HEADER, String.class),
                        r -> r.subFlowMapping(DONE_DIR, f -> f.handle(moveTo(DONE_DIR)))
                                .subFlowMapping(FAILED_DIR, f -> f.handle(moveTo(FAILED_DIR))))
                .get();
    }

    private Message<File> importFile(File file, MessageHeaders headers) {
        String outcome;
        ImportReport report = null;
        try {
            ListingSource source = ListingSource.ofPrefix(file.getName());
            report = importer.importFile(source, file.toPath()).block();
            outcome = DONE_DIR;
        } catch (RuntimeException e) {
            log.error("import of {} failed; moving it to {}/", file, FAILED_DIR, e);
            outcome = FAILED_DIR;
        }
        return MessageBuilder.withPayload(file).copyHeaders(headers)
                .setHeader(OUTCOME_HEADER, outcome).setHeader(REPORT_HEADER, report)
                .setHeader(FileHeaders.FILENAME, file.getName()).build();
    }

    private FileWritingMessageHandler moveTo(String sub) {
        return Files.outboundAdapter(new File(dir, sub)).autoCreateDirectory(true)
                .fileExistsMode(FileExistsMode.REPLACE).deleteSourceFiles(true).getObject();
    }
}
