import json
import requests
from urllib.parse import urljoin

def getFeature(ftrId):
    return json.loads(open('%s.json' % ftrId).read())

def eachFeature(loadFeatureNames):
    for featureName in loadFeatureNames:
        parsedJson = getFeature(featureName)
        template = parsedJson['query']
        featureSpec = {
            "name": "%s" % featureName,
            "params": ["keywords"],
            "template": template
        }
        yield featureSpec

def POST(esHost, path, body):
    fullPath = urljoin(esHost, path)
    print("POST %s" % fullPath)
    print(json.dumps(body, indent=2))
    resp = requests.post(fullPath, json.dumps(body))
    print("%s" % resp.status_code)
    print("%s" % resp.text)



def loadFeatures(esHost, featureSetName='movie_features', loadFeatures=None):
    if loadFeatures and len(loadFeatures) > 0:
        featureSet = {
            "featureset": {
                "name": featureSetName,
                "features": [feature for feature in eachFeature(loadFeatures)]
            }
        }
        path = "_ltr/_featureset/%s" % featureSetName
        POST(esHost, path, featureSet)


def appendFeatures(esHost, featureSetName='movie_features', loadFeatures=None):
    if loadFeatures and len(loadFeatures) > 0:
        body = {
            "features": [feature for feature in eachFeature(loadFeatures)]
        }
        path = "_ltr/_featureset/%s/_addfeatures" % featureSetName
        POST(esHost, path, body)


def initDefaultStore(esHost):
    path = urljoin(esHost, '_ltr')
    print("DELETE %s" % path)
    resp = requests.delete(path)
    print("%s" % resp.status_code)
    print("PUT %s" % path)
    resp = requests.put(path)
    print("%s" % resp.status_code)



if __name__ == "__main__":
    from time import sleep
    esHost='http://localhost:9200'
    initDefaultStore(esHost)
    sleep(1)
    loadFeatures(esHost)
