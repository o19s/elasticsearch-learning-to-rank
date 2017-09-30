import json
import requests
from urllib.parse import urljoin

def getFeature(ftrId):
    return json.loads(open('%s.json' % ftrId).read())

def eachFeature():
    try:
        ftrId = 1
        while True:
            parsedJson = getFeature(ftrId)
            template = parsedJson['query']
            featureSpec = {
                "name": "%s" % ftrId,
                "params": ["keywords"],
                "template": template
            }
            yield featureSpec
            ftrId += 1
    except IOError:
        pass


def loadFeatures(esHost, featureSetName='movie_features'):
    featureSet = {
        "featureset": {
            "name": featureSetName,
            "features": [feature for feature in eachFeature()]
        }
    }
    path = "_ltr/_featureset/%s" % featureSetName
    fullPath = urljoin(esHost, path)
    print("POST %s" % fullPath)
    print(json.dumps(featureSet, indent=2))
    resp = requests.post(fullPath, json.dumps(featureSet))
    print("%s" % resp.status_code)
    print("%s" % resp.text)



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
