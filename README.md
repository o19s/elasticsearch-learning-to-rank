[![CircleCI](https://circleci.com/gh/o19s/elasticsearch-learning-to-rank.svg?style=svg)](https://circleci.com/gh/o19s/elasticsearch-learning-to-rank)

Rank Elasticsearch results using tree based (LambdaMART, Random Forest, MART) and linear models. Models are trained using the scores of Elasicsearch queries as features. You train offline using tooling such as with [xgboost](https://github.com/dmlc/xgboost) or [ranklib](https://sourceforge.net/p/lemur/wiki/RankLib/). You then POST your model to a to Elasticsearch in a specific text format (the custom "ranklib" language, documented [here](https://docs.google.com/document/d/1DL_Z40eGG3r_BVOoVYpBRb3k2qWONRf_w02FfORtiSU/edit#)). You apply a model using this plugin's `ltr` query. See [blog post](http://opensourceconnections.com/blog/2017/02/14/elasticsearch-learning-to-rank/) and the [full demo](demo/) ([training](https://github.com/o19s/elasticsearch-learning-to-rank/blob/master/demo/train.py#L25) and [searching](https://github.com/o19s/elasticsearch-learning-to-rank/blob/master/demo/search.py)).

# Installing

Generally new features follow the latest ES version, but you can get older versions on older ES versions.

### Elasticsearch 5.2.1, 5.2.2, 5.3.0

`./bin/elasticsearch-plugin install http://es-learn-to-rank.labs.o19s.com/ltr-query-0.1.0-es<ES VER>.zip`

### Elasticsearch 5.1.x, 5.2.0

`./bin/elasticsearch-plugin install http://es-learn-to-rank.labs.o19s.com/ltr-query-0.0.5-es<ES VER>.zip`

### Increase Max Script Size

Models are stored using an Elasticsearch script plugin. Tree-based models can be large. So we recommend increasing the `script.max_size_in_bytes` setting. Don't worry, just because tree-based models are verbose, doesn't nescesarilly imply they'll be slow.

`script.max_size_in_bytes: 10000000`

# Running the Demo

The section below gives more context you'll need to use this plugin, but if you want to jump in, just follow the directions in the [demo README](demo/README.md).


# Building a Learning to Rank System with Elasticsearch

This section discusses how this plugin fits in to build a learning to rank search system on Elasticsearch at a very high level. Many quite challenging, domain-specific details are ignored for the sake of illustration.

Learning to Rank uses machine learning models to get better search relevance. One library for doing learning to rank is [Ranklib](https://sourceforge.net/p/lemur/wiki/RankLib/), which we'll use to demonstrate the plugin.

## Background -- Learning to Rank 101

### Judgement List 101

The first thing you'll need is to create what's known in search circles as a *judgment list*. A *judgment list* says for a given query, a given document recieves this relevance *grade*. What do we mean by *grade*? Usually the "grade" is our numerical assesment for how relevant a document is to a keyword query. Traditionally grades come on a 0-4 scale where 0 means not at all relevant and 4 means exactly relevant.

Consider a search for "rambo." If "doc\_1234" is the movie Rambo and "doc\_5678" is "Turner and Hooch", then we might safely make the following two judgements:

```
4,rambo,doc_1234 # "Rambo" an exact match (grade 4) for search "rambo"
0,rambo,doc_5678 # "Turner and Hooch" not at all relevant (grade 0) for search "rambo"
```

We basically just decided on these judgments. Generating judgements from clicks & conversions or expert testing is an art unto itself we won't dive into here.

### Judgment lists -> training set

Libraries like Ranklib and xgboost don't directly use the three-tuples listed above for training. Ranklib when training doesn't really care what the document identifier is. Nor does it care what the actual query keyword is. Ranklib instead expects you to do some legwork to examine the query and document and generate a set of quantitative *features* you hypothesize might predict the relevance grade. A *feature* here is a numerical value that measures something in the query, the document, or a relationship between the query and document. You might arbitrarilly decide, for example, that feature 1 is the number of times the query keywords occur in the movie title. And feature 2 might correspond to how many times the keywords occur in the movies overview field.

A common file format for this sort of training set is the following:

```
grade qid:<queryId> 1:<feature1Val> 2:<feature2Val>... #Comment
```

Let's take an example. When we look at our query "rambo", which we'll call query Id 1, we note the following feature values:

- Feature 1 : Rambo occurs 1 time in the title of movie "Rambo"; 0 times in Turner and Hootch
- Feature 2 : Rambo occurs 6 times in the overview field of movie "Rambo"; 0 times in Turner and Hootch

The judgment list above, then gets transformed into t

```
4 qid:1 1:1 2:6
0 qid:1 1:0 2:0
```

In other words, a relevant movie for qid:1 has higher feature 1 and 2 values than an irrelevant match.

A full training set expresses this idea, with many hundreds of graded documents over hundreds or thousands or more queries:


```
4 qid:1 1:1 2:6
0 qid:1 1:0 2:0
4 qid:2 1:1 2:6
3 qid:2 1:1 2:6
0 qid:2 1:0 2:0
...
```

### Training a model

The goal of *training* is to generate a function (which we also loosely call a *model*) that takes as input features 1...n and outputs the relevance grade. With Ranklib downloaded, you can train a file like the one above as follows:

```
> java -jar bin/RankLib.jar -train train.txt -ranker 6 -save mymodel.txt
```

This line trains against training data `train.txt` to generate a LambdaMART model (ranker 6), outputing a text representation of the model to `mymodel.txt.` Now training, like generating judgments and hypothisizing features is it's own art & science. Facilities exist in libraries like ranklib to leave out some of the training data to be used as test data to evaluate your model for accuracy. Be sure to research all the options available to you to evaluate your model offline.

Ok, once you have a good model, it can be used as a ranking function to generate relevance scores. Yay!

## Learning to Rank with Elasticsearch

How can we integrate this workflow with Elasticsearch?

There relevance scores of Elasticsearch queries make tremendous features. So for example, you may suspect that one feature that correlates with relevance might be if your user's search keywords have a strong title TF\*IDF relevance score:

```
{
    "query": {
        "match": {
            "title": userSearchString
        }
    }
}
```

Or another promising feature might be an overview phrase match, perhaps ignoring the TF\*IDF score and sticking with a `constant_score`.

```
{
    "query": {
        "constant_score": {
            "query": {
                "match_phrase": {
                    "overview": userSearchString
                }
            }
        }
    }
}
```

As you can imagine, much of the art is guessing which features (aka Elasticsearch Queries) will do the best job at predicting relevance.

Now we need to transform our judgment list:

```
4,rambo,doc_1234 # "Rambo" an exact match (grade 4) for search "rambo"
0,rambo,doc_5678 # "Turner and Hooch" not at all relevant (grade 0) for search "rambo"
```

Into a training set, where feature 1 is the relevance score for the first Elasticsearch query above; feature 2 the second, and so on. Perhaps this turns into something like:

```
# Query id 1, "rambo"
4 qid:1 1:24.42 2:52.0 # Rambo
0 qid:1 1:12.12 2:12.5 # Turner and  Hootch
...
```

How can one use a proposed set of features to bulk-gather relevance scores? Our [demo](/demo) shows how to do this using `_msearch` while filtering down to the graded documents (see [/demo/features.py](/demo/features.py). There's many problems you need to solve for a real-life production system. You need to log features (relevance scores) for your graded documents. Ideally you'd log using your production search index or the closest approximation. Many details left to the reader here, as this starts getting into questions like (1) how well known are your grades (instant based on clicks, or graded by humans weeks later?) (2) how easy is it to bulk evaluate queries on your production system? How often can you do that?

With a sufficiently fleshed out training set, you then repeat the process above.

## Store the model in Elasticsearch

Ok, we haven't even gotten to the plugin yet! Everything above is vanilla Elasticsearch or Ranklib.

Here's where this plugin comes in. This plugin lets you

1. Use/Store ranklib models as you do any other Elasticsearch script (as a special 'ranklib' scripting language)
2. Evaluate a model given a list of ES queries (aka the "features").

### Store your model

We currently just support the ranklib format, documented [here](https://docs.google.com/document/d/1DL_Z40eGG3r_BVOoVYpBRb3k2qWONRf_w02FfORtiSU/edit#). The big tree models are stored using an XML format. Below we store an ensemble of a single regression tree as the model "dummy".

```
POST _scripts/ranklib/dummy
{
  "script": "## LambdaMART\n## No. of trees = 1\n## No. of leaves = 10\n## No. of threshold candidates = 256\n## Learning rate = 0.1\n## Stop early = 100\n\n<ensemble>\n <tree id=\"1\" weight=\"0.1\">\n  <split>\n   <feature> 1 </feature>\n   <threshold> 0.45867884 </threshold>\n   <split pos=\"left\">\n    <feature> 1 </feature>\n    <threshold> 0.0 </threshold>\n    <split pos=\"left\">\n     <output> -2.0 </output>\n    </split>\n    <split pos=\"right\">\n     <output> -1.3413081169128418 </output>\n    </split>\n   </split>\n   <split pos=\"right\">\n    <feature> 1 </feature>\n    <threshold> 0.6115718 </threshold>\n    <split pos=\"left\">\n     <output> 0.3089442849159241 </output>\n    </split>\n    <split pos=\"right\">\n     <output> 2.0 </output>\n    </split>\n   </split>\n  </split>\n </tree>\n</ensemble>"
}
```

Finally, you use the `ltr` query to score using this model. Below "dummy" is the model we just scored. Each "feature" mirrors the queries used at training time.

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
                            "overview": "userSearchString"
                        }
                    }
                }
            }]
    	}
    }
}
```

It's expected that the 0th feature in this array corresponds to first feature (feature 1) that you used when training the model.

Ideally you should use this query in a rescore context, because ltr models can be quite expensive to evaluate. So a more realistic implementation of ltr would look like:

```
{
    "query": {/*your base query goes here*/},
    "rescore": {
        "query": {
           "rescore_query": {
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
                                "overview": "userSearchString"
                            }
                        }
                    }
                }]
            }
           }
        }
    }
}
```

Viola! Periodically you'll want to retrain your model. Features may change or judgements may get out of date. Go back to the earlier steps and start again!

# Development

Notes if you want to dig into the code.

### 1. Build with Gradle 2.13

This plugin requires the very specific 2.13 version of Gradle.  Fortunately running the build with the included Gradle Wrapper will download this version for you!

```
gradlew clean check
```

This runs the tasks in the `esplugin` gradle plugin that builds, tests, generates a Elasticsearch plugin zip file.

### 2. Install with `./bin/elasticsearch-plugin`

```
./bin/elasticsearch-plugin install file:///path/to/project/build/distributions/ltr-query-0.0.1-SNAPSHOT.zip
```

## Acknowledgements
- Bloomberg's [Learning to Rank work for Solr](https://issues.apache.org/jira/browse/SOLR-8542)
- Developed by the [Search Relevance](http://opensourceconnections.com/services/relevancy) team at [OpenSource Connections](http://opensourceconnections.com)
- Also check out our other thingies: book [Relevant Search](http://manning.com/books/relevant-search), projects [Elyzer](http://github.com/o19s/elyzer), [Splainer](http://splainer.io), and [Quepid](http://quepid.com)
