FAQ
**************************

This section contains answers to common issues that may trip up users.


=============================
Negative Scores
=============================

Lucene does not allow queries to have negative scores.  This can be problematic if you have a raw feature that has a negative value.  Unfortunately there is no easy quick fix for this.  If you are working with such features, you need to make them non-negative *BEFORE* you train your model.  This can be accomplished by creating normalized fields with values shifted by the mininum value or you can run the score thru a function that produces a value >= 0.

=============================
I found a bug
=============================

If you've been fighting with the plugin it's entirely possible you've encountered a bug.  Please open an issue on the Github project and we will do our best to get it sorted.  If you need general support, please see the section below as we will typically close issues that are only looking for support.


=============================
I'm still stuck!
=============================

We'd love to hear from you!  Consider joining the `Relevance Slack Community <https://opensourceconnections.com/slack>`_ and join the #es-learn-to-rank channel. 

