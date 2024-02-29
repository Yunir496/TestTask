package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


interface Document {
}

@AllArgsConstructor
@Getter
@Setter
class CrptApi {
    private final int requestLimit;
    private final TimeUnit timeUnit;
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final Lock lock = new ReentrantLock();
    private final Condition limitCondition = lock.newCondition();
    private final Gson gson;
    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    private final HttpPost httpPost = new HttpPost("https://ismp.crpt.ru/api/v3/lk/documents/create");

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("Лимит запросов не может быть меньше или равен 0");
        }
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDate.class, (JsonSerializer<LocalDate>) (localDate, type, jsonSerializationContext) ->
                        jsonSerializationContext.serialize(localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))))
                .create();
    }

    public static void main(String[] args) {
        Product product = new Product(
                "string",
                LocalDate.of(2020, 1, 23),
                "string",
                "string",
                "string",
                LocalDate.of(2020, 1, 23),
                "string",
                "string",
                "string"
        );
        Description description = new Description("string");
        Document document = new DocumentImpl(
                description,
                "string",
                "string",
                DocType.LP_INTRODUCE_GOODS,
                true,
                "string",
                "string",
                "string",
                LocalDate.of(2020, 1, 23),
                "string",
                List.of(product),
                LocalDate.of(2020, 1, 23),
                "string"
        );

        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 10);
        for (int i = 0; i < 100; i++) {
            new Thread(() -> {
                try {
                    crptApi.createDocument(document, "подпись");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }

    }

    public void createDocument(Document document, String signature) throws InterruptedException {
        lock.lock();
        try {
            while (requestCount.get() >= requestLimit) {
                limitCondition.await();
            }
            String payload = gson.toJson(document);
            httpPost.addHeader("Content-Type", "application/json");
            httpPost.addHeader("Signature", signature); //считаю что передается в зашифрованном виде в метод

            StringEntity entity = new StringEntity(payload);
            httpPost.setEntity(entity);

            CloseableHttpResponse res = httpClient.execute(httpPost);
            System.out.println(res.getStatusLine().getStatusCode()); //log

            requestCount.incrementAndGet();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
        timeUnit.sleep(1);

        lock.lock();
        try {
            requestCount.decrementAndGet();
            limitCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }
}

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
class DocumentImpl implements Document{
    private Description description;
    @SerializedName("doc_id")
    private String docId;
    @SerializedName("doc_status")
    private String docStatus;
    @SerializedName("doc_type")
    private DocType docType;
    @SerializedName("importRequest")
    private boolean importRequest;
    @SerializedName("owner_inn")
    private String ownerInn;
    @SerializedName("participant_inn")
    private String participantInn;
    @SerializedName("producer_inn")
    private String producerInn;
    @SerializedName("production_date")
    private LocalDate productionDate;
    @SerializedName("production_type")
    private String productionType;
    private List<Product> products;
    @SerializedName("reg_date")
    private LocalDate regDate;
    @SerializedName("reg_number")
    private String regNumber;
}

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
class Description {
    @SerializedName("participantInn")
    private String participantInn;
}

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
class Product {
    @SerializedName("certificate_document")
    private String certificateDocument;
    @SerializedName("certificate_document_date")
    private LocalDate certificateDocumentDate;
    @SerializedName("certificate_document_number")
    private String certificateDocumentNumber;
    @SerializedName("owner_inn")
    private String ownerInn;
    @SerializedName("producer_inn")
    private String producerInn;
    @SerializedName("production_date")
    private LocalDate productionDate;
    @SerializedName("tnved_code")
    private String tnvedCode;
    @SerializedName("uit_code")
    private String uitCode;
    @SerializedName("uitu_code")
    private String uituCode;
}
enum DocType{
    LP_INTRODUCE_GOODS, ETC
}