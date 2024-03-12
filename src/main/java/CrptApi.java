import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final String URL = "https://ismp.crpt.ru/api/v3/lk";
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final long timeIntervalMillis;
    private int requestsMade;
    private long startTimeMillis;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.create(URL);
        this.timeIntervalMillis = timeUnit.toMillis(1);
        this.requestsMade = 0;
        this.startTimeMillis = System.currentTimeMillis();
    }

    private synchronized void checkRateLimit() {
        long currentTime = System.currentTimeMillis();
        long timeElapsed = currentTime - startTimeMillis;

        if (timeElapsed >= timeIntervalMillis) {
            requestsMade = 0;
            startTimeMillis = currentTime;
        }

        if (requestsMade >= requestLimit) {
            try {
                wait(timeIntervalMillis - timeElapsed + 1);
                requestsMade = 0;
                startTimeMillis = System.currentTimeMillis();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public Mono<Document> createDocument(Document document, String token) throws IOException {
        checkRateLimit();

        String jsonBody = objectMapper.writeValueAsString(document);

        return webClient
                .post()
                .uri("/documents/create")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(jsonBody))
                .retrieve()
                .bodyToMono(Document.class)
                .doOnSuccess(success -> {
                    requestsMade++;
                });
    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 10);

        String token = "api_token";

        Document document = new Document();
        document.setDocId("doc123");
        document.setDocStatus("draft");
        document.setDocType("LP_INTRODUCE_GOODS");
        document.setImportRequest(true);
        document.setOwnerInn("ownerInn123");
        document.setParticipantInn("participantInn123");
        document.setProducerInn("producerInn123");
        document.setProductionDate("2020-01-23");
        document.setProductionType("productionType123");
        document.setRegDate("2020-01-23");
        document.setRegNumber("regNumber123");

        Product product = new Product();
        product.setCertificateDocument("cert123");
        product.setCertificateDocumentDate("2020-01-23");
        product.setCertificateDocumentNumber("certNumber123");
        product.setOwnerInn("ownerInn123");
        product.setProducerInn("producerInn123");
        product.setProductionDate("2020-01-23");
        product.setTnvedCode("tnved123");
        product.setUitCode("uit123");
        product.setUituCode("uitu123");

        document.getProducts().add(product);

        try {
            Mono<Document> responseMono = crptApi.createDocument(document, token);
            responseMono.subscribe(
                    response -> {
                        System.out.println("API Response:");
                        System.out.println(response);
                    },
                    error -> {
                        System.err.println("API Error:");
                        error.printStackTrace();
                    }
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Document {
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private List<Product> products;
        private String regDate;
        private String regNumber;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Product {
        private String certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;
    }
}
