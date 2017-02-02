"""
Gather relevance scores for given
"""
import json

baseQuery = {
   "query": {
      "bool": {
         "filter": {
             "ids": {
                "values": [
                   # list of ids goes here
                ]
             }
         },
         "should": {
		# query goes here
         }
      }
   }
}

def kwDocFeatures(es, index, searchType, judgements):
    for qid, judgements in judgements.items():
        docIds = [judgement.docId for judgement in judgements]
        keywords = judgements[0].keywords
        bulkSearchReq = []
        for query in featureQueries(keywords, docIds):
            bulkSearchReq.append(json.dumps({'index': index, 'type': searchType}))
            bulkSearchReq.append(json.dumps(query))

        res = es.msearch(body="\n".join(bulkSearchReq))
        ftrId = 1
        for featureResp in res['responses']:
            featuresByDoc = {}
            for docScored in featureResp['hits']['hits']:
                print("%s => %s" % (docScored['_id'], docScored['_score']))
                featuresByDoc[docScored['_id']] = docScored['_score']

            for judgement in judgements:
                try:
                    judgement.features.append(featuresByDoc[judgement.docId])
                except KeyError:
                    judgement.features.append(0.0)

            ftrId += 1




def featureQueries(keywords, docIds):
    from jinja2 import Template
    from copy import deepcopy
    thisBase = deepcopy(baseQuery)
    thisBase['query']['bool']['filter']['ids']['values'] = docIds
    try:
        ftrId = 1
        while True:
            template = Template(open("%s.json.jinja" % ftrId).read())
            jsonStr = template.render(keywords=keywords)
            parsedJson = json.loads(jsonStr)
            if not 'query' in parsedJson:
                raise ValueError("%s.json.jinja should be an ES query with root of {\"query..." % ftrId)
            thisBase['query']['bool']['should'] = parsedJson['query']
            yield thisBase
            ftrId+=1
    except IOError:
        pass





if __name__ == "__main__":
    from elasticsearch import Elasticsearch
    from judgments import judgmentsFromFile, judgmentsByQid
    esUrl="http://localhost:9200"
    es = Elasticsearch()
    judgements = judgmentsByQid(judgmentsFromFile(filename='sample_judgements.txt'))
    kwDocFeatures(es, index='tmdb', searchType='movie', judgements=judgements)
    for qid, judgmentList in judgements.items():
        for judgment in judgmentList:
            print("%s,%s,%s" % (judgment.keywords, judgment.docId, judgment.features))

