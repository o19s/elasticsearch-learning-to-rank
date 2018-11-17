import configparser
import elasticsearch
from requests.auth import HTTPBasicAuth

__all__ = ["ES_AUTH", "ES_HOST", "elastic_connection", "RANKLIB_JAR", "BASEPATH_FEATURES", "FEATURE_SET_NAME",
           "JUDGMENTS_FILE", "JUDGMENTS_FILE_FEATURES", "INDEX_NAME"]

config = configparser.ConfigParser()
config.read('settings.cfg')

config_set = 'DEFAULT'

ES_HOST = config[config_set]['ESHost']
if 'ESUser' in config[config_set]:
    auth = (config[config_set]['ESUser'], config[config_set]['ESPassword'])
    ES_AUTH = HTTPBasicAuth(*auth)
else:
    auth = None
    ES_AUTH = None

RANKLIB_JAR = config[config_set]['RanklibJar']
BASEPATH_FEATURES = config[config_set]['BasepathFeatures']
FEATURE_SET_NAME = config[config_set]['FeatureSetName']
JUDGMENTS_FILE = config[config_set]['JudgmentsFile']
JUDGMENTS_FILE_FEATURES = config[config_set]['JudgmentsFileWithFeature']
INDEX_NAME = config[config_set]['IndexName']


def elastic_connection(url=None, timeout=1000, http_auth=auth):
    if url is None:
        url = ES_HOST
    return elasticsearch.Elasticsearch(url, timeout=timeout, http_auth=http_auth)
