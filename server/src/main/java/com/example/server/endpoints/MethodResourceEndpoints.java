package com.example.server.endpoints;

import java.util.Set;

import com.example.server.Main;
import com.example.server.TimeUtils;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * RESTful service to manage server method (services)
 */
@Path("/files")
public class MethodResourceEndpoints {
  /**
   * Endpoint to change the current method.
   * This endpoint updates the method used in the server logic dynamically.
   *
   * @param method The new method to use.
   * @return HTTP 200 OK with a confirmation message.
   */
  @POST
  @Path("/method/{method}")
  @Produces(MediaType.TEXT_PLAIN)
  public Response updateCurrentMethod(@PathParam("method") String method) {
    System.out.println("[" + TimeUtils.getCurrentTimestamp() + "] [ChangeMethod]  Changing method to: " + method);

    //List of allowed methods
    Set<String> allowedMethods = Set.of("presign", "iam", "accesspoints", "streamS3ObjectViaServer");

    if (!allowedMethods.contains(method)) {
      String message = "Invalid method: " + method + ". Allowed values are: " + String.join(", ", allowedMethods);
      System.out.println("[" + TimeUtils.getCurrentTimestamp() + "] [ChangeMethod]   " + message);
      return Response.status(Response.Status.BAD_REQUEST).entity(message).build();
    }

    Main.setCurrentMethod(method);

    return Response.ok("Changed to method: " + method).build();
  }

  // ----------

  /**
   * Endpoint to return which communication method is
   * currently used on the server
   *
   * @return A simple response with a message.
   */
  @GET
  @Path("/request-method")
  @Produces(MediaType.TEXT_PLAIN)
  public Response getCurrentMethod() {
    //String message = "presign";
    String message = Main.getCurrentMethod();

    System.out.println("[" + TimeUtils.getCurrentTimestamp() + "] [ModeSelection] Active file retrieval method = " + message);

    return Response.ok(message).build();
  }
}
