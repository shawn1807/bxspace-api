# GCP Deployment Instructions for bxspace-api

## Prerequisites

1. Install [Google Cloud CLI](https://cloud.google.com/sdk/docs/install)
2. Authenticate: `gcloud auth login`
3. Set your project: `gcloud config set project YOUR_PROJECT_ID`

## Option 1: Cloud Run (Recommended)

### Initial Setup

```bash
# Enable required APIs
gcloud services enable cloudbuild.googleapis.com
gcloud services enable run.googleapis.com
gcloud services enable artifactregistry.googleapis.com

# Create Artifact Registry repository
gcloud artifacts repositories create docker-repo \
  --repository-format=docker \
  --location=us-central1 \
  --description="Docker repository for bxspace-api"
```

### Deploy using Cloud Build

```bash
# Deploy with default settings
gcloud builds submit --config cloudbuild.yaml

# Or customize with substitutions
gcloud builds submit --config cloudbuild.yaml \
  --substitutions=_REGION=us-central1,_MEMORY=1Gi,_CPU=2,_MAX_INSTANCES=20
```

### Manual Docker Build and Deploy

```bash
# Build and push image
gcloud builds submit --tag us-central1-docker.pkg.dev/YOUR_PROJECT_ID/docker-repo/bxspace-api

# Deploy to Cloud Run
gcloud run deploy bxspace-api \
  --image us-central1-docker.pkg.dev/YOUR_PROJECT_ID/docker-repo/bxspace-api \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --memory 512Mi
```

## Option 2: App Engine

```bash
# Enable App Engine API
gcloud services enable appengine.googleapis.com

# Create App Engine application (one-time)
gcloud app create --region=us-central

# Deploy
gcloud app deploy

# View application
gcloud app browse
```

## Configuration

### Environment Variables

Add environment variables to Cloud Run:

```bash
gcloud run services update bxspace-api \
  --region=us-central1 \
  --set-env-vars="SPRING_PROFILES_ACTIVE=prod,DATABASE_URL=your-db-url"
```

Or update `cloudbuild.yaml` to include `--set-env-vars` in the deploy step.

### Secrets

Use Secret Manager for sensitive data:

```bash
# Create secret
echo -n "secret-value" | gcloud secrets create my-secret --data-file=-

# Grant access to Cloud Run
gcloud secrets add-iam-policy-binding my-secret \
  --member=serviceAccount:YOUR_PROJECT_NUMBER-compute@developer.gserviceaccount.com \
  --role=roles/secretmanager.secretAccessor

# Update Cloud Run to use secret
gcloud run services update bxspace-api \
  --region=us-central1 \
  --update-secrets=MY_SECRET=my-secret:latest
```

## Monitoring

```bash
# View logs
gcloud run logs read bxspace-api --region=us-central1

# Follow logs
gcloud run logs tail bxspace-api --region=us-central1
```

## CI/CD with Cloud Build Triggers

Set up automatic deployment on git push:

```bash
gcloud builds triggers create github \
  --repo-name=bxspace-api \
  --repo-owner=YOUR_GITHUB_USERNAME \
  --branch-pattern="^main$" \
  --build-config=cloudbuild.yaml
```

## Troubleshooting

- Check build logs: `gcloud builds list`
- Check service status: `gcloud run services describe bxspace-api --region=us-central1`
- View detailed logs: `gcloud run logs read bxspace-api --region=us-central1 --limit=50`
