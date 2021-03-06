import os
import re
import json
from google.cloud import bigquery
from google.cloud import scheduler
import datetime

"""
{
	'bucket': 'azurecustomerbillingdata-qa',
	'contentType': 'text/csv',
	'crc32c': 'txz7fA==',
	'etag': 'CKro0cDewO4CEAE=',
	'generation': '1611909414810666',
	'id': 'azurecustomerbillingdata-qa/kmpySmUISimoRrJL6NL73w/JUKVZIGKQzCVKXYbDhmM_g/20210101-20210131/cereportnikunj_0e8fa366-608d-4aa8-8fb7-dc8fef9fe717.csv/1611909414810666',
	'kind': 'storage#object',
	'md5Hash': '/wftWBBbhax7CKIlPjL/DA==',
	'mediaLink': 'https://www.googleapis.com/download/storage/v1/b/azurecustomerbillingdata-qa/o/kmpySmUISimoRrJL6NL73w%2FJUKVZIGKQzCVKXYbDhmM_g%2F20210101-20210131%2Fcereportnikunj_0e8fa366-608d-4aa8-8fb7-dc8fef9fe717.csv?generation=1611909414810666&alt=media',
	'metageneration': '1',
	'name': 'kmpySmUISimoRrJL6NL73w/JUKVZIGKQzCVKXYbDhmM_g/20210101-20210131/cereportnikunj_0e8fa366-608d-4aa8-8fb7-dc8fef9fe717.csv',
	'selfLink': 'https://www.googleapis.com/storage/v1/b/azurecustomerbillingdata-qa/o/kmpySmUISimoRrJL6NL73w%2FJUKVZIGKQzCVKXYbDhmM_g%2F20210101-20210131%2Fcereportnikunj_0e8fa366-608d-4aa8-8fb7-dc8fef9fe717.csv',
	'size': '3085577',
	'storageClass': 'STANDARD',
	'timeCreated': '2021-01-29T08:36:54.878Z',
	'timeStorageClassUpdated': '2021-01-29T08:36:54.878Z',
	'updated': '2021-01-29T08:36:54.878Z'
}
"""


def main(event, context):
    """Triggered by a change to a Cloud Storage bucket.
    Args:
         event (dict): Event payload.
         context (google.cloud.functions.Context): Metadata for the event.
    """
    jsonData = event
    print(f"Processing event:\n {jsonData}")
    file = jsonData['name']
    print(f"Processing file: {jsonData['name']}.")
    if not file.split("/")[-1].endswith(".csv") or file.split("/")[-1] == "DefaultRule-AllBlobs.csv":
        print("Not processing as file type is not csv")
        return

    projectName = os.environ.get('GCP_PROJECT', 'ce-prod-274307')
    path = file.split("/")
    accountIdOrig = path[0]
    accountId = re.sub('[^0-9a-z]', '_', accountIdOrig.lower())
    datasetName = "BillingReport_%s" % (accountId)
    # -2 should always be the month folder
    reportYear = path[-2].split("-")[0][:4]
    reportMonth = path[-2].split("-")[0][4:6]
    tableSuffix = "%s_%s" % (reportYear, reportMonth)
    tableName = "%s.%s.%s" % (projectName, datasetName, "azureBilling_%s" % tableSuffix)
    jsonData["accountId"] = accountId.strip()
    jsonData["projectName"] = projectName
    jsonData["tableName"] = tableName
    jsonData["datasetName"] = datasetName
    jsonData["tableSuffix"] = tableSuffix
    jsonData["accountIdOrig"] = accountIdOrig
    print(jsonData)
    client = bigquery.Client(projectName)
    create_dataset(client, jsonData)
    create_scheduler_job(jsonData)


def create_scheduler_job(jsonData):
    # Create a client.
    client = scheduler.CloudSchedulerClient()
    projectName = jsonData["projectName"]
    # Construct the fully qualified location path.
    parent = f"projects/{projectName}/locations/us-central1"
    name = f"{parent}/jobs/ce-azuredata-{jsonData['accountIdOrig']}-{jsonData['tableSuffix']}"
    now = datetime.datetime.utcnow()
    triggerTime = now + datetime.timedelta(minutes = 10)
    schedule = "%d %d %d %d *" % (triggerTime.minute, triggerTime.hour, triggerTime.day, triggerTime.month)
    topic = "projects/%s/topics/ce-azuredata-scheduler" % jsonData["projectName"]

    # Construct the request body.
    job = {
        'name': name,
        'pubsub_target': {
            'topic_name': topic,
            'data': bytes(json.dumps(jsonData), 'utf-8')
        },
        'schedule': schedule
    }

    try:
        # Delete existing jobs for the same name if any.
        client.delete_job(name=name)
        print("Job deleted.")
    except Exception as e:
        print("Error: %s. This can be ignored" % e)

    # Use the client to send the job creation request.
    response = client.create_job(
        request={
            "parent": parent,
            "job": job
        }
    )
    print('Created job: {}'.format(response.name))
    return response

def create_dataset(client, jsonData):
    dataset_id = "{}.{}".format(client.project, jsonData["datasetName"])
    dataset = bigquery.Dataset(dataset_id)
    dataset.location = "US"
    dataset.description = "Data set for [ AccountId: %s ]" % (jsonData.get("accountIdOrig"))

    # Send the dataset to the API for creation, with an explicit timeout.
    # Raises google.api_core.exceptions.Conflict if the Dataset already
    # exists within the project.
    try:
        dataset = client.create_dataset(dataset, timeout=30)  # Make an API request.
        print("Created dataset {}.{}".format(client.project, dataset.dataset_id))
    except Exception as e:
        print("Dataset {} already exists {}".format(dataset_id, e))