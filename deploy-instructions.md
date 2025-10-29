# GCP Deployment Instructions for bxspace-api

## Prerequisites

1. Install [Google Cloud CLI](https://cloud.google.com/sdk/docs/install)
2. Authenticate: `gcloud auth login`
3. Set your project: `gcloud config set project YOUR_PROJECT_ID`
4. Ensure you have the necessary IAM permissions:
   - Cloud Run Admin
   - Storage Admin (for GCS bucket creation)
   - Secret Manager Admin (for secrets)
   - Service Account User

## Option 1: Cloud Run (Recommended)

### Initial Setup

```bash
# Enable required APIs
gcloud services enable cloudbuild.googleapis.com
gcloud services enable run.googleapis.com
gcloud services enable artifactregistry.googleapis.com
gcloud services enable storage.googleapis.com

# Create Artifact Registry repository
gcloud artifacts repositories create docker-repo \
  --repository-format=docker \
  --location=us-central1 \
  --description="Docker repository for bxspace-api"

# Grant Cloud Run service account permissions for GCS
# Note: Buckets will be created automatically by the application
# The Cloud Run service account needs Storage Admin role
PROJECT_NUMBER=$(gcloud projects describe $(gcloud config get-value project) --format="value(projectNumber)")
SERVICE_ACCOUNT="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"

gcloud projects add-iam-policy-binding $(gcloud config get-value project) \
  --member="serviceAccount:${SERVICE_ACCOUNT}" \
  --role="roles/storage.admin"
```

### Deploy using Cloud Build

```bash
# Deploy with default settings
gcloud builds submit --config cloudbuild.yaml

# Or customize with substitutions (including GCS configuration)
gcloud builds submit --config cloudbuild.yaml \
  --substitutions=_REGION=us-central1,_MEMORY=1Gi,_CPU=2,_MAX_INSTANCES=20,_GCS_BUCKET_PREFIX=bxspace-prod,_GCS_LOCATION=US

# Deploy to a specific region with custom GCS location
gcloud builds submit --config cloudbuild.yaml \
  --substitutions=_REGION=us-west1,_GCS_LOCATION=us-west1,_GCS_BUCKET_PREFIX=bxspace-west
```

### Manual Docker Build and Deploy

```bash
# Build and push image
gcloud builds submit --tag us-central1-docker.pkg.dev/YOUR_PROJECT_ID/docker-repo/bxspace-api

# Deploy to Cloud Run with GCS configuration
gcloud run deploy bxspace-api \
  --image us-central1-docker.pkg.dev/YOUR_PROJECT_ID/docker-repo/bxspace-api \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --memory 512Mi \
  --cpu 1 \
  --set-env-vars="SPRING_PROFILES_ACTIVE=gcp" \
  --set-env-vars="GCP_PROJECT_ID=YOUR_PROJECT_ID" \
  --set-env-vars="GCS_BUCKET_PREFIX=bxspace-prod" \
  --set-env-vars="GCS_LOCATION=US" \
  --set-env-vars="ENVIRONMENT=production"
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
  --set-env-vars="SPRING_PROFILES_ACTIVE=gcp,DATABASE_URL=your-db-url"
```

Or update `cloudbuild.yaml` to include `--set-env-vars` in the deploy step.

### Google Cloud Storage Configuration

The application automatically creates GCS buckets for namespaces. Environment variables:

- `SPRING_PROFILES_ACTIVE=gcp` - Activates GCS bucket provider
- `GCP_PROJECT_ID` - Your GCP project ID (auto-set in Cloud Build)
- `GCS_BUCKET_PREFIX` - Prefix for bucket names (default: `bxspace-prod`)
- `GCS_LOCATION` - Bucket location (default: `US`, options: `US`, `EU`, `us-central1`, etc.)
- `GCS_STORAGE_CLASS` - Storage class (default: `STANDARD`, options: `NEARLINE`, `COLDLINE`, `ARCHIVE`)

Example update:

```bash
gcloud run services update bxspace-api \
  --region=us-central1 \
  --update-env-vars="GCS_BUCKET_PREFIX=myapp-prod,GCS_LOCATION=us-west1,GCS_STORAGE_CLASS=STANDARD"
```

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

## Google Cloud Storage Setup

### Automatic Bucket Creation

The application automatically creates GCS buckets when namespaces are created. The buckets are configured with:

- **Uniform bucket-level access** for simplified IAM management
- **Public access prevention** enforced
- **Lifecycle rules** for automatic cleanup of archived objects after 1 year
- **Labels** for tracking (managed-by, environment, namespace)

### Service Account Permissions

Ensure the Cloud Run service account has the necessary permissions:

```bash
# Get your project number
PROJECT_NUMBER=$(gcloud projects describe $(gcloud config get-value project) --format="value(projectNumber)")
SERVICE_ACCOUNT="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"

# Grant Storage Admin role (for bucket creation and management)
gcloud projects add-iam-policy-binding $(gcloud config get-value project) \
  --member="serviceAccount:${SERVICE_ACCOUNT}" \
  --role="roles/storage.admin"
```

### Using a Custom Service Account (Optional)

For better security isolation, create a dedicated service account:

```bash
# Create service account
gcloud iam service-accounts create bxspace-gcs-sa \
  --display-name="BxSpace GCS Service Account"

# Grant Storage Admin role
gcloud projects add-iam-policy-binding $(gcloud config get-value project) \
  --member="serviceAccount:bxspace-gcs-sa@$(gcloud config get-value project).iam.gserviceaccount.com" \
  --role="roles/storage.admin"

# Deploy Cloud Run with custom service account
gcloud run services update bxspace-api \
  --region=us-central1 \
  --service-account="bxspace-gcs-sa@$(gcloud config get-value project).iam.gserviceaccount.com"
```

### Using Service Account Keys (Not Recommended for Cloud Run)

If deploying outside Cloud Run (e.g., on-premises), create a service account key:

```bash
# Create service account
gcloud iam service-accounts create bxspace-gcs-sa \
  --display-name="BxSpace GCS Service Account"

# Grant permissions
gcloud projects add-iam-policy-binding $(gcloud config get-value project) \
  --member="serviceAccount:bxspace-gcs-sa@$(gcloud config get-value project).iam.gserviceaccount.com" \
  --role="roles/storage.admin"

# Create key
gcloud iam service-accounts keys create gcs-credentials.json \
  --iam-account="bxspace-gcs-sa@$(gcloud config get-value project).iam.gserviceaccount.com"

# Store in Secret Manager
gcloud secrets create gcs-service-account-key \
  --data-file=gcs-credentials.json

# Update Cloud Run to mount the secret
gcloud run services update bxspace-api \
  --region=us-central1 \
  --update-secrets=/secrets/gcs-key.json=gcs-service-account-key:latest \
  --set-env-vars="GCS_CREDENTIALS_PATH=/secrets/gcs-key.json"

# Clean up local key file
rm gcs-credentials.json
```

### Testing GCS Integration

After deployment, verify GCS is working:

1. Create a namespace via the API
2. Check that a bucket was created:

```bash
# List buckets with prefix
gsutil ls | grep bxspace-prod

# Check bucket details
gsutil ls -L gs://bxspace-prod-your-namespace
```

3. View application logs for GCS operations:

```bash
gcloud run logs read bxspace-api \
  --region=us-central1 \
  --filter="textPayload:GoogleCloudStorageBucketProvider"
```

### GCS Configuration Reference

| Environment Variable | Description | Default | Example |
|---------------------|-------------|---------|---------|
| `GCP_PROJECT_ID` | GCP Project ID | (required) | `my-project-123` |
| `GCS_BUCKET_PREFIX` | Prefix for bucket names | `bxspace-prod` | `myapp-prod` |
| `GCS_LOCATION` | Bucket location | `US` | `us-central1`, `EU` |
| `GCS_STORAGE_CLASS` | Storage class | `STANDARD` | `NEARLINE`, `COLDLINE` |
| `GCS_CREDENTIALS_PATH` | Path to key JSON | (use ADC) | `/secrets/gcs-key.json` |

### Cost Optimization

For cost optimization, consider:

1. **Regional buckets** for lower latency and costs: `GCS_LOCATION=us-central1`
2. **Lifecycle rules** (already configured):
   - Coldline/Archive objects deleted after 365 days
3. **Storage class selection**:
   - `STANDARD` - Frequently accessed data
   - `NEARLINE` - Monthly access (lower cost)
   - `COLDLINE` - Quarterly access (even lower cost)
   - `ARCHIVE` - Yearly access (lowest cost)

## Troubleshooting

- Check build logs: `gcloud builds list`
- Check service status: `gcloud run services describe bxspace-api --region=us-central1`
- View detailed logs: `gcloud run logs read bxspace-api --region=us-central1 --limit=50`
- **GCS Issues**: Check logs with filter `--filter="textPayload:GCS"` or `--filter="textPayload:GoogleCloudStorageBucketProvider"`
- **Bucket creation fails**: Verify service account has `storage.admin` role
- **ADC not working**: Ensure Cloud Run service account has proper IAM roles
