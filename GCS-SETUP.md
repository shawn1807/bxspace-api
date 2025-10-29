# Google Cloud Storage Setup Guide

This document provides a comprehensive guide for setting up and using Google Cloud Storage (GCS) with the bxspace-api application.

## Overview

The bxspace-api uses Google Cloud Storage to store files for each namespace. When a namespace is created, a dedicated GCS bucket is automatically provisioned with proper security configurations.

## Architecture

```
┌─────────────────────┐
│   bxspace-api       │
│   (Cloud Run)       │
└──────────┬──────────┘
           │
           │ Uses Application Default Credentials (ADC)
           │ or Service Account Key
           │
           ▼
┌─────────────────────────────────────────┐
│  Google Cloud Storage                   │
│                                         │
│  ├── bxspace-prod-namespace1            │
│  ├── bxspace-prod-namespace2            │
│  └── bxspace-prod-namespace3            │
└─────────────────────────────────────────┘
```

## Features

### Implemented Features

1. **Automatic Bucket Creation**: Buckets are created automatically when namespaces are created
2. **Security**:
   - Uniform bucket-level access (recommended by Google)
   - Public access prevention enforced
   - IAM-based access control
3. **Lifecycle Management**: Automatic cleanup of archived objects after 1 year
4. **Metadata & Labels**: Buckets tagged with environment, namespace, and management info
5. **Flexible Configuration**: Support for different regions, storage classes, and prefixes
6. **Signed URLs**: Generate temporary upload/download URLs for secure file access
7. **Fallback Support**: Falls back to FileSystemBucketProvider if GCS configuration fails

### Bucket Configuration

Each bucket created includes:

- **Location**: Configurable (default: `US`)
- **Storage Class**: Configurable (default: `STANDARD`)
- **Uniform Bucket-Level Access**: Enabled
- **Public Access Prevention**: Enforced
- **Labels**:
  - `managed-by: bxspace-api`
  - `environment: production` (or from `ENVIRONMENT` env var)
  - `namespace: <namespace-name>`
- **Lifecycle Rule**: Delete archived objects after 365 days

## Configuration

### Environment Variables

| Variable | Description | Required | Default |
|----------|-------------|----------|---------|
| `SPRING_PROFILES_ACTIVE` | Must include `gcp` or `prod` | Yes | - |
| `GCP_PROJECT_ID` | Your GCP project ID | Yes | - |
| `GCS_BUCKET_PREFIX` | Prefix for all bucket names | No | `bxspace` |
| `GCS_LOCATION` | Bucket location/region | No | `US` |
| `GCS_STORAGE_CLASS` | Storage class | No | `STANDARD` |
| `GCS_CREDENTIALS_PATH` | Path to service account key JSON | No | (uses ADC) |
| `ENVIRONMENT` | Environment name for labels | No | `production` |

### Storage Class Options

- `STANDARD` - Best for frequently accessed data
- `NEARLINE` - Best for data accessed < once per month
- `COLDLINE` - Best for data accessed < once per quarter
- `ARCHIVE` - Best for data accessed < once per year

### Location Options

**Multi-Regional**:
- `US` - United States
- `EU` - European Union
- `ASIA` - Asia

**Regional** (examples):
- `us-central1` - Iowa
- `us-west1` - Oregon
- `us-east1` - South Carolina
- `europe-west1` - Belgium
- `asia-southeast1` - Singapore

See [GCS Locations](https://cloud.google.com/storage/docs/locations) for full list.

## Setup Instructions

### 1. Enable Required APIs

```bash
gcloud services enable storage.googleapis.com
gcloud services enable run.googleapis.com
```

### 2. Set Up Service Account Permissions

#### Option A: Use Default Compute Service Account (Easiest)

```bash
# Get project info
PROJECT_ID=$(gcloud config get-value project)
PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format="value(projectNumber)")
SERVICE_ACCOUNT="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"

# Grant Storage Admin role
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:${SERVICE_ACCOUNT}" \
  --role="roles/storage.admin"
```

#### Option B: Create Dedicated Service Account (Recommended for Production)

```bash
PROJECT_ID=$(gcloud config get-value project)

# Create service account
gcloud iam service-accounts create bxspace-gcs-sa \
  --display-name="BxSpace GCS Service Account" \
  --description="Service account for bxspace-api GCS operations"

# Grant Storage Admin role
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:bxspace-gcs-sa@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/storage.admin"

# Update Cloud Run to use custom service account
gcloud run services update bxspace-api \
  --region=us-central1 \
  --service-account="bxspace-gcs-sa@${PROJECT_ID}.iam.gserviceaccount.com"
```

### 3. Deploy Application with GCS Configuration

#### Via Cloud Build (Recommended)

```bash
gcloud builds submit --config cloudbuild.yaml \
  --substitutions=_GCS_BUCKET_PREFIX=bxspace-prod,_GCS_LOCATION=US
```

#### Manual Deployment

```bash
PROJECT_ID=$(gcloud config get-value project)

gcloud run deploy bxspace-api \
  --image us-central1-docker.pkg.dev/$PROJECT_ID/docker-repo/bxspace-api:latest \
  --region us-central1 \
  --platform managed \
  --set-env-vars="SPRING_PROFILES_ACTIVE=gcp" \
  --set-env-vars="GCP_PROJECT_ID=$PROJECT_ID" \
  --set-env-vars="GCS_BUCKET_PREFIX=bxspace-prod" \
  --set-env-vars="GCS_LOCATION=US" \
  --set-env-vars="GCS_STORAGE_CLASS=STANDARD" \
  --set-env-vars="ENVIRONMENT=production"
```

## Usage Examples

### Bucket Naming Convention

Buckets are named using the pattern: `{prefix}-{namespace-context-path}`

For example:
- Prefix: `bxspace-prod`
- Namespace: `acme-corp`
- Bucket name: `bxspace-prod-acme-corp`

Special characters in namespace names are sanitized to meet GCS naming requirements:
- Converted to lowercase
- Non-alphanumeric characters replaced with hyphens
- Multiple hyphens collapsed to single hyphen
- Leading/trailing hyphens removed

### Using the GCS Provider in Code

The `GoogleCloudStorageBucketProvider` implements the `AppBucketProvider` interface and is automatically injected when the `gcp` or `prod` profile is active.

**Creating a Bucket** (done automatically by NamespaceService):

```java
@Autowired
private AppBucketProvider bucketProvider;

EntryBucket bucket = bucketProvider.createBucket("my-namespace", AclMode.FULL);
```

**Additional Operations** (if you cast to GoogleCloudStorageBucketProvider):

```java
GoogleCloudStorageBucketProvider gcsProvider = (GoogleCloudStorageBucketProvider) bucketProvider;

// Check if bucket exists
boolean exists = gcsProvider.bucketExists("my-namespace");

// Generate signed upload URL (valid for 60 minutes)
String uploadUrl = gcsProvider.generateUploadUrl("my-namespace", "file.pdf", 60);

// Generate signed download URL (valid for 60 minutes)
String downloadUrl = gcsProvider.generateDownloadUrl("my-namespace", "file.pdf", 60);

// Get bucket details
Bucket bucket = gcsProvider.getBucket("my-namespace");

// Delete bucket (must be empty)
boolean deleted = gcsProvider.deleteBucket("my-namespace");

// Access underlying Storage client for advanced operations
Storage storage = gcsProvider.getStorage();
```

## Testing

### Verify GCS Integration

After deployment, test the integration:

**1. Create a namespace via the API:**

```bash
curl -X POST https://your-api-url/api/namespaces \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Namespace",
    "contextPath": "test-ns",
    "description": "Testing GCS integration"
  }'
```

**2. Verify bucket was created:**

```bash
gsutil ls | grep bxspace-prod-test-ns
```

**3. Check bucket details:**

```bash
gsutil ls -L gs://bxspace-prod-test-ns
```

**4. View application logs:**

```bash
gcloud run logs read bxspace-api \
  --region=us-central1 \
  --filter="textPayload:GoogleCloudStorageBucketProvider"
```

### Local Development Testing

For local testing with GCS:

**1. Install gcloud CLI and authenticate:**

```bash
gcloud auth application-default login
```

**2. Set environment variables:**

```bash
export SPRING_PROFILES_ACTIVE=gcp
export GCP_PROJECT_ID=your-project-id
export GCS_BUCKET_PREFIX=bxspace-dev
export GCS_LOCATION=us-central1
```

**3. Run the application:**

```bash
./mvnw spring-boot:run
```

**4. Or use service account key (not recommended for production):**

```bash
export GCS_CREDENTIALS_PATH=/path/to/service-account-key.json
./mvnw spring-boot:run
```

## Monitoring & Operations

### View Bucket List

```bash
# List all buckets
gsutil ls

# List buckets with prefix
gsutil ls | grep bxspace-prod

# List with details
gsutil ls -L gs://bxspace-prod-*
```

### Monitor Bucket Usage

```bash
# Get bucket size
gsutil du -sh gs://bxspace-prod-namespace

# List objects in bucket
gsutil ls -r gs://bxspace-prod-namespace
```

### Check Application Logs

```bash
# All logs
gcloud run logs read bxspace-api --region=us-central1

# GCS-specific logs
gcloud run logs read bxspace-api \
  --region=us-central1 \
  --filter="textPayload:GCS OR textPayload:GoogleCloudStorageBucketProvider"

# Errors only
gcloud run logs read bxspace-api \
  --region=us-central1 \
  --filter="severity>=ERROR"
```

### View Metrics in Cloud Console

Navigate to: Cloud Console → Storage → Browser

Or use Monitoring:

```bash
# Create custom dashboard for GCS metrics
gcloud monitoring dashboards create --config-from-file=dashboard.json
```

## Cost Optimization

### Estimated Costs

**Storage Costs** (as of 2024, US multi-regional):
- Standard Storage: $0.020/GB/month
- Nearline Storage: $0.010/GB/month
- Coldline Storage: $0.004/GB/month
- Archive Storage: $0.0012/GB/month

**Operation Costs**:
- Class A Operations (write, list): $0.005/1,000 ops
- Class B Operations (read, get): $0.0004/1,000 ops

### Cost Optimization Strategies

**1. Use Regional Buckets** (if applicable):

```bash
# Lower cost than multi-regional
export GCS_LOCATION=us-central1
```

**2. Choose Appropriate Storage Class**:

For infrequently accessed data:

```bash
export GCS_STORAGE_CLASS=NEARLINE  # or COLDLINE, ARCHIVE
```

**3. Monitor and Clean Up**:

```bash
# Find old buckets
gsutil ls -L gs://bxspace-prod-* | grep "Time created"

# Delete unused buckets
gsutil rm -r gs://bxspace-prod-old-namespace
```

**4. Set Up Budget Alerts**:

```bash
gcloud billing budgets create \
  --billing-account=BILLING_ACCOUNT_ID \
  --display-name="GCS Storage Budget" \
  --budget-amount=100.00 \
  --threshold-rule=percent=80 \
  --threshold-rule=percent=100
```

## Troubleshooting

### Common Issues

**Issue: Bucket creation fails with permission denied**

Solution:
```bash
# Verify service account has storage.admin role
gcloud projects get-iam-policy $(gcloud config get-value project) \
  --flatten="bindings[].members" \
  --format="table(bindings.role, bindings.members)"

# Grant permission if missing
gcloud projects add-iam-policy-binding $(gcloud config get-value project) \
  --member="serviceAccount:YOUR_SERVICE_ACCOUNT" \
  --role="roles/storage.admin"
```

**Issue: Application using FileSystemBucketProvider instead of GCS**

Check:
1. Profile is set: `SPRING_PROFILES_ACTIVE=gcp`
2. GCS is enabled: `gcs.enabled=true` in properties
3. Check logs for initialization errors

**Issue: Bucket names are invalid**

- Bucket names must be globally unique
- Must be 3-63 characters
- Only lowercase letters, numbers, hyphens
- Cannot start/end with hyphen

Solution: Change `GCS_BUCKET_PREFIX` to something unique

**Issue: Application Default Credentials not working**

Solution:
```bash
# Re-authenticate
gcloud auth application-default login

# Or use service account key
export GCS_CREDENTIALS_PATH=/path/to/key.json
```

### Debug Mode

Enable debug logging for GCS operations:

Add to `application-gcp.properties`:

```properties
logging.level.com.tsu.api.service.impl.GoogleCloudStorageBucketProvider=DEBUG
logging.level.com.google.cloud.storage=DEBUG
```

Or set environment variable:

```bash
gcloud run services update bxspace-api \
  --region=us-central1 \
  --update-env-vars="LOGGING_LEVEL_COM_TSU_API_SERVICE_IMPL_GOOGLECLOUDSTORAGEBUCKETPROVIDER=DEBUG"
```

## Security Best Practices

1. **Use Application Default Credentials** in Cloud Run (no keys needed)
2. **Create dedicated service accounts** with minimal permissions
3. **Enable uniform bucket-level access** (already configured)
4. **Enforce public access prevention** (already configured)
5. **Use signed URLs** for temporary file access instead of making buckets public
6. **Rotate service account keys** regularly (if using keys)
7. **Audit access logs** using Cloud Audit Logs
8. **Use VPC Service Controls** for additional network-level security

## Additional Resources

- [Google Cloud Storage Documentation](https://cloud.google.com/storage/docs)
- [GCS Best Practices](https://cloud.google.com/storage/docs/best-practices)
- [GCS Pricing](https://cloud.google.com/storage/pricing)
- [Cloud Run Documentation](https://cloud.google.com/run/docs)
- [Application Default Credentials](https://cloud.google.com/docs/authentication/application-default-credentials)

## Support

For issues or questions:
1. Check application logs: `gcloud run logs read bxspace-api`
2. Review GCS audit logs in Cloud Console
3. Check Cloud Run service health
4. Verify IAM permissions
5. Review this documentation

## Changelog

### Version 1.0 (2025-01-15)
- Initial GCS integration
- Automatic bucket creation for namespaces
- Support for signed URLs
- Configurable regions and storage classes
- Lifecycle management
- Comprehensive security configurations
