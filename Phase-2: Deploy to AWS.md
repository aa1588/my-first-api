# Building a Java REST API with AWS SAM — Phase 2: Deploy to AWS

This is Phase 2 of the AWS SAM Java series. By the end of Phase 1 you had a working Java Lambda running locally on `localhost:3000`. In this phase you deploy it to real AWS, set up isolated dev and prod environments, manage secrets with SSM Parameter Store, and add full observability with CloudWatch and X-Ray.

You will need an AWS account and credentials configured before starting.

---

## Verify Your AWS Credentials

Before touching anything, confirm your credentials are working:

```bash
aws sts get-caller-identity
```

Expected output:

```json
{
    "UserId": "AIDA...",
    "Account": "123456789012",
    "Arn": "arn:aws:iam::123456789012:user/your-username"
}
```

Also confirm your region is set:

```bash
aws configure get region
# us-east-1
```

If both commands return values you are ready to deploy.

---

## Step 7 — Your First Deploy

Run this from your project root:

```bash
cd ~/Desktop/my-first-api
sam deploy --guided
```

Answer the prompts as follows:

```
Stack Name                              → my-first-api-dev
AWS Region                              → us-east-1
Parameter Environment                   → dev
Confirm changes before deploy           → Y
Allow SAM CLI IAM role creation         → Y
Disable rollback                        → N
GetUserFunction may not have auth...    → y
Save arguments to configuration file   → Y
SAM configuration file                  → samconfig.toml
SAM configuration environment          → dev
```

SAM will show you a changeset — a preview of every resource it is about to create:

```
CloudFormation stack changeset
-----------------------------------------------------------------------
Operation  LogicalResourceId            ResourceType
-----------------------------------------------------------------------
+ Add      GetUserFunction              AWS::Lambda::Function
+ Add      GetUserFunctionRole          AWS::IAM::Role
+ Add      GetUserFunctionGetUser...    AWS::Lambda::Permission
+ Add      MyApi                        AWS::ApiGateway::RestApi
+ Add      MyApidevStage                AWS::ApiGateway::Stage
+ Add      ServerlessDeploymentApp...   AWS::S3::Bucket
-----------------------------------------------------------------------
```

Type `Y` to confirm. The first deploy takes 60 to 90 seconds. When it finishes you will see:

```
CloudFormation outputs from deployed stack
-----------------------------------------------------------------------
Key         ApiEndpoint
Value       https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/dev/users
-----------------------------------------------------------------------

Successfully created/updated stack - my-first-api-dev in us-east-1
```

Test your live API:

```bash
curl https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/dev/users/42
```

Expected response:

```json
{"message":"Hello from Lambda","userId":"42"}
```

That is your Java code running on real AWS Lambda behind a real API Gateway.

---

## Step 8 — What SAM Created Behind the Scenes

SAM took your 40-line `template.yaml` and created six AWS resources automatically. Inspect them:

```bash
aws cloudformation describe-stack-resources \
  --stack-name my-first-api-dev \
  --query 'StackResources[*].{Type:ResourceType,Name:LogicalResourceId,Status:ResourceStatus}' \
  --output table
```

Here is what each resource does:

**`GetUserFunction`** is your Lambda function with your fat jar uploaded to it.

**`GetUserFunctionRole`** is the IAM execution role SAM created automatically. This is the badge your Lambda carries when it calls other AWS services.

**`GetUserFunctionGetUserPermission`** is a Lambda resource-based policy that allows API Gateway to invoke your function. This is the bouncer on Lambda's door with API Gateway's name on the guest list.

**`MyApi`** is the API Gateway REST API with your `/users/{id}` route.

**`MyApidevStage`** is the `dev` stage which forms the `/dev/` part of your URL.

**`ServerlessDeploymentApplicationRepository`** is the S3 bucket SAM created to store your fat jar for deployment.

Check your stack outputs anytime:

```bash
aws cloudformation describe-stacks \
  --stack-name my-first-api-dev \
  --query 'Stacks[0].Outputs' \
  --output table
```

Check your Lambda configuration directly:

```bash
aws lambda get-function-configuration \
  --function-name get-user-dev \
  --query '{Runtime:Runtime,Memory:MemorySize,Timeout:Timeout,Handler:Handler}'
```

Expected output:

```json
{
    "Runtime": "java21",
    "Memory": 512,
    "Timeout": 30,
    "Handler": "helloworld.App::handleRequest"
}
```

---

## Step 9 — Multi-Environment Setup

Right now you have one environment. You need dev and prod to be completely isolated — separate Lambda functions, separate API Gateway, separate IAM roles, separate everything. A bad deploy to dev must never touch prod.

### Update samconfig.toml

Open `samconfig.toml` and add the prod block at the bottom:

```toml
version = 0.1

[default.global.parameters]
stack_name = "my-first-api"

[default.build.parameters]
cached = true
parallel = true

[default.validate.parameters]
lint = true

[default.deploy.parameters]
capabilities = "CAPABILITY_IAM"
confirm_changeset = true
resolve_s3 = true

[default.sync.parameters]
watch = true

[default.local_start_api.parameters]
warm_containers = "EAGER"

[default.local_start_lambda.parameters]
warm_containers = "EAGER"

[dev.deploy.parameters]
stack_name = "my-first-api-dev"
resolve_s3 = true
s3_prefix = "my-first-api-dev"
region = "us-east-1"
confirm_changeset = true
capabilities = "CAPABILITY_IAM"
parameter_overrides = "Environment=\"dev\""
image_repositories = []

[dev.global.parameters]
region = "us-east-1"

[prod.deploy.parameters]
stack_name = "my-first-api-prod"
resolve_s3 = true
s3_prefix = "my-first-api-prod"
region = "us-east-1"
confirm_changeset = true
capabilities = "CAPABILITY_IAM"
parameter_overrides = "Environment=\"prod\""
image_repositories = []

[prod.global.parameters]
region = "us-east-1"
```

Deploying to each environment is now a single flag:

```bash
# Deploy to dev
sam deploy --config-env dev

# Deploy to prod
sam deploy --config-env prod
```

### Deploy to Prod

```bash
sam build && sam deploy --config-env prod
```

Type `Y` when prompted with the changeset. When done you have two completely separate stacks:

```
my-first-api-dev  →  https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/dev/users
my-first-api-prod →  https://yyyyyyyyyy.execute-api.us-east-1.amazonaws.com/prod/users
```

Test both:

```bash
curl https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/dev/users/1
curl https://yyyyyyyyyy.execute-api.us-east-1.amazonaws.com/prod/users/1
```

Both return the same response but are running on completely separate infrastructure.

---

## Step 10 — Secrets and Config with SSM Parameter Store

Hardcoding config values like database URLs or API keys in your code or `template.yaml` is the wrong approach. SSM Parameter Store is the right way. You store a value in SSM once and your Lambda reads it at deploy time. Dev and prod have separate SSM paths so they never share config.

### Create Parameters in SSM

```bash
# Dev parameters
aws ssm put-parameter \
  --name "/my-first-api/dev/table-name" \
  --value "users-dev" \
  --type String

aws ssm put-parameter \
  --name "/my-first-api/dev/log-level" \
  --value "DEBUG" \
  --type String

# Prod parameters
aws ssm put-parameter \
  --name "/my-first-api/prod/table-name" \
  --value "users-prod" \
  --type String

aws ssm put-parameter \
  --name "/my-first-api/prod/log-level" \
  --value "INFO" \
  --type String
```

For actual secrets like API keys use `SecureString`. SSM encrypts it with KMS automatically:

```bash
aws ssm put-parameter \
  --name "/my-first-api/dev/api-key" \
  --value "your-secret-key" \
  --type SecureString
```

### Update template.yaml

Replace your `template.yaml` with this version that reads SSM values and injects them as environment variables:

```yaml
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31
Description: My REST API

Globals:
  Function:
    Runtime: java21
    Architectures: [x86_64]
    MemorySize: 512
    Timeout: 30
    Tracing: Active
    Environment:
      Variables:
        ENV: !Ref Environment
        TABLE_NAME: !Sub "{{resolve:ssm:/my-first-api/${Environment}/table-name}}"
        LOG_LEVEL: !Sub "{{resolve:ssm:/my-first-api/${Environment}/log-level}}"
        POWERTOOLS_SERVICE_NAME: !Sub "my-first-api-${Environment}"
        POWERTOOLS_METRICS_NAMESPACE: MyFirstApi

Parameters:
  Environment:
    Type: String
    Default: dev
    AllowedValues: [dev, prod]

Resources:

  MyApi:
    Type: AWS::Serverless::Api
    Properties:
      StageName: !Ref Environment
      TracingEnabled: true
      Cors:
        AllowMethods: "'GET,POST,PUT,DELETE,OPTIONS'"
        AllowHeaders: "'Content-Type,Authorization'"
        AllowOrigin: "'*'"

  GetUserFunction:
    Type: AWS::Serverless::Function
    Properties:
      FunctionName: !Sub "get-user-${Environment}"
      CodeUri: HelloWorldFunction/
      Handler: helloworld.App::handleRequest
      Policies:
        - SSMParameterReadPolicy:
            ParameterName: "my-first-api/${Environment}/*"
        - AWSXRayDaemonWriteAccess
      Events:
        GetUser:
          Type: Api
          Properties:
            RestApiId: !Ref MyApi
            Path: /users/{id}
            Method: GET

Outputs:
  ApiEndpoint:
    Description: API Gateway endpoint URL
    Value: !Sub "https://${MyApi}.execute-api.${AWS::Region}.amazonaws.com/${Environment}/users"
```

Two things to notice. The `{{resolve:ssm:...}}` syntax tells CloudFormation to fetch the SSM value at deploy time and inject it as an environment variable. The `SSMParameterReadPolicy` is a SAM policy template that automatically generates the correct IAM permissions — no manual IAM policy writing needed.

### Update App.java

Read the environment variables as class fields so they are loaded once at cold start, not on every request:

```java
package helloworld;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Read once at cold start — not on every request
    private final String tableName = System.getenv("TABLE_NAME");
    private final String logLevel  = System.getenv("LOG_LEVEL");
    private final String env       = System.getenv("ENV");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent input, Context context) {

        if (context != null) {
            context.getLogger().log("ENV=" + env + " TABLE=" + tableName + " LOG_LEVEL=" + logLevel);
        }

        try {
            String userId = input.getPathParameters() != null
                    ? input.getPathParameters().get("id")
                    : "unknown";

            Map<String, Object> body = Map.of(
                    "message", "Hello from Lambda",
                    "userId",  userId,
                    "env",     env,
                    "table",   tableName
            );

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(objectMapper.writeValueAsString(body));

        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Internal Server Error\"}");
        }
    }
}
```

### Deploy and Verify

```bash
sam build && sam deploy --config-env dev
```

Test it:

```bash
curl https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/dev/users/42
```

Expected response — the SSM values are now injected:

```json
{
  "message": "Hello from Lambda",
  "userId": "42",
  "env": "dev",
  "table": "users-dev"
}
```

Deploy to prod and test:

```bash
sam build && sam deploy --config-env prod
curl https://yyyyyyyyyy.execute-api.us-east-1.amazonaws.com/prod/users/42
```

```json
{
  "message": "Hello from Lambda",
  "userId": "42",
  "env": "prod",
  "table": "users-prod"
}
```

Same code, completely different config, no hardcoded values anywhere.

---

## Step 11 — Observability: CloudWatch, X-Ray and Lambda Powertools

Observability means being able to answer three questions when something goes wrong in production. What happened? Where did it slow down? Why did it fail? You need structured logs, distributed traces, and metrics.

### Part 1 — CloudWatch Logs

Your Lambda already writes to CloudWatch automatically. Tail your logs live:

```bash
# Tail live logs
sam logs --stack-name my-first-api-dev --tail

# In another terminal hit your API
curl https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/dev/users/42
```

Query historical logs:

```bash
# Logs from the last 10 minutes
sam logs --stack-name my-first-api-dev --start-time "10min ago"

# Logs for a specific function
sam logs --name get-user-dev --start-time "1h ago"
```

### Part 2 — Lambda Powertools for Java

Plain `context.getLogger()` writes unstructured text which is hard to query. Lambda Powertools gives you structured JSON logging, X-Ray tracing, and metrics with almost no extra code. It is the standard observability library for Lambda.

**Add Powertools to pom.xml:**

Add these dependencies inside your `<dependencies>` block:

```xml
<!-- Structured logging -->
<dependency>
  <groupId>software.amazon.lambda</groupId>
  <artifactId>powertools-logging</artifactId>
  <version>1.18.0</version>
</dependency>

<!-- X-Ray tracing -->
<dependency>
  <groupId>software.amazon.lambda</groupId>
  <artifactId>powertools-tracing</artifactId>
  <version>1.18.0</version>
</dependency>

<!-- Metrics -->
<dependency>
  <groupId>software.amazon.lambda</groupId>
  <artifactId>powertools-metrics</artifactId>
  <version>1.18.0</version>
</dependency>
```

Add the AspectJ plugin inside your `<build><plugins>` block — Powertools uses annotations that need it:

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>aspectj-maven-plugin</artifactId>
  <version>1.14.0</version>
  <configuration>
    <source>21</source>
    <target>21</target>
    <complianceLevel>21</complianceLevel>
    <aspectLibraries>
      <aspectLibrary>
        <groupId>software.amazon.lambda</groupId>
        <artifactId>powertools-logging</artifactId>
      </aspectLibrary>
      <aspectLibrary>
        <groupId>software.amazon.lambda</groupId>
        <artifactId>powertools-tracing</artifactId>
      </aspectLibrary>
      <aspectLibrary>
        <groupId>software.amazon.lambda</groupId>
        <artifactId>powertools-metrics</artifactId>
      </aspectLibrary>
    </aspectLibraries>
  </configuration>
  <executions>
    <execution>
      <goals><goal>compile</goal></goals>
    </execution>
  </executions>
</plugin>
```

**Update App.java to use Powertools:**

```java
package helloworld;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.metrics.Metrics;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.util.Map;

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // Powertools logger — writes structured JSON to CloudWatch
    private static final Logger log = LogManager.getLogger(App.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String tableName = System.getenv("TABLE_NAME");
    private final String env       = System.getenv("ENV");

    @Override
    @Logging(logEvent = true)   // logs the full incoming event automatically
    @Tracing                    // creates an X-Ray trace for every invocation
    @Metrics(namespace = "MyFirstApi", service = "UserService")
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent input, Context context) {

        String userId = input.getPathParameters() != null
                ? input.getPathParameters().get("id")
                : "unknown";

        log.info("Processing get user request",
                Map.of("userId", userId, "env", env, "table", tableName));

        try {
            Map<String, Object> body = Map.of(
                    "message", "Hello from Lambda",
                    "userId",  userId,
                    "env",     env,
                    "table",   tableName
            );

            log.info("Request successful", Map.of("userId", userId, "statusCode", 200));

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(objectMapper.writeValueAsString(body));

        } catch (Exception e) {
            log.error("Request failed", Map.of("userId", userId, "error", e.getMessage()));

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Internal Server Error\"}");
        }
    }
}
```

The three annotations do all the heavy lifting. `@Logging` automatically logs the incoming event and adds request ID and function name to every log line. `@Tracing` creates an X-Ray trace segment for every invocation. `@Metrics` emits custom CloudWatch metrics using EMF format.

### Part 3 — Deploy and Verify Observability

Build and deploy:

```bash
sam build && sam deploy --config-env dev
```

Hit the API several times:

```bash
for i in 1 2 3 4 5; do
  curl https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/dev/users/$i
done
```

Check structured logs:

```bash
sam logs --stack-name my-first-api-dev --start-time "5min ago"
```

Each log line is now structured JSON:

```json
{
  "level": "INFO",
  "message": "Processing get user request",
  "userId": "42",
  "env": "dev",
  "table": "users-dev",
  "function_name": "get-user-dev",
  "function_request_id": "756bb795-...",
  "timestamp": "2026-04-29T03:13:44Z"
}
```

Check X-Ray traces:

```bash
aws xray get-service-graph \
  --start-time $(date -u -v-10M +%s) \
  --end-time $(date -u +%s)
```

Or go to the AWS Console → X-Ray → Traces for a visual timeline of every request.

### What You Now Have

Every request automatically produces three things:

```
Structured Log  →  CloudWatch Logs  →  queryable JSON with request context
Trace           →  X-Ray            →  visual timeline of the full request flow
Metric          →  CloudWatch       →  invocation count, duration, and errors
```

---

## Phase 2 Summary

Here is everything you built in this phase:

| Step | What You Did |
|---|---|
| Step 7 | Deployed to real AWS with `sam deploy --guided` |
| Step 8 | Inspected the six CloudFormation resources SAM created |
| Step 9 | Added isolated dev and prod stacks via `samconfig.toml` |
| Step 10 | Injected environment-specific config from SSM Parameter Store |
| Step 11 | Added structured logging, X-Ray tracing, and metrics via Powertools |

Your two live environments:

```
dev  →  https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/dev/users
prod →  https://yyyyyyyyyy.execute-api.us-east-1.amazonaws.com/prod/users
```

---

## Next Steps — Phase 3

In Phase 3 you will automate all of this with GitHub Actions so every push to your repository triggers a build, runs tests, deploys to dev, and after approval deploys to prod. No manual `sam deploy` commands ever again.

The most important thing you will set up in Phase 3 is OIDC authentication — a way for GitHub Actions to assume an IAM role securely without storing any AWS credentials in GitHub.