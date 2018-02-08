import configparser
import elasticsearch
from requests.auth import HTTPBasicAuth

__all__ = [ "ES_AUTH", "ES_HOST", "Elasticsearch" ]

config = configparser.ConfigParser()
config.read('settings.cfg')
ES_HOST = config['DEFAULT']['ESHost']
if 'ESUser' in config['DEFAULT']:
    auth = (config['DEFAULT']['ESUser'], config['DEFAULT']['ESPassword'])
    ES_AUTH = HTTPBasicAuth(*auth)
else:
    auth = None
    ES_AUTH = None


def Elasticsearch(url=None, timeout=1000, http_auth=auth):
    if url is None:
        url = ES_HOST
    return elasticsearch.Elasticsearch(url, timeout=timeout, http_auth=http_auth)

