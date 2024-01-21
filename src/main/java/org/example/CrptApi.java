package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private ReentrantLock locker;
    private Condition condition;
    private static long[] invokeTimes;
    private static int nextIndex = 0;
    private static long timeAwait;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        invokeTimes = new long[requestLimit];
        locker = new ReentrantLock();
        condition = locker.newCondition();
        timeAwait = timeUnit.toMillis(1);
    }

    public void post(Map<String,String> document, String signature) {
        locker.lock();
        try {
            while ((new Date().getTime() - invokeTimes[nextIndex]) <= timeAwait) {
                condition.await(100, TimeUnit.MILLISECONDS);
            }

            ObjectMapper objectMapper = new ObjectMapper();
            String requestDocument = objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(document);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofString(requestDocument), HttpRequest.BodyPublishers.ofString(signature)))
                    .build();

            HttpResponse<?> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            System.out.println(response.statusCode());

            invokeTimes[nextIndex] = new Date().getTime();
            nextIndex = (nextIndex == requestLimit - 1) ? 0 : nextIndex + 1;
            condition.signalAll();
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } finally {
            locker.unlock();
        }
    }
}