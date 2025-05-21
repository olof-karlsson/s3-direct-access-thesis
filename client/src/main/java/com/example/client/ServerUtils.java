package com.example.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient; // Need Java 11+
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.client.utils.TimeUtils;

/**
 * Class for server status related operations.
 */
public class ServerUtils {
  private static final Logger logger = LoggerFactory.getLogger(App.class);

  // ------------------------------------------------------------------------
  // Check if the server is running
  // Endpoint: (GET http://localhost:3000/)
  // ------------------------------------------------------------------------
  public static boolean isServerUp(HttpClient client) {
    // Prepare the HTTP GET request to check server status.
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:3000/files/health")) // health endpoint
      .timeout(Duration.ofSeconds(2)) // Set a timeout of 2 seconds to avoid hanging.
      .GET() // Define HTTP method as GET.
      .build();

    try {
      // Send the request and receive the response.
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      // Return true if the server responds with HTTP 200 OK and healthy
      return response.statusCode() == 200 && response.body().contains("healthy");

    } catch (IOException e) {
      logger.error("Server connection failed: {}", e.getMessage());
      return false;

    } catch (InterruptedException e) {
      // Handle thread interruption, restore interrupted status.
      Thread.currentThread().interrupt();
      logger.error("Server check interrupted");
      return false;
    }
  }

  //------------------------------------------------------------------------
  // Check current method
  // Endpoint: (GET /files/request-method)
  // ------------------------------------------------------------------------
  public static String getCurrentMethod(HttpClient client) {
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:3000/files/request-method")).build();

    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        System.out.println("[" + TimeUtils.getCurrentTimestamp() + "] [GetMethod]    Current Server Download Method: " + response.body());
        return response.body();
      } else {
        logger.error("Failed to get method. Status: {}", response.statusCode());
        return null;
      }
    } catch (Exception e) {
      logger.error("Error checking method", e);
      return null;
    }
  }

  //------------------------------------------------------------------------
  // Change the current method
  // Endpoint: POST /files/method/{method}
  // ------------------------------------------------------------------------
  public static String changeCurrentMethod(HttpClient client, String newMethod) {

    // Build the POST request to update the method
    HttpRequest request = HttpRequest.newBuilder() //
      .uri(URI.create("http://localhost:3000/files/method/" + newMethod)) //
      .POST(HttpRequest.BodyPublishers.noBody()) //
      .build();

    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        System.out.println("[" + TimeUtils.getCurrentTimestamp() + "] [GetMethod]    Current Server Download Method: " + response.body());

        System.out.println("[" + TimeUtils.getCurrentTimestamp() + "] [ChangeMethod] " + response.body());
        return response.body();

      } else {
        logger.error("Failed to get method. Status: {}", response.statusCode());
        return null;
      }
    } catch (Exception e) {
      logger.error("Error checking method", e);
      return null;
    }
  }

}
