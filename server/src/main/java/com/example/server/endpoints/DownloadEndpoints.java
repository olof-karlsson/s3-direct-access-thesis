package com.example.server.endpoints;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.server.Main;
import com.example.server.TimeUtils;
import com.example.server.STSutil.STSTokenCreator;
import com.example.server.downloadServices.PresignUrlDownloadService;
import com.example.server.downloadServices.StreamServerDownloadService;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.sts.model.Credentials;

/**
 * RESTful service for handling file downloads.
 */
@Path("/files")
public class DownloadEndpoints {
  private static final Logger logger = LoggerFactory.getLogger(DownloadEndpoints.class);

  @GET
  @Path("/download/{fileName}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public void downloadFile(@PathParam("fileName") String fileName, @Suspended AsyncResponse asyncResponse) {

    try {
      String currentMethod = Main.getCurrentMethod();

      if ("streamS3ObjectViaServer".equals(currentMethod)) {
        StreamServerDownloadService.asyncStreamS3ObjectViaServer(fileName, asyncResponse);
      } else if ("presign".equals(currentMethod)) {
        PresignUrlDownloadService.handlePresignedMethod(fileName) //
          .thenAccept(asyncResponse::resume) //
          .exceptionally(ex -> {
            asyncResponse.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to generate presigned URL: " + ex.getMessage()).build());
            return null;
          });

      } else {
        asyncResponse.resume(Response.status(Response.Status.BAD_REQUEST).entity("Unsupported method: " + currentMethod).build());
      }
    } catch (Exception e) {
      System.err.println("Unexpected error: " + e.getMessage());
      e.printStackTrace();
      asyncResponse.resume(handleException(e));
    }
  }

  @GET
  @Path("/download-accesspoint/{fileName}/{clientId}")
  @Produces(MediaType.APPLICATION_JSON)
  public void asyncAccesspointUploadController(@PathParam("fileName") String fileName, @PathParam("clientId") String clientId, @Suspended AsyncResponse asyncResponse, @Context HttpHeaders headers) {

    String accesspointArn = Main.accesspointArn();

    Map<String, String> response = new HashMap<>();
    try {
      Credentials credential = STSTokenCreator.generateSTSToken(clientId);

      if (credential == null || accesspointArn == null || fileName == null || fileName.isBlank()) {
        throw new IllegalArgumentException("Missing required parameters or credentials.");
      }

      logger.info(String.format("[%s] [AccessPoint]   Initiating download via access point | AccessPointAlias: %s | Key: %s", TimeUtils.getCurrentTimestamp(), accesspointArn, fileName));

      response.put("accesspointArn", accesspointArn);
      response.put("accessKeyId", credential.accessKeyId());
      response.put("secretAccessKey", credential.secretAccessKey());
      response.put("sessionToken", credential.sessionToken());

      logger.info(String.format("[%s] [AccessPoint]   Successfully retrieved access point alias | AccessPointAlias: %s | for file: %s", TimeUtils.getCurrentTimestamp(), accesspointArn, fileName));

      asyncResponse.resume(Response.ok(response).build());

    } catch (Exception e) {
      e.printStackTrace();

      logger.info(String.format("[%s] [AccessPoint]   Failed to retrieve credentials or access point alias | Error: %s | File: %s", TimeUtils.getCurrentTimestamp(), e.getMessage(), fileName));

      asyncResponse.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Map.of("error", "Failed to retrieve credentials or access point")).type(MediaType.APPLICATION_JSON).build());
    }
  }

  private Response handleException(Throwable cause) {
    if (cause instanceof S3Exception) {
      S3Exception s3Ex = (S3Exception)cause;
      return Response.status(s3Ex.statusCode()).entity(s3Ex.getMessage()).build();
    }
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error retrieving file: " + cause.getMessage()).build();
  }
}
