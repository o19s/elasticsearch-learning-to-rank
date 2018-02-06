import json
import requests
from utils import ES_AUTH, ES_HOST
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


def loadFeatures(featureSetName='movie_features'):
    featureSet = {
        "featureset": {
            "name": featureSetName,
            "features": [feature for feature in eachFeature()]
        }
    }
    path = "_ltr/_featureset/%s" % featureSetName
    fullPath = urljoin(ES_HOST, path)
    print("POST %s" % fullPath)
    print(json.dumps(featureSet, indent=2))
    head = {'Content-Type': 'application/json'}
    resp = requests.post(fullPath, data=json.dumps(featureSet), headers=head, auth=ES_AUTH)
    print("%s" % resp.status_code)
    print("%s" % resp.text)



def initDefaultStore():
    path = urljoin(ES_HOST, '_ltr')
    print("DELETE %s" % path)
    resp = requests.delete(path, auth=ES_AUTH)
    print("%s" % resp.status_code)
    print("PUT %s" % path)
    resp = requests.put(path, auth=ES_AUTH)
    print("%s" % resp.status_code)



if __name__ == "__main__":
    from time import sleep
    initDefaultStore()
    sleep(1)
    loadFeatures()
