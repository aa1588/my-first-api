package helloworld;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {

  private final App app = new App();

  @Test
  void returnsSuccessWithUserId() {
    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withHttpMethod("GET")
            .withPathParameters(Map.of("id", "42"));

    APIGatewayProxyResponseEvent response = app.handleRequest(request, null);

    assertEquals(200, response.getStatusCode());
    assertTrue(response.getBody().contains("42"));
  }

  @Test
  void handlesNullPathParams() {
    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withHttpMethod("GET");

    APIGatewayProxyResponseEvent response = app.handleRequest(request, null);

    assertEquals(200, response.getStatusCode());
    assertNotNull(response.getBody());
  }
}