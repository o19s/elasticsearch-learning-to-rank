import json
import requests
from utils import ES_AUTH, ES_HOST, BASEPATH_FEATURES, FEATURE_SET_NAME
from urllib.parse import urljoin


# Each feature is stored in a file with the name of the file as feature_number.json: 1.json, 2.json. This function
# load the file from the filesystem using the basepath from the configuration file.
def get_feature(ftr_id: int):
    return json.loads(open(BASEPATH_FEATURES + '%s.json' % ftr_id).read())


# Find all available features using the pattern 1.json, 2.json, etc. All features have to be in order as we stop the
# moment we cannot find the next feature.
def each_feature():
    try:
        ftr_id = 1
        while True:
            parsed_json = get_feature(ftr_id)
            template = parsed_json['query']
            feature_spec = {
                "name": "%s" % ftr_id,
                "params": ["keywords"],
                "template": template
            }
            yield feature_spec
            ftr_id += 1
    except IOError:
        pass


# Obtain all found features from the filesystem and store them into elasticsearch using the provided name of the
# feature set.
def load_features(feature_set_name: str):
    feature_set = {
        "featureset": {
            "name": feature_set_name,
            "features": [feature for feature in each_feature()]
        }
    }
    path = "_ltr/_featureset/%s" % feature_set_name
    full_path = urljoin(ES_HOST, path)
    print("POST %s" % full_path)
    print(json.dumps(feature_set, indent=2))
    head = {'Content-Type': 'application/json'}
    resp = requests.post(full_path, data=json.dumps(feature_set), headers=head, auth=ES_AUTH)
    print("%s" % resp.status_code)
    print("%s" % resp.text)


# Initialize the default feature store
def init_default_store():
    path = urljoin(ES_HOST, '_ltr')
    print("DELETE %s" % path)
    resp = requests.delete(path, auth=ES_AUTH)
    print("%s" % resp.status_code)
    print("PUT %s" % path)
    resp = requests.put(path, auth=ES_AUTH)
    print("%s" % resp.status_code)


if __name__ == "__main__":
    from time import sleep
    init_default_store()
    sleep(1)
    load_features(FEATURE_SET_NAME)
