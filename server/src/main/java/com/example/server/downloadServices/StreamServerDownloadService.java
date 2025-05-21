package com.example.server.downloadServices;

import java.io.IOException;
import java.io.InputStream;

import com.example.server.Main;
import com.example.server.TimeUtils;

import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class StreamServerDownloadService {

  /**
   * SOLUTION 1:
   * Streams directly from S3 using a blocking InputStream, minimizing memory use (best for large files).
   * Uses .join(), which blocks the thread until the download completes.
   *
   * Asynchronously streams a file from S3 to the client using JAX-RS async response.
   *
   * @param fileName       The name (key) of the file in the S3 bucket.
   * @param asyncResponse  The JAX-RS async response object to stream data back to the client.
   */
  public static void asyncStreamS3ObjectViaServer(String fileName, AsyncResponse asyncResponse) throws IOException {
    // Get AWS S3 client and bucket name from the application's configuration
    S3AsyncClient s3AsyncClient = Main.getAsyncS3Client();
    String bucketName = Main.getBucketName();

    // Prepare a GetObjectRequest to fetch the file from S3
    GetObjectRequest objectRequest = GetObjectRequest.builder().key(fileName).bucket(bucketName).build();

    System.out.println("[" + TimeUtils.getCurrentTimestamp() + "] [FileDownload]  Starting S3 download request | Bucket: " + bucketName + " | Key: " + fileName);

    ResponseInputStream<GetObjectResponse> s3InputStream;
    try {
      // Asynchronously fetch the object from S3 and convert it to a blocking input stream
      s3InputStream = s3AsyncClient.getObject(objectRequest, AsyncResponseTransformer.toBlockingInputStream()) //
        .exceptionally(ex -> {
          // Handle async exception and log the error
          System.err.println("Async S3 error: " + ex.getMessage());
          ex.printStackTrace();
          return null;
        }).join(); // Block until the operation completes

      if (s3InputStream == null) {
        throw new RuntimeException("S3 getObject returned null stream");
      }

      // Define a StreamingOutput that transfers data from the S3 input stream to the HTTP output stream
      StreamingOutput stream = output -> {
        try (InputStream in = s3InputStream) {
          in.transferTo(output); // Stream the entire file content to the client
          System.out.println("[" + TimeUtils.getCurrentTimestamp() + "] [FileDownload]  Download to Client complete ✔");
        }
      };

      // Build the HTTP response with headers for file download
      Response response = Response.ok(stream).header("Content-Disposition", "attachment; filename=\"" + fileName + "\"") //
        .header("Content-Length", s3InputStream.response().contentLength()) //
        .header("Cache-Control", "no-store, must-revalidate") //
        .header("Pragma", "no-cache") //
        .build();

      // Resume the async response with the built response object
      asyncResponse.resume(response);

    } catch (Exception e) {
      // Handle any exceptions during the S3 request or streaming
      System.err.println("Final exception: " + e.getMessage());
      System.err.println("[" + TimeUtils.getCurrentTimestamp() + "] [FileDownload]  Final exception: " + e.getMessage());
      e.printStackTrace();
      asyncResponse.resume(handleException(e));
    }
  }

  /**
   * Converts exceptions into an appropriate HTTP response.
   *
   * @param cause The exception that occurred.
   * @return A Response with the relevant status and error message.
   */
  private static Response handleException(Throwable cause) {
    if (cause instanceof S3Exception) {
      S3Exception s3Ex = (S3Exception)cause;
      return Response.status(s3Ex.statusCode()).entity(s3Ex.getMessage()).build();
    }
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error retrieving file: " + cause.getMessage()).build();
  }

  /**
   * SOLUTION 2:
   * Downloads the entire file into memory as a byte array (simpler but not ideal for large files).
   */
//  public static void asyncStreamS3ObjectViaServer(String fileName, AsyncResponse asyncResponse) {
//    S3AsyncClient s3AsyncClient = Main.getAsyncS3Client();
//    String bucketName = Main.getBucketName();
//
//    GetObjectRequest objectRequest = GetObjectRequest.builder().key(fileName).bucket(bucketName).build();
//
//    System.out.printf("[%s] [FileDownload] Starting S3 download request | Bucket: %s | Key: %s%n", TimeUtils.getCurrentTimestamp(), bucketName, fileName);
//
//    s3AsyncClient.getObject(objectRequest, AsyncResponseTransformer.toBytes()).whenComplete((s3BytesResponse, ex) -> {
//      if (ex != null) {
//        System.err.println("Async S3 error: " + ex.getMessage());
//        ex.printStackTrace();
//        asyncResponse.resume(handleException(ex));
//        return;
//      }
//
//      GetObjectResponse s3Response = s3BytesResponse.response();
//      byte[] fileData = s3BytesResponse.asByteArray();
//
//      // Log available data size from the response; this is now the total size of your file.
//      System.out.println("Downloaded file size: " + fileData.length + " bytes");
//
//      try {
//        // Define a StreamingOutput that creates a fresh input stream from fileData
//        StreamingOutput stream = output -> {
//          // Create a new ByteArrayInputStream from the buffered fileData.
//          try (InputStream in = new ByteArrayInputStream(fileData)) {
//            in.transferTo(output);
//          }
//        };
//
//        Response response = Response.ok(stream).header("Content-Disposition", "attachment; filename=\"" + fileName + "\"").header("Content-Length", s3Response.contentLength()).header("Cache-Control", "no-store, must-revalidate").header("Pragma", "no-cache").build();
//
//        asyncResponse.resume(response);
//      } catch (Exception e) {
//        System.err.println("Error creating response: " + e.getMessage());
//        asyncResponse.resume(handleException(e));
//      }
//    });
//  }
}
