import json
import requests

results = requests.get("http://localhost:9200/tmdb/movie/_search?size=40&q=_all:(boxing stallone rambo)&_source=false&stored_fields=title,overview")

results = json.loads(results.text)

ratings = []

for result in results['hits']['hits']:
    grade = None
    if 'fields' in result:
        print("")
        print("## %s " % result['fields']['title'][0])
        print("   %s " % result['fields']['overview'][0])
        while grade not in ["0", "1", "2", "3", "4"]:
            grade = input("Rate this shit (0-4) ")
        ratings.append((grade, result['_id'], result['fields']['title'][0]))

for rating in ratings:
    print("%s qid:1 # %s %s" % rating)
