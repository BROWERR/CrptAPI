import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CrptApi {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore semaphore;

    private static final String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final Logger log = Logger.getLogger(CrptApi.class.getName());

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
        this.semaphore = new Semaphore(requestLimit);

        long periodInMillis = timeUnit.toMillis(1);
        Runnable task = () -> {
            try {
                semaphore.release(requestLimit - semaphore.availablePermits());
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        Thread periodicTask = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(periodInMillis);
                    task.run();
                } catch (InterruptedException e) {
                    log.log(Level.WARNING, "Interrupted!", e);
                    Thread.currentThread().interrupt();
                }
            }
        });
        periodicTask.setDaemon(true);
        periodicTask.start();
    }

    /**
     * Метод для отправки запроса на создание документа
     *
     * @param document  документ
     * @param signature подпись
     * @throws InterruptedException
     * @throws IOException
     */
    public void createDocument(Object document, String signature) throws InterruptedException, IOException {
        semaphore.acquire();
        try {
            String jsonDocument = objectMapper.writeValueAsString(document);
            StringEntity requestEntity = new StringEntity(jsonDocument, ContentType.APPLICATION_JSON);
            HttpPost httpPost = new HttpPost(URL);
            httpPost.setEntity(requestEntity);

            httpPost.addHeader("Signature", signature);

            HttpResponse response = httpClient.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new StatusResponseException("Failed with HTTP error code: " + statusCode);
            }
        } finally {
            semaphore.release();
        }
    }

}

class StatusResponseException extends RuntimeException {
    public StatusResponseException(String messsage) {
        super(messsage);
    }
}
