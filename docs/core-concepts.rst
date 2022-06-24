Core Concepts
*******************************

Welcome! You're here if you're interested in adding machine learning ranking capabilities to your Elasticsearch system. This guidebook is intended for Elasticsearch developers and data scientists.

=========================
What is Learning to Rank?
========================= 

*Learning to Rank* (LTR) applies machine learning to search relevance ranking. How does relevance ranking differ from other machine learning problems? Regression is one classic machine learning problem. In *regression*, you're attempting to predict a variable (such as a stock price) as a function of known information (such as number of company employees, the company's revenue, etc). In these cases, you're building a function, say `f`, that can take what's known (`numEmployees`, `revenue`), and have `f` output an approximate stock price. 

Classification is another machine learning problem. With classification, our function `f`, would classify our company into several categories. For example, profitable or not profitable. Or perhaps whether or not the company is evading taxes. 

In Learning to Rank, the function `f` we want to learn does not make a direct prediction. Rather it's used for ranking documents. We want a function `f` that comes as close as possible to our user's sense of the ideal ordering of documents dependent on a query. The value output by `f` itself has no meaning (it's not a stock price or a category). It's more a prediction of a users' sense of the relative usefulnes of a document given a query.

Here, we'll briefly walk through the 10,000 meter view of Learning to Rank. For more information, we recommend blog articles `How is Search Different From Other Machine Learning Problems? <http://opensourceconnections.com/blog/2017/08/03/search-as-machine-learning-prob/>`_ and `What is Learning to Rank? <http://opensourceconnections.com/blog/2017/02/24/what-is-learning-to-rank/>`_.

===========================================
Judgments: expression of the ideal ordering
===========================================

Judgment lists, sometimes referred to as "golden sets" grade individual search results for a keyword search. For example, our `demo <http://github.com/o19s/elasticsearch-learning-to-rank//tree/master/demo/>`_ uses `TheMovieDB <http://themoviedb.org>`_. When users search for "Rambo" we can indicate which movies ought to come back for "Rambo" based on our user's expectations of search. 

For example, we know these movies are very relevant:

- First Blood
- Rambo

We know these sequels are fairly relevant, but not exactly relevant:

- Rambo III
- Rambo First Blood, Part II

Some movies that star Sylvester Stallone are only tangentially relevant:

- Rocky
- Cobra

And of course many movies are not even close:

- Bambi
- First Daughter

Judgment lists apply "grades" to documents for a keyword, this helps establish the ideal ordering for a given keyword. For example, if we grade documents from 0-4, where 4 is exactly relevant. The above would turn into the judgment list::

    grade,keywords,movie
    4,Rambo,First Blood     # Exactly Relevant
    4,Rambo,Rambo
    3,Rambo,Rambo III       # Fairly Relevant
    3,Rambo,Rambo First Blood Part II
    2,Rambo,Rocky           # Tangentially Relevant
    2,Rambo,Cobra
    0,Rambo,Bambi           # Not even close...
    0,Rambo,First Daughter


A search system that approximates this ordering for the search query "Rambo", and all our other test queries, can said to be performing well. Metrics such as `NDCG <https://en.wikipedia.org/wiki/Discounted_cumulative_gain>`_ and `ERR <http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.157.4509&rep=rep1&type=pdf>`_ evaluate a query's actual ordering vs the ideal judgment list.

Our ranking function `f` needs to rank search results as close as possible to our judgment lists. We want to maximize quality metrics such as ERR or NDCG over the broadest number of queries in our training set. When we do this, with accurate judgments, we work to return results listings that will be maximally useful to users.

=======================================
Features: the raw material of relevance
=======================================

Above in the example of a stock market predictor, our ranking function `f` used variables such as the number of employees, revenue, etc to arrive at a predicted stock price. These are *features* of the company. Here our ranking function must do the same: using features that describe the document, the query, or some relationship between the document and the query (such as query keyword's TF\*IDF score in a field). 

Features for movies, for example, might include:

- Whether/how much the search keywords match the title field (let's call this `titleScore`)
- Whether/how much the search keywords match the description field (`descScore`)
- The popularity of the movie (`popularity`)
- The rating of the movie (`rating`)
- How many keywords are used during search? (`numKeywords`)

Our ranking function then becomes :code:`f(titleScore, descScore, popularity, rating, numKeywords)`. We hope whatever method we use to create a ranking function can utilize these features to maximize the likelihood of search results being useful for users. For example, it seems intuitive in the "Rambo" use case that `titleScore` matters quite a bit. But one top movie "First Blood" probably only mentions the keyword Rambo in the description. So in this case `descScore` comes into play. Also `popularity`/`rating` might help determine which movies are "sequels" and which are the originals. We might learn this feature doesn't work well in this regard, and introduce a new feature `isSequel` that our ranking function could use to make better ranking decisions. 

Selecting and experimenting with features is a core piece of learning to rank. Good judgments with poor features that don't help predict patterns in the predicted grades and won't create a good search experience. Just like any other machine learning problem: garbage in-garbage out!

For more on the art of creating features for search, check out the book `Relevant Search <http://manning.com/books/relevant-search>`_ by Doug Turnbull and John Berryman.

=============================================
Logging features: completing the training set
=============================================

With a set of features we want to use, we need to annotate the judgment list above with values of each feature. This data will be used once training commences.

In other words, we need to transfer::

    grade,keywords,movie
    4,Rambo,First Blood
    4,Rambo,Rambo
    3,Rambo,Rambo III
    ...

into::

    grade,keywords,movie,titleScore,descScore,popularity,...
    4,Rambo,First Blood,0.0,21.5,100,...
    4,Rambo,Rambo,42.5,21.5,95,...
    3,Rambo,Rambo III,53.1,40.1,50,...

(here titleScore is the relevance score of "Rambo" for title field in document "First  Blood", and so on)

Many learning to rank models are familiar with a file format introduced by SVM Rank, an early learning to rank method. Queries are given ids, and the actual document identifier can be removed for the training process. Features in this file format are labeled with ordinals starting at 1. For the above example, we'd have the file format::

    4   qid:1   1:0.0   2:21.5  3:100,...
    4   qid:1   1:42.5  2:21.5  3:95,...
    3   qid:1   1:53.1  2:40.1  3:50,...
    ...

In actual systems, you might log these values after the fact, gathering them to annotate a judgment list with feature values. In others the judgment list might come from user analytics, so it may be logged as the user interacts with the search application. More on this when we cover it in :doc:`logging-features`.

===============================
Training a ranking function
==============================

With judgments and features in place, the next decision is to arrive at the ranking function. There's a number of models available for ranking, with their own intricate pros and cons. Each one attempts to use the features to minimize the error in the ranking function. Each has its own notion of what "error" means in a ranking system. (for more read `this blog article <http://opensourceconnections.com/blog/2017/08/03/search-as-machine-learning-prob/>`_)

Generally speaking there's a couple of families of models:

- Tree-based models (LambdaMART, MART, Random Forests): These models tend to be most accurate in general. They're large and complex models that can be fairly expensive to train. `RankLib <https://sourceforge.net/p/lemur/wiki/RankLib/>`_ and `xgboost <https://github.com/dmlc/xgboost>`_ both focus on tree-based models.
- SVM based models (SVMRank): Less accurate, but cheap to train. See `SVMRank <https://www.cs.cornell.edu/people/tj/svm_light/svm_rank.html>`_.
- Linear models: Performing a basic linear regression over the judgment list. Tends to not be useful outside of toy examples. See `this blog article <http://opensourceconnections.com/blog/2017/04/01/learning-to-rank-linear-models/>`_

As with any technology, model selection can be as much about what a team has experience with, not just with what performs best.

===================================
Testing: is our model any good?
===================================

Our judgment lists can't cover every user query our model will encounter out in the wild. So it's important to throw our model curveballs, to see how well it can "think for itself." Or as machine learning folks say: can the model generalize beyond the training data? A model that cannot generalize beyond training data is *overfit* to the training data, and not as useful.

To avoid overfitting, you hide some of your judgment lists from the training process. You then use these to test your model. This side data set is known as the "test set." When evaluating models you'll hear about statistics such as "test NDCG" vs "training NDCG." The former reflects how your model will perform against scenarios it hasn't seen before. You hope as you train, your test search quality metrics continue to reflect high quality search. Further: after you deploy a model, you'll want to try out newer/more recent judgment lists to see if your model might be overfit to seasonal/temporal situations.

===================
Real World Concerns
===================

Now that you're oriented, the rest of this guide builds on this context to point out how to use the learning to rank plugin. But before we move on, we want to point out some crucial decisions everyone encounters in building learning to rank systems. We invite you to watch a talk with `Doug Turnbull and Jason Kowalewski <https://www.youtube.com/watch?v=JqqtWfZQUTU&list=PLq-odUc2x7i-9Nijx-WfoRMoAfHC9XzTt&index=5>`_ where the painful lessons of real learning to rank systems are brought out.

- How do you get accurate judgment lists that reflect your users real sense of search quality? 
- What metrics best measure whether search results are useful to users?
- What infrastructure do you need to collect and log user behavior and features?
- How will you detect when/whether your model needs to be retrained?
- How will you A/B test your model vs your current solution? What KPIs will determine success in your search system.

Of course, please don't hesitate to seek out the services of `OpenSource Connection <http://opensourceconnections.com/services>`, sponsors of this plugin, as we work with organizations to explore these issues.

Next up, see how exactly this plugin's functionality fits into a learning to rank system: :doc:`fits-in`.
