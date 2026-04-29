# Building a Java REST API with AWS SAM — Phase 4: Production Grade

This is Phase 4 of the AWS SAM Java series. By the end of Phase 3 you had a fully automated CI/CD pipeline deploying your Java Lambda to dev and prod on every code push. Your API works. Now you make it production-ready.

Production-ready means five things. Cold starts are fast enough that users do not notice them. Failures are captured and alerted on before you find out from a customer. The API is protected against abuse and bad input. The infrastructure costs as little as possible for the traffic it handles. And every component runs with the minimum permissions it needs to do its job.

This phase covers all five.

---

## Step 19 — Cold Start Tuning

Java has the worst cold start problem of any Lambda runtime. When Lambda spins up a new container it has to start the JVM, load all your classes, and initialise every dependency before it can handle the first request. This can take 2 to 5 seconds on a fresh container, which is unacceptable for a user-facing API.

You have three weapons against this.

### Weapon 1 — SnapStart

SnapStart is the biggest win and costs nothing extra. It works by taking a snapshot of your Lambda container after the JVM has fully initialised. Instead of booting a cold JVM on every new container, Lambda restores from the cached snapshot. Cold starts drop from 2 to 5 seconds down to under 200ms.

Enable it in `template.yaml`:

```yaml
  GetUserFunction:
    Type: AWS::Serverless::Function
    Properties:
      FunctionName: !Sub "get-user-${Environment}"
      CodeUri: HelloWorldFunction/
      Handler: helloworld.App::handleRequest
      SnapStart:
        ApplyOn: PublishedVersions    # take snapshot after each new version
      AutoPublishAlias: live          # required — SnapStart needs a published version
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
```

`AutoPublishAlias: live` tells SAM to publish a new Lambda version on every deploy and point the `live` alias at it. SnapStart snapshots that published version. API Gateway is automatically updated to invoke the alias.

One important caveat — if your Lambda opens network connections or generates random values during initialisation, SnapStart can restore stale state. Register your class with the CRaC lifecycle to handle this cleanly.

Add the CRaC dependency to `pom.xml`:

```xml
<dependency>
  <groupId>io.github.crac</groupId>
  <artifactId>org-crac</artifactId>
  <version>0.1.3</version>
</dependency>
```

Implement the CRaC Resource interface in `App.java`:

```java
import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>,
        Resource {

    public App() {
        // Register this class for snapshot lifecycle events
        Core.getGlobalContext().register(this);
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) {
        // Called just before the snapshot is taken
        // Close any open connections here — HTTP clients, DB connections
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) {
        // Called immediately after restore from snapshot
        // Re-open connections and reinitialise anything time-sensitive here
    }
}
```

For a simple handler like yours that has no open connections, you can register without implementing anything in the methods. The snapshot will still be taken and restored correctly.

### Weapon 2 — Memory Sizing

Lambda allocates CPU proportionally to memory. A 512MB function gets half a vCPU. A 1024MB function gets a full vCPU. The JVM benefits enormously from more CPU during class loading and JIT compilation.

Counterintuitively, a 1024MB Lambda is often cheaper than a 512MB one because it completes faster and you pay per millisecond. Do not guess — measure. The Power Tuning tool in Step 22 will give you the exact number.

Update your Globals in `template.yaml` as a starting point:

```yaml
Globals:
  Function:
    Runtime: java21
    Architectures: [x86_64]
    MemorySize: 1024     # increase from 512 — Java needs more memory than other runtimes
    Timeout: 30
    Tracing: Active
```

### Weapon 3 — Provisioned Concurrency

For truly latency-sensitive paths you can pay to keep containers permanently pre-warmed. This eliminates cold starts entirely but costs money even when idle. Use this only for your most critical endpoints, such as checkout flows or payment processing where even a 200ms cold start is unacceptable.

```yaml
  GetUserFunctionAlias:
    Type: AWS::Lambda::Alias
    Properties:
      FunctionName: !Ref GetUserFunction
      FunctionVersion: !GetAtt GetUserFunction.Version
      Name: live
      ProvisionedConcurrencyConfig:
        ProvisionedConcurrentExecutions: 2   # 2 containers always warm
```

For most APIs SnapStart with increased memory is the right choice. Provisioned Concurrency is a last resort when you have measured SnapStart and it is still not fast enough.

---

## Step 20 — Error Handling

Unhandled errors in Lambda are silent by default. A request fails, something gets logged to CloudWatch, and nobody knows until a user complains. In production you need to capture every failure, alert on it immediately, and have a recovery path.

### Dead Letter Queue

A Dead Letter Queue captures failed invocations so you can inspect them, understand the failure, and replay them once the underlying problem is fixed. Add an SQS queue as the DLQ for your function.

Add to `template.yaml`:

```yaml
Resources:

  GetUserDLQ:
    Type: AWS::SQS::Queue
    Properties:
      QueueName: !Sub "get-user-dlq-${Environment}"
      MessageRetentionPeriod: 1209600   # retain messages for 14 days

  GetUserFunction:
    Type: AWS::Serverless::Function
    Properties:
      FunctionName: !Sub "get-user-${Environment}"
      CodeUri: HelloWorldFunction/
      Handler: helloworld.App::handleRequest
      SnapStart:
        ApplyOn: PublishedVersions
      AutoPublishAlias: live
      DeadLetterQueue:
        Type: SQS
        TargetArn: !GetAtt GetUserDLQ.Arn
      Policies:
        - SSMParameterReadPolicy:
            ParameterName: "my-first-api/${Environment}/*"
        - AWSXRayDaemonWriteAccess
        - SQSSendMessagePolicy:
            QueueName: !GetAtt GetUserDLQ.QueueName
      Events:
        GetUser:
          Type: Api
          Properties:
            RestApiId: !Ref MyApi
            Path: /users/{id}
            Method: GET
```

Check your DLQ for failed messages anytime:

```bash
aws sqs get-queue-attributes \
  --queue-url $(aws sqs get-queue-url \
    --queue-name get-user-dlq-dev \
    --query QueueUrl \
    --output text) \
  --attribute-names ApproximateNumberOfMessages
```

### CloudWatch Alarm on the DLQ

Know the moment something lands in your DLQ. Add this alarm to `template.yaml`:

```yaml
  DLQAlarm:
    Type: AWS::CloudWatch::Alarm
    Properties:
      AlarmName: !Sub "get-user-dlq-not-empty-${Environment}"
      AlarmDescription: Messages are appearing in the DLQ — investigate immediately
      MetricName: ApproximateNumberOfMessagesVisible
      Namespace: AWS/SQS
      Statistic: Sum
      Period: 60
      EvaluationPeriods: 1
      Threshold: 1
      ComparisonOperator: GreaterThanOrEqualToThreshold
      Dimensions:
        - Name: QueueName
          Value: !GetAtt GetUserDLQ.QueueName
```

This alarm fires within 60 seconds of the first message appearing in the DLQ. Wire it to an SNS topic and email subscription to get notified immediately.

### Structured Error Handling in Java

Never let exceptions bubble up to Lambda unhandled. Always catch at every level, log with context, and return a meaningful response with a consistent error shape.

Replace the error handling in `App.java`:

```java
@Override
@Logging(logEvent = true)
@Tracing
@Metrics(namespace = "MyFirstApi", service = "UserService")
public APIGatewayProxyResponseEvent handleRequest(
        APIGatewayProxyRequestEvent input, Context context) {

    String userId = null;

    try {
        userId = input.getPathParameters() != null
                ? input.getPathParameters().get("id")
                : null;

        if (userId == null || userId.isBlank()) {
            log.warn("Missing userId in path parameters");
            return errorResponse(400, "USER_ID_REQUIRED", "userId path parameter is required");
        }

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

    } catch (IllegalArgumentException e) {
        log.error("Validation error", Map.of("userId", String.valueOf(userId), "error", e.getMessage()));
        return errorResponse(400, "VALIDATION_ERROR", e.getMessage());

    } catch (Exception e) {
        log.error("Unexpected error", Map.of("userId", String.valueOf(userId), "error", e.getMessage()));
        return errorResponse(500, "INTERNAL_ERROR", "An unexpected error occurred");
    }
}

private APIGatewayProxyResponseEvent errorResponse(int statusCode, String code, String message) {
    try {
        Map<String, Object> error = Map.of("code", code, "message", message);
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(objectMapper.writeValueAsString(error));
    } catch (Exception e) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(500)
                .withBody("{\"code\":\"INTERNAL_ERROR\",\"message\":\"An unexpected error occurred\"}");
    }
}
```

Every error now returns a consistent shape:

```json
{
  "code": "USER_ID_REQUIRED",
  "message": "userId path parameter is required"
}
```

Callers always get a machine-readable `code` and a human-readable `message`. Never return stack traces or internal error details to the caller.

---

## Step 21 — API Best Practices

### Request Validation at API Gateway

Validate requests before they ever reach your Lambda. Malformed requests rejected at the gateway level cost you nothing — no Lambda invocation, no compute charge, instant feedback to the caller.

Update the `MyApi` resource in `template.yaml`:

```yaml
  MyApi:
    Type: AWS::Serverless::Api
    Properties:
      StageName: !Ref Environment
      TracingEnabled: true
      MethodSettings:
        - ResourcePath: "/*"
          HttpMethod: "*"
          ThrottlingBurstLimit: 100
          ThrottlingRateLimit: 50
          LoggingLevel: ERROR
          MetricsEnabled: true
      Cors:
        AllowMethods: "'GET,POST,PUT,DELETE,OPTIONS'"
        AllowHeaders: "'Content-Type,Authorization'"
        AllowOrigin: !If [IsProd, "'https://yourdomain.com'", "'*'"]
```

Add the condition for the CORS origin at the top level of your template:

```yaml
Conditions:
  IsProd: !Equals [!Ref Environment, prod]
```

Notice the CORS `AllowOrigin` is locked to your real domain in prod but open in dev. Never use `*` in production — it allows any website to call your API on behalf of your users.

### Throttling

The `MethodSettings` block sets two throttling limits that protect your API from being overwhelmed:

`ThrottlingBurstLimit: 100` is the maximum number of concurrent requests at any instant. Requests above this limit receive a `429 Too Many Requests` response immediately from API Gateway with no Lambda invocation.

`ThrottlingRateLimit: 50` is the maximum sustained requests per second. This is the steady-state rate limit.

Callers that are throttled get a `429` automatically. Your Lambda is never invoked and you are never charged for the rejected requests.

### Usage Plans and API Keys

For APIs consumed by external clients, add a usage plan so you can control and monitor per-client usage:

```yaml
  MyApiKey:
    Type: AWS::ApiGateway::ApiKey
    Properties:
      Name: !Sub "my-first-api-key-${Environment}"
      Enabled: true

  MyUsagePlan:
    Type: AWS::ApiGateway::UsagePlan
    Properties:
      UsagePlanName: !Sub "my-first-api-plan-${Environment}"
      ApiStages:
        - ApiId: !Ref MyApi
          Stage: !Ref Environment
      Throttle:
        BurstLimit: 100
        RateLimit: 50
      Quota:
        Limit: 10000
        Period: MONTH    # hard cap of 10,000 requests per month per key
```

Each external client gets their own API key. You can revoke a single key without affecting other clients, and CloudWatch tracks usage per key so you know exactly who is calling your API and how often.

---

## Step 22 — Cost Optimisation

### Lambda Power Tuning

Do not guess the optimal memory setting. Use the open-source Lambda Power Tuning tool to measure actual cost and duration across a range of memory configurations. It invokes your function multiple times at each memory level and returns a visualisation showing the cost-performance tradeoff at every setting.

Deploy the tool into your account once:

```bash
aws serverlessrepo create-cloud-formation-change-set \
  --application-id arn:aws:serverlessrepo:us-east-1:451282441545:applications/aws-lambda-power-tuning \
  --stack-name lambda-power-tuning \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides '[{"name":"lambdaResource","value":"*"}]'
```

Run it against your function:

```bash
aws stepfunctions start-execution \
  --state-machine-arn $(aws stepfunctions list-state-machines \
    --query 'stateMachines[?name==`powerTuningStateMachine`].stateMachineArn' \
    --output text) \
  --input '{
    "lambdaARN": "arn:aws:lambda:us-east-1:YOUR_ACCOUNT:function:get-user-dev",
    "powerValues": [256, 512, 1024, 2048, 3008],
    "num": 10,
    "payload": {"httpMethod":"GET","pathParameters":{"id":"42"}},
    "parallelInvocation": true,
    "strategy": "cost"
  }'
```

The output is a URL to a visualisation. Look for the knee of the curve — the point where doubling the memory no longer halves the duration. That is your optimal setting. For most Java APIs this lands around 1024MB to 1536MB.

### Arm64 Architecture

Switching from x86_64 to arm64 (AWS Graviton) gives you 20% cheaper compute per millisecond and typically 10 to 20% faster execution for Java workloads. For most workloads this is a free performance and cost improvement.

```yaml
Globals:
  Function:
    Runtime: java21
    Architectures: [arm64]    # change from x86_64 to arm64
    MemorySize: 1024
    Timeout: 30
    Tracing: Active
```

One important constraint — SnapStart currently only supports x86_64. You cannot use both SnapStart and arm64 on the same function. The decision comes down to your workload:

```
High traffic, latency sensitive   →  SnapStart on x86_64
Async processing, batch jobs      →  arm64 for cost savings
```

For a user-facing REST API SnapStart on x86_64 is almost always the better choice.

---

## Step 23 — Security Hardening

### Tighten IAM Roles

The IAM policies attached to your GitHub Actions role in Phase 3 used broad AWS-managed policies like `AWSCloudFormationFullAccess` and `AWSLambda_FullAccess`. These are too permissive for production. Replace them with a custom least-privilege policy scoped to exactly what your pipeline needs.

Create `iam-deploy-policy.json`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "CloudFormation",
      "Effect": "Allow",
      "Action": [
        "cloudformation:CreateStack",
        "cloudformation:UpdateStack",
        "cloudformation:DeleteStack",
        "cloudformation:DescribeStacks",
        "cloudformation:DescribeStackEvents",
        "cloudformation:DescribeStackResources",
        "cloudformation:GetTemplate",
        "cloudformation:ValidateTemplate",
        "cloudformation:CreateChangeSet",
        "cloudformation:ExecuteChangeSet",
        "cloudformation:DescribeChangeSet"
      ],
      "Resource": "arn:aws:cloudformation:us-east-1:YOUR_ACCOUNT:stack/my-first-api-*/*"
    },
    {
      "Sid": "Lambda",
      "Effect": "Allow",
      "Action": [
        "lambda:CreateFunction",
        "lambda:UpdateFunctionCode",
        "lambda:UpdateFunctionConfiguration",
        "lambda:GetFunction",
        "lambda:AddPermission",
        "lambda:RemovePermission",
        "lambda:PublishVersion",
        "lambda:CreateAlias",
        "lambda:UpdateAlias",
        "lambda:PutFunctionConcurrency"
      ],
      "Resource": "arn:aws:lambda:us-east-1:YOUR_ACCOUNT:function:get-user-*"
    },
    {
      "Sid": "S3Artifacts",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:CreateBucket",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::my-first-api-*",
        "arn:aws:s3:::my-first-api-*/*"
      ]
    },
    {
      "Sid": "SSM",
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters",
        "ssm:GetParametersByPath"
      ],
      "Resource": "arn:aws:ssm:us-east-1:YOUR_ACCOUNT:parameter/my-first-api/*"
    },
    {
      "Sid": "IAMPassRole",
      "Effect": "Allow",
      "Action": [
        "iam:PassRole",
        "iam:GetRole",
        "iam:CreateRole",
        "iam:AttachRolePolicy",
        "iam:DetachRolePolicy"
      ],
      "Resource": "arn:aws:iam::YOUR_ACCOUNT:role/my-first-api-*"
    },
    {
      "Sid": "APIGateway",
      "Effect": "Allow",
      "Action": [
        "apigateway:GET",
        "apigateway:POST",
        "apigateway:PUT",
        "apigateway:DELETE",
        "apigateway:PATCH"
      ],
      "Resource": "arn:aws:apigateway:us-east-1::/*"
    }
  ]
}
```

Apply it:

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
sed -i '' "s/YOUR_ACCOUNT/$ACCOUNT_ID/g" iam-deploy-policy.json

# Create the tight policy
aws iam create-policy \
  --policy-name github-actions-sam-deploy-policy \
  --policy-document file://iam-deploy-policy.json

# Detach the broad policies from Phase 3
aws iam detach-role-policy \
  --role-name github-actions-sam-role \
  --policy-arn arn:aws:iam::aws:policy/AWSCloudFormationFullAccess

aws iam detach-role-policy \
  --role-name github-actions-sam-role \
  --policy-arn arn:aws:iam::aws:policy/AWSLambda_FullAccess

aws iam detach-role-policy \
  --role-name github-actions-sam-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonAPIGatewayAdministrator

# Attach the least-privilege policy
aws iam attach-role-policy \
  --role-name github-actions-sam-role \
  --policy-arn arn:aws:iam::$ACCOUNT_ID:policy/github-actions-sam-deploy-policy
```

The difference between Phase 3 and Phase 4 IAM is scope. Phase 3 policies allow actions on any resource in your account. Phase 4 policies allow the same actions but only on resources named `my-first-api-*`. A compromised pipeline token cannot touch anything outside your project.

### Add WAF to API Gateway

WAF sits in front of API Gateway and blocks common attacks before they reach your Lambda. SQL injection, cross-site scripting, bad bots, and IP-based flooding are all handled automatically by AWS managed rule groups.

Add to `template.yaml`:

```yaml
  MyWAF:
    Type: AWS::WAFv2::WebACL
    Properties:
      Name: !Sub "my-first-api-waf-${Environment}"
      Scope: REGIONAL
      DefaultAction:
        Allow: {}
      Rules:
        - Name: AWSManagedRulesCommonRuleSet
          Priority: 1
          OverrideAction:
            None: {}
          Statement:
            ManagedRuleGroupStatement:
              VendorName: AWS
              Name: AWSManagedRulesCommonRuleSet
          VisibilityConfig:
            SampledRequestsEnabled: true
            CloudWatchMetricsEnabled: true
            MetricName: CommonRuleSetMetric

        - Name: RateLimitRule
          Priority: 2
          Action:
            Block: {}
          Statement:
            RateBasedStatement:
              Limit: 1000          # block IPs making more than 1000 requests per 5 minutes
              AggregateKeyType: IP
          VisibilityConfig:
            SampledRequestsEnabled: true
            CloudWatchMetricsEnabled: true
            MetricName: RateLimitMetric

      VisibilityConfig:
        SampledRequestsEnabled: true
        CloudWatchMetricsEnabled: true
        MetricName: !Sub "my-first-api-waf-${Environment}"

  MyWAFAssociation:
    Type: AWS::WAFv2::WebACLAssociation
    Properties:
      ResourceArn: !Sub
        - "arn:aws:apigateway:${AWS::Region}::/restapis/${ApiId}/stages/${Stage}"
        - ApiId: !Ref MyApi
          Stage: !Ref Environment
      WebACLArn: !GetAtt MyWAF.Arn
```

The `AWSManagedRulesCommonRuleSet` covers the OWASP Top 10 automatically. The `RateLimitRule` blocks any single IP that makes more than 1000 requests in a 5-minute window. Both rules are managed by AWS and updated as new threats emerge.

---

## Step 24 — The Final Production-Grade template.yaml

Here is the complete `template.yaml` incorporating everything from Phase 2 and Phase 4:

```yaml
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31
Description: My REST API — Production Grade

Globals:
  Function:
    Runtime: java21
    Architectures: [x86_64]
    MemorySize: 1024
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

Conditions:
  IsProd: !Equals [!Ref Environment, prod]

Resources:

  GetUserDLQ:
    Type: AWS::SQS::Queue
    Properties:
      QueueName: !Sub "get-user-dlq-${Environment}"
      MessageRetentionPeriod: 1209600

  MyApi:
    Type: AWS::Serverless::Api
    Properties:
      StageName: !Ref Environment
      TracingEnabled: true
      MethodSettings:
        - ResourcePath: "/*"
          HttpMethod: "*"
          ThrottlingBurstLimit: 100
          ThrottlingRateLimit: 50
          LoggingLevel: ERROR
          MetricsEnabled: true
      Cors:
        AllowMethods: "'GET,POST,PUT,DELETE,OPTIONS'"
        AllowHeaders: "'Content-Type,Authorization'"
        AllowOrigin: !If [IsProd, "'https://yourdomain.com'", "'*'"]

  GetUserFunction:
    Type: AWS::Serverless::Function
    Properties:
      FunctionName: !Sub "get-user-${Environment}"
      CodeUri: HelloWorldFunction/
      Handler: helloworld.App::handleRequest
      SnapStart:
        ApplyOn: PublishedVersions
      AutoPublishAlias: live
      DeadLetterQueue:
        Type: SQS
        TargetArn: !GetAtt GetUserDLQ.Arn
      Policies:
        - SSMParameterReadPolicy:
            ParameterName: "my-first-api/${Environment}/*"
        - AWSXRayDaemonWriteAccess
        - SQSSendMessagePolicy:
            QueueName: !GetAtt GetUserDLQ.QueueName
      Events:
        GetUser:
          Type: Api
          Properties:
            RestApiId: !Ref MyApi
            Path: /users/{id}
            Method: GET

  DLQAlarm:
    Type: AWS::CloudWatch::Alarm
    Properties:
      AlarmName: !Sub "get-user-dlq-not-empty-${Environment}"
      AlarmDescription: Messages appearing in the DLQ — investigate immediately
      MetricName: ApproximateNumberOfMessagesVisible
      Namespace: AWS/SQS
      Statistic: Sum
      Period: 60
      EvaluationPeriods: 1
      Threshold: 1
      ComparisonOperator: GreaterThanOrEqualToThreshold
      Dimensions:
        - Name: QueueName
          Value: !GetAtt GetUserDLQ.QueueName

  MyWAF:
    Type: AWS::WAFv2::WebACL
    Properties:
      Name: !Sub "my-first-api-waf-${Environment}"
      Scope: REGIONAL
      DefaultAction:
        Allow: {}
      Rules:
        - Name: AWSManagedRulesCommonRuleSet
          Priority: 1
          OverrideAction:
            None: {}
          Statement:
            ManagedRuleGroupStatement:
              VendorName: AWS
              Name: AWSManagedRulesCommonRuleSet
          VisibilityConfig:
            SampledRequestsEnabled: true
            CloudWatchMetricsEnabled: true
            MetricName: CommonRuleSetMetric
        - Name: RateLimitRule
          Priority: 2
          Action:
            Block: {}
          Statement:
            RateBasedStatement:
              Limit: 1000
              AggregateKeyType: IP
          VisibilityConfig:
            SampledRequestsEnabled: true
            CloudWatchMetricsEnabled: true
            MetricName: RateLimitMetric
      VisibilityConfig:
        SampledRequestsEnabled: true
        CloudWatchMetricsEnabled: true
        MetricName: !Sub "my-first-api-waf-${Environment}"

  MyWAFAssociation:
    Type: AWS::WAFv2::WebACLAssociation
    Properties:
      ResourceArn: !Sub
        - "arn:aws:apigateway:${AWS::Region}::/restapis/${ApiId}/stages/${Stage}"
        - ApiId: !Ref MyApi
          Stage: !Ref Environment
      WebACLArn: !GetAtt MyWAF.Arn

Outputs:
  ApiEndpoint:
    Description: API Gateway endpoint URL
    Value: !Sub "https://${MyApi}.execute-api.${AWS::Region}.amazonaws.com/${Environment}/users"

  DLQUrl:
    Description: Dead letter queue URL for failed invocations
    Value: !Ref GetUserDLQ
```

### Deploy the Final Version

```bash
sam build && sam deploy --config-env dev
sam build && sam deploy --config-env prod
```

Verify both environments are healthy:

```bash
# Dev
curl https://YOUR_DEV_ID.execute-api.us-east-1.amazonaws.com/dev/users/42

# Prod
curl https://YOUR_PROD_ID.execute-api.us-east-1.amazonaws.com/prod/users/42
```

---

## Phase 4 Summary

| Step | What You Did |
|---|---|
| Step 19 | Enabled SnapStart to cut cold starts from 5 seconds to under 200ms |
| Step 20 | Added DLQ, CloudWatch alarm, and structured error responses with consistent shape |
| Step 21 | Added throttling, CORS hardening per environment, and usage plans |
| Step 22 | Used Lambda Power Tuning to find the optimal memory setting and evaluated arm64 |
| Step 23 | Tightened IAM to least-privilege scope and added WAF with managed rule groups |
| Step 24 | Assembled the complete production-grade template.yaml |

---

## The Complete Series — What You Built

Across all four phases you went from zero to a production-grade Java REST API on AWS:

| Phase | What You Accomplished |
|---|---|
| Phase 1 | Java Lambda running locally in Docker with unit tests passing |
| Phase 2 | Deployed to real AWS with isolated dev and prod, SSM config, and observability |
| Phase 3 | Full CI/CD pipeline on GitHub Actions with OIDC auth and approval gates |
| Phase 4 | Production hardening — cold starts, error handling, security, and cost tuning |

Your final architecture handles every concern of a real production system:

```
Every request flows through:

WAF (blocks attacks and rate limits)
        ↓
API Gateway (throttles, validates, routes)
        ↓
Lambda with SnapStart (fast cold starts, structured logs, X-Ray traces)
        ↓
SSM Parameter Store (environment-specific config, no hardcoded values)
        ↓
DLQ + CloudWatch Alarm (captures and alerts on every failure)
        ↓
GitHub Actions (automated build, test, and deploy on every push)
```

Every code change you make now flows through compile, unit test, build, deploy to dev, integration test, approval gate, and deploy to prod — automatically and safely.


## Clean up

```aiignore
$ sam delete \
     --stack-name my-first-api-prod \          
     --region us-east-1 
                            
        Are you sure you want to delete the stack my-first-api-prod in the region us-east-1 ? [y/N]: y
        Are you sure you want to delete the folder my-first-api-prod in S3 which contains the artifacts? [y/N]: y
        - Deleting S3 object with key my-first-api-prod/93441011334008649429245128c9272c                                                                                             
        - Deleting S3 object with key my-first-api-prod/6606cf7221dd31f688ded22664257601.template                                                                                    
        - Deleting Cloudformation stack my-first-api-prod

Deleted successfully

$ sam delete \
    --stack-name my-first-api-dev \
    --region us-east-1
    
        Are you sure you want to delete the stack my-first-api-dev in the region us-east-1 ? [y/N]: y
        Are you sure you want to delete the folder my-first-api-dev in S3 which contains the artifacts? [y/N]: y
        - Deleting S3 object with key my-first-api-dev/93441011334008649429245128c9272c                                                                                              
        - Deleting S3 object with key my-first-api-dev/a619d9b8c759fbf9e2c3a21d2c9e2cd3.template                                                                                     
        - Deleting Cloudformation stack my-first-api-dev

Deleted successfully
```