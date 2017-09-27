import os
from collectFeatures import logFeatures, buildFeaturesJudgmentsFile
from loadFeatures import initDefaultStore, loadFeatures


def trainModel(judgmentsWithFeaturesFile, modelOutput, whichModel=6):
    # java -jar RankLib-2.6.jar -ranker 6 -train sample_judgments_wfeatures.txt -save model.txt
    cmd = "java -jar RankLib-2.8.jar -ranker %s -train %s -save %s -frate 1.0" % (whichModel, judgmentsWithFeaturesFile, modelOutput)
    print("*********************************************************************")
    print("*********************************************************************")
    print("Running %s" % cmd)
    os.system(cmd)
    pass


def saveModel(esHost, scriptName, featureSet, modelFname):
    """ Save the ranklib model in Elasticsearch """
    import requests
    import json
    from urllib.parse import urljoin
    modelPayload = {
        "model": {
            "name": scriptName,
            "model": {
                "type": "model/ranklib",
                "definition": {
                }
            }
        }
    }

    with open(modelFname) as modelFile:
        modelContent = modelFile.read()
        path = "_ltr/_featureset/%s/_createmodel" % featureSet
        fullPath = urljoin(esHost, path)
        modelPayload['model']['model']['definition'] = modelContent
        print("POST %s" % fullPath)
        resp = requests.post(fullPath, json.dumps(modelPayload))
        print(resp.status_code)
        if (resp.status_code >= 300):
            print(resp.text)





if __name__ == "__main__":
    import configparser
    from elasticsearch import Elasticsearch
    from judgments import judgmentsFromFile, judgmentsByQid

    config = configparser.ConfigParser()
    config.read('settings.cfg')
    esUrl = config['DEFAULT']['ESHost']

    es = Elasticsearch(esUrl, timeout=1000)
    # Load features into Elasticsearch
    initDefaultStore(esUrl)
    loadFeatures(esUrl)
    # Parse a judgments
    movieJudgments = judgmentsByQid(judgmentsFromFile(filename='sample_judgments.txt'))
    # Use proposed Elasticsearch queries (1.json.jinja ... N.json.jinja) to generate a training set
    # output as "sample_judgments_wfeatures.txt"
    logFeatures(es, judgmentsByQid=movieJudgments)
    buildFeaturesJudgmentsFile(movieJudgments, filename='sample_judgments_wfeatures.txt')
    # Train each ranklib model type
    for modelType in [0,1,2,3,4,5,6,7,8,9]:
        # 0, MART
        # 1, RankNet
        # 2, RankBoost
        # 3, AdaRank
        # 4, coord Ascent
        # 6, LambdaMART
        # 7, ListNET
        # 8, Random Forests
        # 9, Linear Regression
        print("*** Training %s " % modelType)
        trainModel(judgmentsWithFeaturesFile='sample_judgments_wfeatures.txt', modelOutput='model.txt', whichModel=modelType)
        saveModel(esHost=esUrl, scriptName="test_%s" % modelType, featureSet='movie_features', modelFname='model.txt')
