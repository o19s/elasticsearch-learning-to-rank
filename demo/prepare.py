from urllib.parse import urljoin
import requests


# Use this to download the library taking the version from the configuration file
from utils import RANKLIB_JAR


def download_ranklib_library():
    download_ltr_resource(RANKLIB_JAR)


# Downloads the provided resource from the o19s website to your local folder. When running this file, the defaults
# for the demo are downloaded. You can also used it yourself
def download_ltr_resource(resource):
    ltr_domain = 'http://es-learn-to-rank.labs.o19s.com/'
    resource_url = urljoin(ltr_domain, resource)
    with open(resource, 'wb') as dest:
        print("GET %s" % resource_url)
        resp = requests.get(resource_url, stream=True)
        for chunk in resp.iter_content(chunk_size=1024):
            if chunk:
                dest.write(chunk)


if __name__ == "__main__":

    download_ltr_resource('tmdb.json')
    download_ranklib_library()
