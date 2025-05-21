package com.example.server.endpoints;

import com.example.server.TimeUtils;

// Jakarta
import jakarta.ws.rs.GET; // Annotation to define a GET HTTP endpoint.
import jakarta.ws.rs.Path; // Specifies the URL path for a REST resource.
import jakarta.ws.rs.Produces; // Defines the media type of responses (e.g., text, JSON, binary).
import jakarta.ws.rs.core.MediaType; // Provides predefined media type constants (e.g., JSON, OCTET_STREAM).
import jakarta.ws.rs.core.Response; // Represents an HTTP response, used to return status codes and data.

/**
 * RESTful service to see server health
 */
@Path("/files")
public class HealthResourceEndpoints {

  /**
   * Health check endpoint to verify that the server is running.
   * This endpoint responds with a simple message indicating the server status.
   *
   * @return HTTP 200 OK with "Server is healthy" message.
   */
  @GET
  @Path("/health")
  @Produces(MediaType.TEXT_PLAIN)
  public Response healthCheck() {
    System.out.println("[" + TimeUtils.getCurrentTimestamp() + "] [HealthCheck]   Server is healthy");

    return Response.ok("Server is healthy").build();
  }
}
