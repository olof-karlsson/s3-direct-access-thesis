package com.example.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MyHttpClient {
  private final HttpClient httpClient;

  public MyHttpClient(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  // Simple method to download a file
  public HttpResponse<String> downloadFile(String url) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
