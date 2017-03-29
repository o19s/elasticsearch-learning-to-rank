import requests
import json
import os
import time
from requests.exceptions import ConnectionError

# you'll need to have an API key for TMDB
# to run these examples,
# run export TMDB_API_KEY=<YourAPIKey>
tmdb_api_key = os.environ["TMDB_API_KEY"]
# Setup tmdb as its own session, caching requests
# (we only want to cache tmdb, not elasticsearch)
# Get your TMDB API key from
#  https://www.themoviedb.org/documentation/api
# then in shell do export TMDB_API_KEY=<Your Key>
tmdb_api = requests.Session()
tmdb_api.params={'api_key': tmdb_api_key}

def movieList(linksFile='ml-20m/links.csv'):
    import csv
    rdr = csv.reader(open(linksFile))
    tmdbIds = {}
    numMissing = 0
    for rowNo, row in enumerate(rdr):
        if rowNo == 0:
            continue
        try:
            tmdbIds[row[0]] = int(row[2])
        except ValueError:
            numMissing += 1
            print("No TMDB ID at %s, imdb is: %s, missing %s/%s" % (row[0], row[1], numMissing, rowNo))
    return tmdbIds

def getCastAndCrew(movieId, movie):
    httpResp = tmdb_api.get("https://api.themoviedb.org/3/movie/%s/credits" % movieId, verify=False)
    credits = json.loads(httpResp.text) #C
    try:
        crew = credits['crew']
        directors = []
        for crewMember in crew: #D
            if crewMember['job'] == 'Director':
                directors.append(crewMember)
        movie['cast'] = credits['cast'] #E
        movie['directors'] = directors
    except KeyError as e:
        print(e)
        print(credits)

def extract(movieIds=[]):
    movieDict = {}
    for idx, (mlensId, tmdbId) in enumerate(movieIds.items()):
        try:
            print("On %s / %s movies" % (idx, len(movieIds)))
            httpResp = tmdb_api.get("https://api.themoviedb.org/3/movie/%s"
                                    % tmdbId, verify=False)
            if int(httpResp.headers['x-ratelimit-remaining']) < 10:
                print("Rate limited, sleeping")
                time.sleep(6)
            movie = json.loads(httpResp.text)
            movie['mlensId'] = mlensId
            getCastAndCrew(tmdbId, movie)
            movieDict[tmdbId] = movie
        except ConnectionError as e:
            print(e)
    return movieDict



if __name__ == "__main__":
    movieIds = movieList()
    movieDict = extract(movieIds)
    f = open('tmdb.json', 'w')
    f.write(json.dumps(movieDict))
