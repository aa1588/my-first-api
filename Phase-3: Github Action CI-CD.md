# Building a Java REST API with AWS SAM — Phase 3: GitHub Actions CI/CD

This is Phase 3 of the AWS SAM Java series. By the end of Phase 2 you had a working Java REST API deployed to isolated dev and prod environments on real AWS. Every deployment was still a manual `sam deploy` command. In this phase you automate the entire process with GitHub Actions so every code push triggers a full build, test, and deploy pipeline.

By the end of this phase you will never run `sam deploy` manually again.

---

## What You Are Building

```
Developer pushes code
        ↓
GitHub Actions triggers
        ↓
Maven compiles and runs unit tests
        ↓
sam build packages the fat jar
        ↓
sam deploy → dev stack
        ↓
Integration tests hit the live dev API
        ↓
Manual approval gate
        ↓
sam deploy → prod stack
        ↓
Live on API Gateway
```

Three workflows power this:

```
ci.yml          →  runs on every pull request
deploy-dev.yml  →  runs on every merge to main
deploy-prod.yml →  runs after dev passes, waits for approval
```

---

## Step 13 — OIDC Authentication (No AWS Keys in GitHub)

This is the most important step in Phase 3. The wrong way to give GitHub Actions access to AWS is to create an IAM user, generate access keys, and paste them into GitHub Secrets. Those keys are long-lived credentials that never expire and can leak.

The right way is OIDC. GitHub Actions proves its identity to AWS using a short-lived signed token. AWS verifies it and issues temporary credentials that expire in 15 minutes. No keys stored anywhere.

### How OIDC Works

```
GitHub Actions Runner
        │
        │  "I am workflow X on repo Y — here is my signed JWT"
        ↓
AWS STS (Security Token Service)
        │
        │  Verifies JWT against GitHub's OIDC provider
        │  Checks the trust policy on the IAM role
        ↓
Issues temporary credentials (15 min lifetime)
        │
        ↓
GitHub Actions uses them for sam deploy
```

### Set Up the OIDC Provider in AWS

Run this once. It tells AWS to trust GitHub's identity tokens:

```bash
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

### Create the IAM Role for GitHub Actions

Create a file called `github-actions-trust-policy.json` on your Desktop.

Replace `YOUR_ACCOUNT_ID` and `YOUR_GITHUB_USERNAME` before running:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::YOUR_ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:YOUR_GITHUB_USERNAME/my-first-api:*"
        }
      }
    }
  ]
}
```

The `Condition` block is critical. It ensures only your specific repo can assume this role. Without it any GitHub Actions workflow could request credentials.

Get your account ID:

```bash
aws sts get-caller-identity --query Account --output text
```

Create the role:

```bash
aws iam create-role \
  --role-name github-actions-sam-role \
  --assume-role-policy-document file://~/Desktop/github-actions-trust-policy.json
```

Attach the permissions this role needs to deploy SAM applications:

```bash
aws iam attach-role-policy \
  --role-name github-actions-sam-role \
  --policy-arn arn:aws:iam::aws:policy/AWSCloudFormationFullAccess

aws iam attach-role-policy \
  --role-name github-actions-sam-role \
  --policy-arn arn:aws:iam::aws:policy/AWSLambda_FullAccess

aws iam attach-role-policy \
  --role-name github-actions-sam-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonAPIGatewayAdministrator

aws iam attach-role-policy \
  --role-name github-actions-sam-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonS3FullAccess

aws iam attach-role-policy \
  --role-name github-actions-sam-role \
  --policy-arn arn:aws:iam::aws:policy/IAMFullAccess

aws iam attach-role-policy \
  --role-name github-actions-sam-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMReadOnlyAccess
```

Get the role ARN — you will need it in every workflow file:

```bash
aws iam get-role \
  --role-name github-actions-sam-role \
  --query 'Role.Arn' \
  --output text
```

Save the output. It looks like:

```
arn:aws:iam::123456789012:role/github-actions-sam-role
```

---

## Step 14 — Push Your Project to GitHub

Before writing workflows you need your project in a GitHub repository.

```bash
cd ~/Desktop/my-first-api

# Initialise git
git init
git add .
git commit -m "Initial commit: Phase 1 and 2 complete"
```

Create a new repository on GitHub at [github.com/new](https://github.com/new). Name it `my-first-api`, keep it private, and do not add a README or .gitignore — you will add those yourself.

Then push:

```bash
git remote add origin https://github.com/YOUR_USERNAME/my-first-api.git
git branch -M main
git push -u origin main
```

---

## Step 15 — Add .gitignore

Make sure you are not committing build artifacts or credentials:

```bash
cat > ~/Desktop/my-first-api/.gitignore << 'EOF'
# SAM build output
.aws-sam/

# Maven build output
HelloWorldFunction/target/

# AWS credentials — never commit these
.aws/credentials

# IntelliJ
.idea/
*.iml

# Mac
.DS_Store
EOF
```

---

## Step 16 — Create the Three Workflows

Create the GitHub Actions directory:

```bash
mkdir -p ~/Desktop/my-first-api/.github/workflows
```

### Workflow 1 — CI on Every Pull Request

This runs on every PR. It compiles, tests, and builds but does not deploy anything. Its job is to be a fast feedback loop — fail early before anything reaches AWS.

Create `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  pull_request:
    branches: [main]

jobs:
  build-and-test:
    name: Build and Test
    runs-on: ubuntu-latest

    permissions:
      id-token: write   # required for OIDC token generation
      contents: read

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'corretto'

      - name: Set up SAM CLI
        uses: aws-actions/setup-sam@v2

      - name: Configure AWS credentials via OIDC
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::YOUR_ACCOUNT_ID:role/github-actions-sam-role
          aws-region: us-east-1

      - name: Run unit tests
        run: |
          cd HelloWorldFunction
          mvn test

      - name: SAM build
        run: sam build

      - name: Validate SAM template
        run: sam validate
```

### Workflow 2 — Deploy to Dev on Merge to Main

This runs when a PR merges into main. It deploys to the dev stack then runs integration tests against the live dev endpoint. If integration tests fail the prod workflow never triggers.

Create `.github/workflows/deploy-dev.yml`:

```yaml
name: Deploy to Dev

on:
  push:
    branches: [main]

jobs:
  deploy-dev:
    name: Deploy to Dev
    runs-on: ubuntu-latest

    permissions:
      id-token: write
      contents: read

    outputs:
      api-endpoint: ${{ steps.get-endpoint.outputs.endpoint }}

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'corretto'

      - name: Set up SAM CLI
        uses: aws-actions/setup-sam@v2

      - name: Configure AWS credentials via OIDC
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::YOUR_ACCOUNT_ID:role/github-actions-sam-role
          aws-region: us-east-1

      - name: Run unit tests
        run: |
          cd HelloWorldFunction
          mvn test

      - name: SAM build
        run: sam build

      - name: Deploy to dev
        run: sam deploy --config-env dev --no-confirm-changeset --no-fail-on-empty-changeset

      - name: Get API endpoint
        id: get-endpoint
        run: |
          ENDPOINT=$(aws cloudformation describe-stacks \
            --stack-name my-first-api-dev \
            --query 'Stacks[0].Outputs[?OutputKey==`ApiEndpoint`].OutputValue' \
            --output text)
          echo "endpoint=$ENDPOINT" >> $GITHUB_OUTPUT
          echo "Dev API endpoint: $ENDPOINT"

      - name: Run integration tests
        run: |
          ENDPOINT="${{ steps.get-endpoint.outputs.endpoint }}"
          echo "Testing endpoint: $ENDPOINT"

          # Test 1 — basic request returns 200
          STATUS=$(curl -s -o /dev/null -w "%{http_code}" $ENDPOINT/42)
          if [ "$STATUS" != "200" ]; then
            echo "FAIL: Expected 200 but got $STATUS"
            exit 1
          fi
          echo "PASS: GET /users/42 returned 200"

          # Test 2 — response contains userId field
          BODY=$(curl -s $ENDPOINT/42)
          if ! echo $BODY | grep -q "userId"; then
            echo "FAIL: Response missing userId field"
            echo "Body: $BODY"
            exit 1
          fi
          echo "PASS: Response contains userId"

          # Test 3 — response contains the correct value
          if ! echo $BODY | grep -q "42"; then
            echo "FAIL: Response missing expected userId value"
            exit 1
          fi
          echo "PASS: Response contains correct userId value"

          echo "All integration tests passed"
```

### Workflow 3 — Deploy to Prod with Approval Gate

This triggers after the dev workflow succeeds. It pauses at the `environment: production` block and waits for a human to click approve in GitHub before touching prod.

Create `.github/workflows/deploy-prod.yml`:

```yaml
name: Deploy to Prod

on:
  workflow_run:
    workflows: ["Deploy to Dev"]
    types: [completed]
    branches: [main]

jobs:
  deploy-prod:
    name: Deploy to Prod
    runs-on: ubuntu-latest
    if: ${{ github.event.workflow_run.conclusion == 'success' }}

    environment:
      name: production        # triggers the approval gate configured in GitHub Settings

    permissions:
      id-token: write
      contents: read

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'corretto'

      - name: Set up SAM CLI
        uses: aws-actions/setup-sam@v2

      - name: Configure AWS credentials via OIDC
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::YOUR_ACCOUNT_ID:role/github-actions-sam-role
          aws-region: us-east-1

      - name: SAM build
        run: sam build

      - name: Deploy to prod
        run: sam deploy --config-env prod --no-confirm-changeset --no-fail-on-empty-changeset

      - name: Get prod endpoint
        id: get-endpoint
        run: |
          ENDPOINT=$(aws cloudformation describe-stacks \
            --stack-name my-first-api-prod \
            --query 'Stacks[0].Outputs[?OutputKey==`ApiEndpoint`].OutputValue' \
            --output text)
          echo "Prod API endpoint: $ENDPOINT"

      - name: Smoke test prod
        run: |
          ENDPOINT=$(aws cloudformation describe-stacks \
            --stack-name my-first-api-prod \
            --query 'Stacks[0].Outputs[?OutputKey==`ApiEndpoint`].OutputValue' \
            --output text)

          STATUS=$(curl -s -o /dev/null -w "%{http_code}" $ENDPOINT/1)
          if [ "$STATUS" != "200" ]; then
            echo "FAIL: Prod smoke test failed with status $STATUS"
            exit 1
          fi
          echo "PASS: Prod smoke test passed"
```

---

## Step 17 — Set Up the Production Approval Gate

The `environment: production` block in the prod workflow pauses the deploy and sends you an approval request. No one can deploy to prod without a human reviewing and clicking approve.

Set it up in GitHub:

1. Go to your repository on GitHub
2. Click **Settings** → **Environments** → **New environment**
3. Name it `production` — this must match exactly what is in `deploy-prod.yml`
4. Enable **Required reviewers** and add yourself
5. Click **Save protection rules**

Every prod deploy will now pause, send you an email, and wait for your approval before proceeding.

---

## Step 18 — Replace YOUR_ACCOUNT_ID in All Workflow Files

Rather than editing each file manually, use this to replace the placeholder in all three files at once:

```bash
cd ~/Desktop/my-first-api

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

sed -i '' "s/YOUR_ACCOUNT_ID/$ACCOUNT_ID/g" \
  .github/workflows/ci.yml \
  .github/workflows/deploy-dev.yml \
  .github/workflows/deploy-prod.yml

echo "Replaced account ID with: $ACCOUNT_ID"
```

Verify it worked:

```bash
grep "role-to-assume" .github/workflows/ci.yml
# Should show your real account ID, not YOUR_ACCOUNT_ID
```

---

## Step 19 — Push and Watch the Pipeline Run

Commit everything and push:

```bash
cd ~/Desktop/my-first-api
git add .
git commit -m "Add GitHub Actions CI/CD pipelines with OIDC auth"
git push origin main
```

Go to your GitHub repo and click the **Actions** tab. You will see the `Deploy to Dev` workflow running. Click into it to watch each step execute in real time:

```
✓ Checkout code
✓ Set up Java 21
✓ Set up SAM CLI
✓ Configure AWS credentials via OIDC
✓ Run unit tests
✓ SAM build
✓ Deploy to dev
✓ Get API endpoint
✓ Run integration tests
```

When dev succeeds, the `Deploy to Prod` workflow appears as **waiting for approval**. Click **Review deployments**, select `production`, and click **Approve and deploy**. Prod deploys automatically.

---

## Step 20 — Test the Full Pipeline End to End

This is the moment that ties everything together. Create a feature branch, make a change, and raise a PR:

```bash
git checkout -b feature/add-greeting
```

Open `App.java` and update the message:

```java
Map<String, Object> body = Map.of(
    "message", "Hello from Lambda — deployed via CI/CD",
    "userId",  userId,
    "env",     env,
    "table",   tableName
);
```

Push and raise a pull request:

```bash
git add .
git commit -m "Update greeting message"
git push origin feature/add-greeting
```

Go to GitHub and open a pull request from `feature/add-greeting` into `main`. Watch what happens automatically:

```
1. CI workflow triggers on the PR
   → unit tests run
   → sam build runs
   → sam validate runs
   → PR shows green checkmark

2. Merge the PR

3. Deploy to Dev triggers on merge
   → unit tests run again
   → sam build runs
   → sam deploy pushes to dev stack
   → integration tests hit the live dev API
   → all three tests pass

4. Deploy to Prod triggers after dev passes
   → pauses for approval
   → you receive an email notification
   → you click Approve
   → sam deploy pushes to prod stack
   → smoke test confirms prod is healthy
```

Your updated message is now live on both environments without you running a single command.

---

## The Complete Branch Strategy

```
Branch              Triggers                    Deploys To
────────────────────────────────────────────────────────────
feature/*           CI on PR open/update        Nothing
main (on merge)     Deploy to Dev               dev stack
main (after dev)    Deploy to Prod (+ approval) prod stack
```

This means:

- Developers work on feature branches and open PRs
- Every PR gets fast feedback from the CI workflow
- Merging to main is the only gate to dev
- Prod is always protected by both integration tests and a human approval

---

## Common Errors and Fixes

**`Error: Credentials could not be loaded`**
The OIDC provider is not set up or the trust policy has the wrong repo name. Verify the `sub` condition in your trust policy matches `repo:YOUR_USERNAME/my-first-api:*` exactly.

**`sam deploy` fails with `No changes to deploy`**
Add `--no-fail-on-empty-changeset` to the deploy command. This tells SAM to exit successfully even when there is nothing new to deploy, which happens when you push a non-infrastructure change.

**Prod workflow does not trigger after dev succeeds**
The `workflow_run` trigger only fires on the default branch. Make sure your default branch in GitHub is set to `main` and not `master`.

**Approval gate does not appear**
The `environment` name in `deploy-prod.yml` must match the environment name you created in GitHub Settings exactly. Both must be `production`.

**`sam validate` fails in CI**
SAM validate needs AWS credentials to resolve `{{resolve:ssm:...}}` references. Make sure the OIDC configure step runs before the validate step.

---

## Phase 3 Summary

Here is everything you built in this phase:

| Step | What You Did |
|---|---|
| Step 13 | Set up OIDC — GitHub Actions assumes an IAM role with no stored keys |
| Step 14 | Pushed the project to GitHub |
| Step 15 | Added .gitignore to keep build artifacts and credentials out of git |
| Step 16 | Created three workflow files — CI, deploy-dev, deploy-prod |
| Step 17 | Configured the production approval gate in GitHub Settings |
| Step 18 | Replaced account ID placeholders across all workflow files |
| Step 19 | Pushed and watched the first automated pipeline run |
| Step 20 | Tested the full end-to-end flow with a feature branch and PR |

Your pipeline now enforces this flow for every single code change:

```
Write code → Open PR → CI passes → Merge → Dev deploys
→ Integration tests pass → Approve → Prod deploys
```

---

## Next Steps — Phase 4

In Phase 4 you move from working to production-grade. The topics are:

**Cold Start Tuning** — Java Lambda cold starts can take 2 to 5 seconds. SnapStart reduces this to under 200ms for most cases. Provisioned Concurrency eliminates it entirely for critical paths.

**Error Handling** — Dead letter queues, Lambda destinations, and retry configuration so failed invocations never silently disappear.

**API Best Practices** — Request validation at the API Gateway level, CORS hardening, throttling, and usage plans so your API is safe to expose publicly.

**Cost Optimisation** — Right-sizing memory, switching to arm64 architecture, and using the Lambda Power Tuning tool to find the optimal memory setting for your specific workload.

**Security** — WAF rules on API Gateway, VPC Lambda configuration, and tightening IAM roles from the broad policies used in Phase 3 to true least-privilege.