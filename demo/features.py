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


def formatFeature(ftrId, keywords):
    from jinja2 import Template
    template = Template(open("%s.json.jinja" % ftrId).read())
    jsonStr = template.render(keywords=keywords)
    return json.loads(jsonStr)


def featureQueries(keywords, docIds):
    from copy import deepcopy
    thisBase = deepcopy(baseQuery)
    thisBase['query']['bool']['filter']['ids']['values'] = docIds
    try:
        ftrId = 1
        while True:
            parsedJson = formatFeature(ftrId, keywords)
            if not 'query' in parsedJson:
                raise ValueError("%s.json.jinja should be an ES query with root of {\"query..." % ftrId)
            thisBase['query']['bool']['should'] = parsedJson['query']
            yield thisBase
            ftrId+=1
    except IOError:
        pass


def buildFeaturesJudgmentsFile(judgmentsWithFeatures, filename):
    with open(filename, 'w') as judgmentFile:
        for qid, judgmentList in judgmentsWithFeatures.items():
            for judgment in judgmentList:
                judgmentFile.write(judgment.toRanklibFormat() + "\n")




if __name__ == "__main__":
    import configparser
    from elasticsearch import Elasticsearch
    from judgments import judgmentsFromFile, judgmentsByQid

    config = configparser.ConfigParser()
    config.read('settings.cfg')
    esUrl = config['DEFAULT']['ESHost']
    es = Elasticsearch(esUrl)
    judgements = judgmentsByQid(judgmentsFromFile(filename='sample_judgements.txt'))
    kwDocFeatures(es, index='tmdb', searchType='movie', judgements=judgements)
    for qid, judgmentList in judgements.items():
        for judgment in judgmentList:
            print(judgment.toRanklibFormat())

