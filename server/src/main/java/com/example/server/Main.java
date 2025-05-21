package com.example.server;

//Java standard library imports
import java.io.BufferedReader; // Reads text from an input stream (used for reading command output).
import java.io.InputStreamReader; // Converts byte streams to character streams for easier processing.
import java.net.URI;
import java.nio.file.Path;

//Grizzly and Jersey dependencies for handling HTTP server and REST API
import org.glassfish.grizzly.http.server.HttpServer; // Provides a lightweight HTTP server to run the REST API.
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory; // Creates and configures a Grizzly-based HTTP server.
import org.glassfish.jersey.media.multipart.MultiPartFeature; // Enables support for handling multipart/form-data requests (file uploads).
import org.glassfish.jersey.server.ResourceConfig; // Configures REST resources, scans for JAX-RS endpoints, and registers features.
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cdimascio.dotenv.Dotenv;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

public class Main {
  private static S3Client s3Client = null;

  static Dotenv dotenv = Dotenv.load();
  private Path dynamicPath;

  private static Region region = Region.[REGION];

  private static final String accessKeyID = dotenv.get("AWS_ACCESS_KEY_ID");
  private static final String secretAccessKey = dotenv.get("AWS_SECRET_ACCESS_KEY");
  private static StaticCredentialsProvider credentialsProvider = null;

  private static StaticCredentialsProvider explicitCredentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyID, secretAccessKey));

  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static String bucketName = null;
  private static S3AsyncClient s3AsyncClient = null;
  private static S3AsyncClient s3AsyncMultiClient = null;
  private static S3Presigner s3Presigner = null;
  private static String accesspointArn = dotenv.get("AWS_ACCESSPOINT_ARN");

  // Methods
  //presign
  //iam
  //accesspoints
  //streamS3ObjectViaServer
  private static String currentMethod = "presign";

  // Initiating the server
  public static void main(String[] args) {

    // Creating credentials for the server
    s3Client = S3Client.builder() //
      .region(region) //
      .credentialsProvider(explicitCredentialsProvider) //
      //.credentialsProvider(DefaultCredentialsProvider.create()) //
      .build(); //

    // For presign
    s3Presigner = S3Presigner.builder() //
      .region(region) //
      .credentialsProvider(explicitCredentialsProvider) //
      //.credentialsProvider(DefaultCredentialsProvider.create()) //
      .build();

    // For streamS3ObjectViaServer
    s3AsyncClient = S3AsyncClient.builder() //
      .region(region) //
      .multipartEnabled(false) //
      .credentialsProvider(explicitCredentialsProvider) //
      //.credentialsProvider(DefaultCredentialsProvider.create()) //
      .build();

    s3AsyncMultiClient = S3AsyncClient.builder() //
      .region(region) //
      .multipartEnabled(true) //
      .credentialsProvider(explicitCredentialsProvider) //
      //.credentialsProvider(DefaultCredentialsProvider.create()) //
      .multipartConfiguration(b -> b //
        .thresholdInBytes(8 * 1024 * 1024L)//
        .minimumPartSizeInBytes(8 * 1024 * 1024L) //
      ).build();

    // Test: singlepart and multipart
    bucketName = dotenv.get("AWS_BUCKET_NAME");

    // -------------------------------------

    // Start the server
    // Create and configure the Jersey application
    ResourceConfig config = new ResourceConfig();

    // Scan this package for annotated REST resources
    config.packages("com.example.server.endpoints");

    // Enable multipart/form-data support (e.g., file uploads)
    config.register(MultiPartFeature.class)//
      .register(org.glassfish.jersey.jackson.JacksonFeature.class); //
    // Register Jackson JSON support for automatic (de)serialization
    // so @Consumes(MediaType.APPLICATION_JSON) works:
    // Used in presign multipart upload durint the multipart-complete-presign 
    .register(org.glassfish.jersey.jackson.JacksonFeature.class);//

    // Ensure the port is not in use
    checkAndKillPortIfInUse(3000);

    HttpServer server = GrizzlyHttpServerFactory.createHttpServer( //
      URI.create("http://localhost:3000/"), config //
    );

    logger.info("Server running at http://localhost:3000/");

    // Shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      logger.info("Shutting down server...");
      server.shutdownNow();
    }));
  }

  // Methods to retrieve static configurations
  // --------------------------------------------------------------------

  public static S3Client getS3Client() {
    return s3Client;
  }

  public static S3AsyncClient getAsyncS3Client() {
    return s3AsyncClient;
  }

  public static S3AsyncClient getAsyncS3MultiClient() {
    return s3AsyncMultiClient;
  }

  public static S3Presigner getS3Presigner() {
    return s3Presigner;
  }

  public static String getCurrentMethod() {
    return currentMethod;
  }

  public static void setCurrentMethod(String currentMethod) {
    Main.currentMethod = currentMethod;
  }

  public static StaticCredentialsProvider getCredentialsProvider() {
    return credentialsProvider;
  }

  public static String getBucketName() {
    return bucketName;
  }

  public static Region getRegion() {
    return region;
  }

  public static String accesspointArn() {
    return accesspointArn;
  }

  // Kill the server if in use
  // --------------------------------------------------------------------

  private static void checkAndKillPortIfInUse(int port) {
    try {
      // Execute netstat command to check if the port is in use
      String command = "netstat -ano | findstr " + port;
      ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
      Process process = processBuilder.start();

      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.contains("LISTENING")) {
          // Extract the PID from the netstat output
          String[] parts = line.split("\\s+");
          String pid = parts[parts.length - 1];

          // Kill the process using the extracted PID
          String killCommand = "taskkill /PID " + pid + " /F";
          System.out.println("Port " + port + " is already in use by process with PID: " + pid);
          System.out.println("Killing process...");

          // Use ProcessBuilder to run taskkill
          ProcessBuilder killProcessBuilder = new ProcessBuilder("cmd.exe", "/c", killCommand);
          killProcessBuilder.start();
          break;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
