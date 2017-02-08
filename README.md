# Elasticsearch Learning to Rank Plugin

This plugin marries the power of the Elasticsearch Query DSL with Ranklib, a popular learning to rank framework. It includes a scripting service for ranklib models & query for learning to rank in Elasticsearch using [Ranklib](https://sourceforge.net/p/lemur/wiki/RankLib/). 

## Installation

Currently, this alpha release supports Elasticsearch 5.1. To install:

`./bin/elasticsearch-plugin install http://es-learn-to-rank.labs.o19s.com/ltr-query-0.0.5.zip`

# Building a Learning to Rank System with Elasticsearch

This section discusses how this plugin fits into build a learning to rank search system on Elasticsearch.

Learning to Rank uses machine learning models to get better search relevance. One prominent library for doing learning to rank is [Ranklib](https://sourceforge.net/p/lemur/wiki/RankLib/)

## Background

Users train Ranklib models at the commandline using a learning to rank training set. Each line lists, for a given query id, how relevant a document is for that query from 0(not at all relevant)-4(exactly relevant). Alongside each line are a set of scalar, query or user dependent features values that go with that judgment. 

```
judgment qid:<queryId> 1:<feature1Val> ... #Comment
```

Here's an example data set taken from Ranklib's documentation:

```
3 qid:1 1:1 2:1 3:0 4:0.2 5:0 # 1A
2 qid:1 1:0 2:0 3:1 4:0.1 5:1 # 1B 
1 qid:1 1:0 2:1 3:0 4:0.4 5:0 # 1C
1 qid:1 1:0 2:0 3:1 4:0.3 5:0 # 1D  
1 qid:2 1:0 2:0 3:1 4:0.2 5:0 # 2A  
2 qid:2 1:1 2:0 3:1 4:0.4 5:0 # 2B 
1 qid:2 1:0 2:0 3:1 4:0.1 5:0 # 2C 
1 qid:2 1:0 2:0 3:1 4:0.2 5:0 # 2D  
2 qid:3 1:0 2:0 3:1 4:0.1 5:1 # 3A 
3 qid:3 1:1 2:1 3:0 4:0.3 5:0 # 3B 
4 qid:3 1:1 2:0 3:0 4:0.4 5:1 # 3C 
1 qid:3 1:0 2:1 3:1 4:0.5 5:0 # 3D
```

The details of how you arive at judgments for a given document/query pair are very application dependent and are left to you. [Quepid](http://quepid.com) can be used to manually gather judgments from experts. Others gather judgments from analytics data such as clicks or conversions. There's a lot of art/science that goes into evaluating user behavior and gathering surveys. (Honestly this step will either make or break your learning to rank solution -- [see here](http://opensourceconnections.com/blog/2014/10/08/when-click-scoring-can-hurt-search-relevance-a-roadmap-to-better-signals-processing-in-search/))


## Learning to Rank with Elasticsearch

How can we integrate what Ranklib does with Elasticsearch?

Bloomberg's [Solr learning to rank](https://issues.apache.org/jira/browse/SOLR-8542) plugin uses Solr's Query DSL as query-dependent features (what [Relevant Search](http://manning.com/books/relevant-search) calls signals). Elasticsearch's Query DSL can serve a similar function. So for example, you may suspect that one feature that correlates with relevance might be if your user's search keywords have a strong title score:

```
{
    "query": {
        "match": {
            "title": userSearchString
        }
    }
}
```

Or another promising feature might be a title phrase match, perhaps ignoring the TF*IDF score and sticking with a `constant_score`.

```
{
    "query": {
        "constant_score": {
            "query": {
                "match_phrase": {
                    "title": userSearchString
                }
            }
        }
    }
}
```

Along with gathering judgments, you can log the relevance scores of these query-dependent features. Perhaps by issuing bulk queries using the `_msearch` endpoint, filtering down to the documents you have judgements for. Taking these  relevance scores, you perhaps arrive at:


```
3 qid:1 1:24.42 2:52.0 # 1A
2 qid:1 1:12.12 2:12.5 # 1B 
...
```

where each feature is a relevance score for a Query DSL query.

Currently, you're in charge of gathering these feature values, logging them (maybe in ES itself), and training Ranklib models externally. Future iterations of this may automatically log query scores to an Elasticsearch index -- but you would still need to provide the judgements.


## Training the Model

Training the model with a file like above, is as simple as using [Ranklib at the command line](https://sourceforge.net/p/lemur/wiki/RankLib%20How%20to%20use/). 

```
> java -jar bin/RankLib.jar -train MQ2008/Fold1/train.txtt -ranker 6 -metric2t NDCG@10 -metric2T ERR@10 -save mymodel.txt
```

mymodel.txt has your ranklib model that Ranklib can use to score documents.

## Store the model in Elasticsearch

Here's where this plugin comes in. This plugin lets you

1. Use/Store ranklib models as 'ranklib' scripts
2. Given a model and a list of features, evaluate the model as an Elasticsearch query


### Store your model

Models can get quite large, so you may wish to store them as files. Nevertheless, we can score them over the API:

```
POST _scripts/ranklib/dummy
{
  "script": "## LambdaMART\n## No. of trees = 1\n## No. of leaves = 10\n## No. of threshold candidates = 256\n## Learning rate = 0.1\n## Stop early = 100\n\n<ensemble>\n <tree id=\"1\" weight=\"0.1\">\n  <split>\n   <feature> 1 </feature>\n   <threshold> 0.45867884 </threshold>\n   <split pos=\"left\">\n    <feature> 1 </feature>\n    <threshold> 0.0 </threshold>\n    <split pos=\"left\">\n     <output> -2.0 </output>\n    </split>\n    <split pos=\"right\">\n     <output> -1.3413081169128418 </output>\n    </split>\n   </split>\n   <split pos=\"right\">\n    <feature> 1 </feature>\n    <threshold> 0.6115718 </threshold>\n    <split pos=\"left\">\n     <output> 0.3089442849159241 </output>\n    </split>\n    <split pos=\"right\">\n     <output> 2.0 </output>\n    </split>\n   </split>\n  </split>\n </tree>\n</ensemble>"
}
```

You can fetch this model again:

```
GET _scripts/ranklib/dummy
```

Finally, you use the `ltr` query to score using this model

```
GET /foo/_search
{
    "query": {
        "ltr": {
    		"model": {
    			"stored": "dummy"
    		},
    		"features": [{
    			"match": {
    				"title": userSearchString
    			}
    		},{
                "constant_score": {
                    "query": {
                        "match_phrase": {
                            "title": "userSearchString"
                        }
                    }
                }
            }]
    	}
    }
}
```

It's expected that the 0th feature in this array corresponds to first feature (feature 1) in the ranklib model. Ranklib does not have a 0th feature, so beware of off-by-one errors.

Ideally you should use this query in a rescore context, because ltr models can be quite expensive to evaluate.

```
{
    "query": {...}
    "rescore": {
        "query": {
            "ltr": {
                "model": {
                    "stored": "dummy"
                },
                "features": [{
                    "match": {
                        "title": userSearchString
                    }
                },{
                    "constant_score": {
                        "query": {
                            "match_phrase": {
                                "title": "userSearchString"
                            }
                        }
                    }
                }]
            }
        }
    }
}
```

Viola! Periodically you'll want to retrain your model. Features may change or judgements may get out of date. Go back to the earlier steps and start again!

# Development

### 1. Install [RanklibPlus.jar](https://github.com/o19s/ranklibplus) in your local maven repo manually:
Download the RankLibPlus jar from the link above and use the following command to install it in your local Maven repo (the quotes around the command arguments will help Maven run without a pom file in the current directory.)

```
mvn install:install-file "-DgroupId=com.o19s" "-DartifactId=RankLibPlus" "-Dversion=0.1" "-Dpackaging=jar" "-Dfile=./RankLibPlus-0.1.0.jar"
```

### 2. Build with Gradle 2.13

```
gradle clean check
```

This runs the tasks in the `esplugin` gradle plugin that builds, tests, generates a Elasticsearch plugin zip file.

### 3. Install with `./bin/elasticsearch-plugin`

```
./bin/elasticsearch-plugin install file:///path/to/project/build/distributions/ltr-query-0.0.1-SNAPSHOT.zip
```

## TODO
- LTR Query Needs Explain that list score of each feature during evaluation.
- Scripts to simplify offline feature logging 
- Create a full example
- Test test test!

## Acknowledgements
- Bloomberg's [Learning to Rank work for Solr](https://issues.apache.org/jira/browse/SOLR-8542)
- Developed by the [Search Relevance](http://opensourceconnections.com/services/relevancy) team at [OpenSource Connections](http://opensourceconnections.com)
- Also check out our other thingies: book [Relevant Search](http://manning.com/books/relevant-search), projects [Elyzer](http://github.com/o19s/elyzer), [Splainer](http://splainer.io), and [Quepid](http://quepid.com)


